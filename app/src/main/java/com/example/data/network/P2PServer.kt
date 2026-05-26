package com.example.data.network

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.local.ChatDao
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class P2PServer(
    private val context: Context,
    private val chatDao: ChatDao,
    private val serverPort: Int = 50005
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val payloadAdapter = moshi.adapter(SocketPayload::class.java)

    fun startServer() {
        if (isRunning) return
        isRunning = true
        serverScope.launch {
            try {
                serverSocket = ServerSocket(serverPort)
                Log.d("P2PServer", "P2P Server started on port $serverPort")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null) {
                        launch { handleClient(clientSocket) }
                    }
                }
            } catch (e: Exception) {
                Log.e("P2PServer", "Server exception: ${e.message}")
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverScope.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        val remoteAddress = socket.inetAddress?.hostAddress ?: ""
        Log.d("P2PServer", "Incoming connection from $remoteAddress")
        
        try {
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))
            
            // Read metadata json header line
            val metadataJson = reader.readLine() ?: return
            val payload = payloadAdapter.fromJson(metadataJson) ?: return
            
            Log.d("P2PServer", "Received payload header: $payload")

            // Ensure we insert or update Peer connection
            val peerId = remoteAddress
            chatDao.insertPeer(
                PeerEntity(
                    peerId = peerId,
                    name = payload.senderName.ifEmpty { "Wifi Arkadaşı ($remoteAddress)" },
                    lastSeen = System.currentTimeMillis(),
                    lastIp = remoteAddress,
                    isInternetFallback = false
                )
            )

            if (payload.type == "TEXT") {
                // Instantly save text message to Room
                chatDao.insertMessage(
                    MessageEntity(
                        peerId = peerId,
                        sender = "peer",
                        text = payload.text,
                        timestamp = System.currentTimeMillis(),
                        status = "RECEIVED"
                    )
                )
            } else if (payload.type == "FILE") {
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val targetFile = File(targetDir, "Kendi_P2P_${System.currentTimeMillis()}_${payload.fileName}")
                
                // Read from client and write directly to file stream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0
                val outputStream = FileOutputStream(targetFile)
                
                // Since readLine consumes the stream partially, we read remaining stream directly.
                // Note: Standard TCP socket InputStream returns EOF when closed on sender's end
                try {
                    while (totalRead < payload.fileSize) {
                        val remaining = payload.fileSize - totalRead
                        val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                        bytesRead = input.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                } finally {
                    outputStream.close()
                }

                Log.d("P2PServer", "Saved file: ${targetFile.absolutePath}, bytes: $totalRead")

                // Insert into messaging db
                chatDao.insertMessage(
                    MessageEntity(
                        peerId = peerId,
                        sender = "peer",
                        text = if (payload.isVoiceMessage) "🎙️ Sesli Mesaj" else "📁 Dosya Alındı: ${payload.fileName}",
                        filePath = targetFile.absolutePath,
                        fileName = payload.fileName,
                        fileSize = totalRead,
                        isVoiceMessage = payload.isVoiceMessage,
                        timestamp = System.currentTimeMillis(),
                        status = "RECEIVED"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("P2PServer", "Error handling client: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
