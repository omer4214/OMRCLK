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
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.IOException

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

    // --- GitHub Auto-Updater States ---
    var githubRepoInput by mutableStateOf(sharedPrefs.getString("github_repo_input", "napim4214/kendi") ?: "napim4214/kendi")
        private set

    var updateStatus by mutableStateOf("IDLE") // IDLE, CHECKING, AVAILABLE, NOT_AVAILABLE, DOWNLOADING, DOWNLOADED, ERROR
        private set

    var updateDownloadProgress by mutableStateOf(0f)
        private set

    var updateVersionName by mutableStateOf("")
        private set

    var updateErrorMessage by mutableStateOf("")
        private set

    var updateDownloadUrl by mutableStateOf("")
        private set

    var updateApkFileName by mutableStateOf("")
        private set

    fun updateGithubRepoInput(repo: String) {
        githubRepoInput = repo
        sharedPrefs.edit().putString("github_repo_input", repo).apply()
    }

    fun checkForGithubUpdate(context: Context) {
        val repo = githubRepoInput.trim()
        if (repo.isEmpty()) {
            updateStatus = "ERROR"
            updateErrorMessage = "Lütfen geçerli bir GitHub depo adresi girin (örn: kullanıcı/depo)"
            return
        }

        // Support direct APK URLs if entered
        if (repo.startsWith("http://", ignoreCase = true) || repo.startsWith("https://", ignoreCase = true)) {
            updateStatus = "AVAILABLE"
            updateVersionName = "Doğrudan Bağlantı"
            updateDownloadUrl = repo
            
            val lastSlash = repo.lastIndexOf('/')
            val baseName = if (lastSlash != -1) repo.substring(lastSlash + 1) else "direct_download"
            // Ensure the downloaded file is saved with .apk extension
            updateApkFileName = if (baseName.endsWith(".apk", ignoreCase = true)) {
                baseName
            } else {
                "dynamic_update.apk"
            }
            return
        }

        val parts = repo.split("/")
        if (parts.size != 2) {
            updateStatus = "ERROR"
            updateErrorMessage = "Format 'kullanici/depo' şeklinde olmalıdır."
            return
        }

        updateStatus = "CHECKING"
        updateErrorMessage = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Kendi-App-Updater")
                    .build()
                
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP hata kodu: ${response.code}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Boş yanıt gövdesi")
                    val json = JSONObject(bodyString)
                    val tag = json.optString("tag_name", "Bilinmeyen")
                    val assets = json.optJSONArray("assets")
                    
                    var foundApk = false
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                updateDownloadUrl = asset.optString("browser_download_url", "")
                                updateApkFileName = name
                                updateVersionName = tag
                                foundApk = true
                                break
                            }
                        }
                    }

                    viewModelScope.launch(Dispatchers.Main) {
                        if (foundApk) {
                            updateStatus = "AVAILABLE"
                        } else {
                            updateStatus = "NOT_AVAILABLE"
                            updateErrorMessage = "$tag sürümünde hiç APK dosyası bulunamadı."
                        }
                    }
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    updateStatus = "ERROR"
                    updateErrorMessage = "Hata: ${e.message}"
                }
            }
        }
    }

    fun downloadAndInstallApk(context: Context) {
        val url = updateDownloadUrl
        val fileName = updateApkFileName
        if (url.isEmpty()) return

        updateStatus = "DOWNLOADING"
        updateDownloadProgress = 0f
        updateErrorMessage = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("İndirme başarısız oldu: ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Gövde boş")
                    val totalBytes = body.contentLength()
                    
                    val file = File(context.cacheDir, fileName.ifEmpty { "kendi_update.apk" })
                    if (file.exists()) {
                        file.delete()
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead: Long = 0
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (totalBytes > 0) {
                                    val progress = totalRead.toFloat() / totalBytes.toFloat()
                                    viewModelScope.launch(Dispatchers.Main) {
                                        updateDownloadProgress = progress
                                    }
                                }
                            }
                        }
                    }

                    viewModelScope.launch(Dispatchers.Main) {
                        updateStatus = "DOWNLOADED"
                        updateDownloadProgress = 1.0f
                        installApkIntent(context, file)
                    }
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    updateStatus = "ERROR"
                    updateErrorMessage = "İndirme hatası: ${e.message}"
                }
            }
        }
    }

    fun installApkIntent(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "Yükleme dosyası bulunamadı.", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Lütfen harici yükleme iznini veriniz.", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Yükleyici başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("ChatViewModel", "Install Activity Error: ${e.message}", e)
        }
    }

    fun resetUpdateStatus() {
        updateStatus = "IDLE"
        updateDownloadProgress = 0f
        updateErrorMessage = ""
    }
}
