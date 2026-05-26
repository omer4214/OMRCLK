package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val peerId: String,  // Room Code or Peer IP
    val name: String,               // Display Name
    val lastSeen: Long = System.currentTimeMillis(),
    val lastIp: String? = null,      // e.g. "192.168.1.15" or "internet"
    val isInternetFallback: Boolean = false
)
