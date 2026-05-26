package com.example.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class P2PClient(private val context: Context, private val serverPort: Int = 50005) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val payloadAdapter = moshi.adapter(SocketPayload::class.java)

    suspend fun sendTextMessage(
        targetIp: String,
        senderName: String,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            // Set 5 seconds connection timeout
            socket.connect(InetSocketAddress(targetIp, serverPort), 5000)
            val output = socket.getOutputStream()
            val writer = PrintWriter(output, true)
            
            val payload = SocketPayload(
                type = "TEXT",
                text = text,
                senderName = senderName
            )
            val jsonPayload = payloadAdapter.toJson(payload)
            
            // Print JSON + newline so server can readLine()
            writer.println(jsonPayload)
            Log.d("P2PClient", "Sent P2P text message to $targetIp")
            return@withContext true
        } catch (e: Exception) {
            Log.e("P2PClient", "Error sending text to $targetIp: ${e.message}")
            return@withContext false
        } finally {
            try { socket.close() } catch (ex: Exception) { ex.printStackTrace() }
        }
    }

    suspend fun sendFileMessage(
        targetIp: String,
        senderName: String,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        isVoiceMessage: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val socket = Socket()
        var inputStream: InputStream? = null
        try {
            socket.connect(InetSocketAddress(targetIp, serverPort), 5000)
            val output = socket.getOutputStream()
            val writer = PrintWriter(output, true)

            val payload = SocketPayload(
                type = "FILE",
                fileName = fileName,
                fileSize = fileSize,
                isVoiceMessage = isVoiceMessage,
                senderName = senderName
            )
            val jsonPayload = payloadAdapter.toJson(payload)
            writer.println(jsonPayload)

            // Stream file contents
            inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalSent: Long = 0

            while (true) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                totalSent += bytesRead
                val progress = totalSent.toFloat() / fileSize.toFloat()
                onProgress(progress)
            }
            output.flush()
            Log.d("P2PClient", "Successfully streamed file $fileName to $targetIp, bytes: $totalSent")
            return@withContext true
        } catch (e: Exception) {
            Log.e("P2PClient", "Error sending file to $targetIp: ${e.message}")
            return@withContext false
        } finally {
            try { inputStream?.close() } catch (ex: Exception) { ex.printStackTrace() }
            try { socket.close() } catch (ex: Exception) { ex.printStackTrace() }
        }
    }
}
