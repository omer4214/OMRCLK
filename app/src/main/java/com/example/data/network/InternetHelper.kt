package com.example.data.network

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InternetHelper(private val context: Context) {
    private val client = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val roomAdapter = moshi.adapter(InternetRoomState::class.java)

    private val bucketUrl = "https://kvdb.io/kendi_app_sec_4938fd83a2"

    // Generate a unique client ID per app launch/install
    val clientId: String by lazy {
        val prefs = context.getSharedPreferences("kendi_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("client_uuid", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("client_uuid", id).apply()
        }
        id
    }

    suspend fun getRoomState(roomCode: String): InternetRoomState? = withContext(Dispatchers.IO) {
        val url = "$bucketUrl/room_$roomCode"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        return@withContext roomAdapter.fromJson(bodyString)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InternetHelper", "Error getting room state: ${e.message}")
        }
        return@withContext null
    }

    suspend fun updateRoomState(roomCode: String, state: InternetRoomState): Boolean = withContext(Dispatchers.IO) {
        val url = "$bucketUrl/room_$roomCode"
        val json = roomAdapter.toJson(state)
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).put(body).build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("InternetHelper", "Error updating room state: ${e.message}")
            return@withContext false
        }
    }

    // Register active device presence
    suspend fun updatePresence(roomCode: String, nickname: String, localIp: String) {
        val currentState = getRoomState(roomCode) ?: InternetRoomState()
        val updatedPeers = currentState.peers.toMutableMap()
        updatedPeers[clientId] = HandshakePayload(deviceName = nickname, localIp = localIp)
        val updatedState = currentState.copy(peers = updatedPeers)
        updateRoomState(roomCode, updatedState)
    }

    // Send a message over internet mailbox fallback
    suspend fun sendInternetMessage(
        roomCode: String,
        nickname: String,
        text: String,
        fileName: String? = null,
        fileSize: Long = 0,
        downloadUrl: String? = null,
        isVoiceMessage: Boolean = false
    ): Boolean {
        val currentState = getRoomState(roomCode) ?: InternetRoomState()
        val newMessage = InternetMessage(
            id = UUID.randomUUID().toString(),
            senderId = clientId,
            senderName = nickname,
            text = text,
            fileName = fileName,
            fileSize = fileSize,
            downloadUrl = downloadUrl,
            isVoiceMessage = isVoiceMessage,
            timestamp = System.currentTimeMillis()
        )
        val updatedMessages = currentState.messages.toMutableList()
        // Cap messages at 50 to avoid bloated payload storage in raw KV
        if (updatedMessages.size > 50) {
            updatedMessages.removeAt(0)
        }
        updatedMessages.add(newMessage)
        val updatedState = currentState.copy(messages = updatedMessages)
        return updateRoomState(roomCode, updatedState)
    }

    suspend fun uploadFile(uri: Uri, fileName: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        val fileInputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}")
        
        try {
            // Write to a temporary file for OkHttp uploading
            FileOutputStream(tempFile).use { out ->
                fileInputStream.use { input ->
                    input.copyTo(out)
                }
            }

            // Create media type
            val mediaType = "application/octet-stream".toMediaTypeOrNull()
            
            // Build progressive request body (optional simplicity: standard request body)
            val fileBody = RequestBody.create(mediaType, tempFile)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build()

            val request = Request.Builder()
                .url("https://file.io")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        // Manual parse or clean substring extraction to avoid library compile complexity
                        val linkPrefix = "\"link\":\""
                        val progressIndex = responseBody.indexOf(linkPrefix)
                        if (progressIndex != -1) {
                            val startIndex = progressIndex + linkPrefix.length
                            val endIndex = responseBody.indexOf("\"", startIndex)
                            if (endIndex != -1) {
                                return@withContext responseBody.substring(startIndex, endIndex)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InternetHelper", "Error uploading file: ${e.message}")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        return@withContext null
    }

    suspend fun downloadFile(url: String, fileName: String): File? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                
                // Save to downloads directory or app storage (Downloads is safer & visible)
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val targetFile = File(targetDir, "Kendi_${System.currentTimeMillis()}_$fileName")
                
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext targetFile
            }
        } catch (e: Exception) {
            Log.e("InternetHelper", "Error downloading file: ${e.message}")
            return@withContext null
        }
    }
}
