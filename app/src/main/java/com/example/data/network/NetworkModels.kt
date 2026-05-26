package com.example.data.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HandshakePayload(
    val deviceName: String,
    val localIp: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class SocketPayload(
    val type: String,               // "TEXT" or "FILE" or "PING"
    val text: String = "",          // Message body
    val fileName: String = "",      // File name (if FILE)
    val fileSize: Long = 0,         // File size (if FILE)
    val isVoiceMessage: Boolean = false,
    val senderName: String = ""
)

@JsonClass(generateAdapter = true)
data class InternetMessage(
    val id: String,
    val senderId: String,           // Client UUID
    val senderName: String,
    val text: String,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val downloadUrl: String? = null,
    val isVoiceMessage: Boolean = false,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class InternetRoomState(
    val peers: Map<String, HandshakePayload> = emptyMap(),
    val messages: List<InternetMessage> = emptyList()
)
