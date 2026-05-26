package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerId: String,             // Room Code or local Peer IP
    val sender: String,             // "me" or "peer"
    val text: String,               // Message text (or voice duration/file name if file)
    val filePath: String? = null,    // Local storage path for received/sent files
    val fileName: String? = null,    // User-facing file name
    val fileSize: Long = 0,          // File size in bytes
    val isVoiceMessage: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT",      // "PREPARING", "SENDING", "SENT", "RECEIVED", "FAILED"
    val internetId: String? = null    // Server-assigned unique UUID to prevent duplicates
)
