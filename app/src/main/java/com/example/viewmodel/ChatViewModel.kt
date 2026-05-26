package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.example.data.network.NetworkUtils
import com.example.data.network.P2PServer
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val chatDao = database.chatDao()
    private val repository = ChatRepository(application, chatDao)

    private val sharedPrefs = application.getSharedPreferences("kendi_prefs", Context.MODE_PRIVATE)

    // User settings
    var myNickname by mutableStateOf(sharedPrefs.getString("nickname", Build.MODEL) ?: Build.MODEL)
        private set

    var currentRoomCode by mutableStateOf(sharedPrefs.getString("room_code", "") ?: "")
        private set

    // Server IP & P2P Server
    val myLocalIp: String get() = repository.getMyLocalIp()
    private val p2pServer = P2PServer(application, chatDao)

    // Conversations & Active Selection
    val allPeers: StateFlow<List<PeerEntity>> = repository.allPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedPeer by mutableStateOf<PeerEntity?>(null)
        private set

    // Message lists
    private val _currentMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val currentMessages: StateFlow<List<MessageEntity>> = _currentMessages.asStateFlow()

    // Recording status
    var isRecording by mutableStateOf(false)
        private set
    private var mediaRecorder: MediaRecorder? = null
    private var activeVoiceFile: File? = null

    // Audio Playback Player
    private var mediaPlayer: MediaPlayer? = null
    var currentlyPlayingMessageId by mutableStateOf<Long?>(null)
        private set

    init {
        // Automatically spin up the P2P Server socket listener on port 50005
        p2pServer.startServer()
        
        // Start sync if there is a saved room code
        if (currentRoomCode.isNotEmpty()) {
            repository.startInternetSync(currentRoomCode, myNickname)
        }
    }

    override fun onCleared() {
        super.onCleared()
        p2pServer.stopServer()
        repository.stopInternetSync()
        releaseAudioResources()
    }

    fun updateNickname(name: String) {
        myNickname = name
        sharedPrefs.edit().putString("nickname", name).apply()
        // Force refresh sync presence
        if (currentRoomCode.isNotEmpty()) {
            repository.startInternetSync(currentRoomCode, name)
        }
    }

    fun selectPeer(peer: PeerEntity?) {
        selectedPeer = peer
        if (peer != null) {
            viewModelScope.launch {
                repository.getMessages(peer.peerId).collect {
                    _currentMessages.value = it
                }
            }
        } else {
            _currentMessages.value = emptyList()
        }
    }

    // Connect via Local IP manually
    fun connectToLocalPeer(ipAddress: String, name: String) {
        viewModelScope.launch {
            if (ipAddress.trim().isNotEmpty()) {
                val cleanIp = ipAddress.trim()
                repository.registerOrUpdatePeer(
                    peerId = cleanIp,
                    name = name.ifEmpty { "Wifi Arkadaşı ($cleanIp)" },
                    ip = cleanIp,
                    isInternet = false
                )
                // Instantly select the peer
                val newPeer = PeerEntity(peerId = cleanIp, name = name.ifEmpty { "Wifi Arkadaşı ($cleanIp)" }, lastIp = cleanIp, isInternetFallback = false)
                selectPeer(newPeer)
            }
        }
    }

    // Connect/Join online room fallback
    fun joinInternetRoom(code: String) {
        val cleanCode = code.uppercase().trim()
        currentRoomCode = cleanCode
        sharedPrefs.edit().putString("room_code", cleanCode).apply()

        if (cleanCode.isNotEmpty()) {
            viewModelScope.launch {
                // Register a fallback peer named after the room code so they have a single canvas
                repository.registerOrUpdatePeer(
                    peerId = cleanCode,
                    name = "☁️ İnternet Odası: $cleanCode",
                    ip = null,
                    isInternet = true
                )
                
                // Select peer room instantly
                val peer = PeerEntity(
                    peerId = cleanCode,
                    name = "☁️ İnternet Odası: $cleanCode",
                    lastIp = null,
                    isInternetFallback = true
                )
                selectPeer(peer)
                
                // Fire up mailbox background polling
                repository.startInternetSync(cleanCode, myNickname)
            }
        } else {
            repository.stopInternetSync()
        }
    }

    fun leaveInternetRoom() {
        repository.stopInternetSync()
        currentRoomCode = ""
        sharedPrefs.edit().putString("room_code", "").apply()
        if (selectedPeer?.isInternetFallback == true) {
            selectPeer(null)
        }
    }

    fun deleteSavedPeer(peerId: String) {
        viewModelScope.launch {
            repository.deletePeer(peerId)
            if (selectedPeer?.peerId == peerId) {
                selectPeer(null)
            }
        }
    }

    // Send Message Dispatcher (Differentiates Local Sockets vs. Mailbox)
    fun sendText(text: String) {
        val peer = selectedPeer ?: return
        if (text.trim().isEmpty()) return
        
        viewModelScope.launch {
            repository.sendMessage(
                peer = peer,
                text = text,
                senderName = myNickname,
                roomCode = currentRoomCode
            )
        }
    }

    // Send File Dispatcher
    fun sendFile(uri: Uri, fileName: String, fileSize: Long, isVoice: Boolean = false, onProgress: (Float) -> Unit = {}) {
        val peer = selectedPeer ?: return
        viewModelScope.launch {
            repository.sendFile(
                peer = peer,
                fileName = fileName,
                fileSize = fileSize,
                uri = uri,
                senderName = myNickname,
                isVoice = isVoice,
                roomCode = currentRoomCode,
                onProgress = onProgress
            )
        }
    }

    // --- Modern Voice Recording Methods ---
    fun startVoiceRecording() {
        if (isRecording) return
        val context = getApplication<Application>()
        try {
            activeVoiceFile = File(context.cacheDir, "kendi_rec_${System.currentTimeMillis()}.3gp")
            
            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(activeVoiceFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("ChatViewModel", "Voice recording started: ${activeVoiceFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to start recording: ${e.message}")
            isRecording = false
        }
    }

    fun stopAndSendVoiceRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = activeVoiceFile
            if (file != null && file.exists() && file.length() > 0) {
                val uri = Uri.fromFile(file)
                sendFile(
                    uri = uri,
                    fileName = file.name,
                    fileSize = file.length(),
                    isVoice = true
                )
                Log.d("ChatViewModel", "Sent vocal recording file: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to stop recording: ${e.message}")
        } finally {
            isRecording = false
            mediaRecorder = null
        }
    }

    // --- Inline MediaPlayer Playback Controller ---
    fun toggleVoicePlay(message: MessageEntity) {
        val path = message.filePath ?: return
        if (currentlyPlayingMessageId == message.id) {
            // Pause/Stop
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingMessageId = null
        } else {
            // Stop previous playing
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingMessageId = null

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    start()
                    setOnCompletionListener {
                        currentlyPlayingMessageId = null
                        release()
                        mediaPlayer = null
                    }
                }
                currentlyPlayingMessageId = message.id
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error playing audio file: ${e.message}")
            }
        }
    }

    private fun releaseAudioResources() {
        mediaRecorder?.release()
        mediaRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
