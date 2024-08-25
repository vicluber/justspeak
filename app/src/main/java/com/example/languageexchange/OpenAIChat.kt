package com.example.languageexchange

import okhttp3.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenAIChat(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val chatUrl = "https://api.openai.com/v1/chat/completions"

    fun createChatCompletion(messages: List<Message>, model: String = "gpt-3.5-turbo", callback: (String?) -> Unit) {
        val requestBody = ChatRequest(model, messages)
        val jsonRequestBody = gson.toJson(requestBody)
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonRequestBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(chatUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                        val result = chatResponse.choices.firstOrNull()?.message?.content
                        callback(result)
                    } else {
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
        })
    }

    data class ChatRequest(
        val model: String,
        val messages: List<Message>
    )

    data class Message(
        val role: String,
        val content: String
    )

    data class ChatResponse(
        val choices: List<Choice>
    )

    data class Choice(
        val message: Message
    )
}
