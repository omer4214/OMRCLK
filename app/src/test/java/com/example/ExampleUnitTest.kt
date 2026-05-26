package com.example

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.io.IOException

class ExampleUnitTest {
  @Test
  fun create_kvdb_bucket() {
    val client = OkHttpClient()
    val body = FormBody.Builder()
        .add("secret", "kendi_p2p_chat_v11_super_secret_password")
        .add("email", "napim4214@gmail.com")
        .build()
    val request = Request.Builder()
        .url("https://kvdb.io/")
        .post(body)
        .build()
    
    try {
        client.newCall(request).execute().use { response ->
            println("--- KVDB CREATION START ---")
            println("KVDB Response Code: ${response.code}")
            val bodyStr = response.body?.string()
            println("KVDB Response Body: $bodyStr")
            println("--- KVDB CREATION END ---")
        }
    } catch (e: Exception) {
        println("Error creating bucket: ${e.message}")
    }
  }
}
