package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.data.local.ChatDao
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.example.data.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream

class ChatRepository(
    private val context: Context,
    private val chatDao: ChatDao
) {
    private val p2pClient = P2PClient(context)
    private val internetHelper = InternetHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    // Room Single Source of Truth
    fun getMessages(peerId: String): Flow<List<MessageEntity>> = chatDao.getMessagesForPeer(peerId)
    val allPeers: Flow<List<PeerEntity>> = chatDao.getAllPeers()

    // Retrieve my local IP address
    fun getMyLocalIp(): String = NetworkUtils.getLocalIpAddress()

    // Insert direct message
    suspend fun localInsertMessage(message: MessageEntity): Long {
        return chatDao.insertMessage(message)
    }

    suspend fun updateMessageStatus(message: MessageEntity) {
        chatDao.updateMessage(message)
    }

    suspend fun registerOrUpdatePeer(peerId: String, name: String, ip: String?, isInternet: Boolean) {
        chatDao.insertPeer(
            PeerEntity(
                peerId = peerId,
                name = name,
                lastSeen = System.currentTimeMillis(),
                lastIp = ip,
                isInternetFallback = isInternet
            )
        )
    }

    suspend fun deletePeer(peerId: String) {
        chatDao.deletePeerById(peerId)
    }

    // --- Outbound Message Dispatcher (Decides Wifi or Internet dynamically) ---
    suspend fun sendMessage(
        peer: PeerEntity,
        text: String,
        senderName: String,
        roomCode: String? = null
    ): Boolean {
        if (!peer.isInternetFallback && peer.lastIp != null) {
            // Wi-Fi Local Connection
            val success = p2pClient.sendTextMessage(peer.lastIp, senderName, text)
            if (success) {
                chatDao.insertMessage(
                    MessageEntity(
                        peerId = peer.peerId,
                        sender = "me",
                        text = text,
                        status = "SENT"
                    )
                )
            }
            return success
        } else {
            // Internet Mode Fallback
            if (roomCode.isNullOrBlank()) return false
            val success = internetHelper.sendInternetMessage(
                roomCode = roomCode,
                nickname = senderName,
                text = text
            )
            if (success) {
                chatDao.insertMessage(
                    MessageEntity(
                        peerId = peer.peerId,
                        sender = "me",
                        text = text,
                        status = "SENT"
                    )
                )
            }
            return success
        }
    }

    // --- Outbound File Transfer Dispatcher (Local Wi-Fi or Internet fallbacks) ---
    suspend fun sendFile(
        peer: PeerEntity,
        fileName: String,
        fileSize: Long,
        uri: Uri,
        senderName: String,
        isVoice: Boolean,
        roomCode: String? = null,
        onProgress: (Float) -> Unit
    ): Boolean {
        // Step 1: Write text to local Room in PREPARING status
        val localMsgId = chatDao.insertMessage(
            MessageEntity(
                peerId = peer.peerId,
                sender = "me",
                text = if (isVoice) "🎙️ Sesli Mesaj Gönderiliyor..." else "📁 Dosya Gönderiliyor: $fileName",
                fileName = fileName,
                fileSize = fileSize,
                isVoiceMessage = isVoice,
                status = "SENDING"
            )
        )

        // Save a persistent copy locally in the downloads or cache folder so the sender can always play or open it
        val localFile = try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val destFile = File(targetDir, "Kendi_Sent_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            null
        }
        val localFilePath = localFile?.absolutePath ?: uri.toString()

        val tempMsg = MessageEntity(
            id = localMsgId,
            peerId = peer.peerId,
            sender = "me",
            text = if (isVoice) "🎙️ Sesli Mesaj" else "📁 Dosya: $fileName",
            fileName = fileName,
            fileSize = fileSize,
            isVoiceMessage = isVoice,
            filePath = localFilePath,
            status = "SENT"
        )

        if (!peer.isInternetFallback && peer.lastIp != null) {
            // Wi-Fi fast transfer via Direct socket streaming
            val success = p2pClient.sendFileMessage(
                targetIp = peer.lastIp,
                senderName = senderName,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                isVoiceMessage = isVoice,
                onProgress = onProgress
            )
            if (success) {
                chatDao.updateMessage(tempMsg.copy(status = "SENT"))
            } else {
                chatDao.updateMessage(tempMsg.copy(status = "FAILED"))
            }
            return success
        } else {
            // Online Internet Transfer via tmpfiles.org secure caching upload
            if (roomCode.isNullOrBlank()) {
                chatDao.updateMessage(tempMsg.copy(status = "FAILED"))
                return false
            }
            try {
                // Upload to tmpfiles.org
                val downloadUrl = internetHelper.uploadFile(uri, fileName, onProgress)
                if (downloadUrl != null) {
                    // Send download metadata to Room fallback mailbox
                    val success = internetHelper.sendInternetMessage(
                        roomCode = roomCode,
                        nickname = senderName,
                        text = if (isVoice) "🎙️ Sesli Mesaj" else "📁 Dosya paylaşıldı: $fileName",
                        fileName = fileName,
                        fileSize = fileSize,
                        downloadUrl = downloadUrl,
                        isVoiceMessage = isVoice
                    )
                    if (success) {
                        chatDao.updateMessage(tempMsg.copy(filePath = localFilePath, status = "SENT"))
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Internet file send failed: ${e.message}")
            }
            chatDao.updateMessage(tempMsg.copy(status = "FAILED"))
            return false
        }
    }

    // --- Background Internet Mailbox Synchronization ---
    // Polls kvdb.io and retrieves remote messages, updating the DB reactively
    fun startInternetSync(roomCode: String, myNickname: String) {
        stopInternetSync()
        syncJob = scope.launch {
            val seenServerMessageIds = mutableSetOf<String>()
            
            while (isActive) {
                try {
                    // Step 1: Update Presence
                    val myIp = getMyLocalIp()
                    internetHelper.updatePresence(roomCode, myNickname, myIp)

                    // Step 2: Fetch updates
                    val state = internetHelper.getRoomState(roomCode)
                    if (state != null) {
                        // Discover local IPs and automatically update local Peer status!
                        state.peers.forEach { (peerClientId, payload) ->
                            if (peerClientId != internetHelper.clientId) {
                                // If the peer has an IP, let's keep track of them
                                registerOrUpdatePeer(
                                    peerId = roomCode, // Dynamic peer id represents the room grouping
                                    name = payload.deviceName,
                                    ip = if (payload.localIp != "127.0.0.1") payload.localIp else null,
                                    isInternet = true
                                )
                            }
                        }

                        // Parse messages
                        val peerId = roomCode
                        state.messages.forEach { msg ->
                            if (!seenServerMessageIds.contains(msg.id)) {
                                seenServerMessageIds.add(msg.id)
                                
                                // Only process if not sent by me
                                if (msg.senderId != internetHelper.clientId) {
                                    // See if it is a file we need to download/preview
                                    var localPath: String? = null
                                    if (msg.downloadUrl != null && msg.fileName != null) {
                                        // Auto-download file in background!
                                        val downloadedFile = internetHelper.downloadFile(msg.downloadUrl, msg.fileName)
                                        if (downloadedFile != null) {
                                            localPath = downloadedFile.absolutePath
                                        }
                                    }

                                    // Save received message to native Room database
                                    chatDao.insertMessage(
                                        MessageEntity(
                                            peerId = peerId,
                                            sender = "peer",
                                            text = msg.text,
                                            filePath = localPath,
                                            fileName = msg.fileName,
                                            fileSize = msg.fileSize,
                                            isVoiceMessage = msg.isVoiceMessage,
                                            timestamp = msg.timestamp,
                                            status = "RECEIVED"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Sync heartbeat error: ${e.message}")
                }
                delay(3000) // Poll every 3 seconds for battery limits and fast updates
            }
        }
    }

    fun stopInternetSync() {
        syncJob?.cancel()
        syncJob = null
    }
}
