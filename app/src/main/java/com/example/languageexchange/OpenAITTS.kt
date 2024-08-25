package com.example.languageexchange

import okhttp3.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class OpenAITTS(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val ttsUrl = "https://api.openai.com/v1/audio/speech"

    fun createSpeech(input: String, model: String = "tts-1", voice: String = "alloy", outputFilePath: String, callback: (Boolean) -> Unit) {
        val requestBody = SpeechRequest(model, voice, input)
        val jsonRequestBody = gson.toJson(requestBody)
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonRequestBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(ttsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val inputStream = response.body?.byteStream()
                    if (inputStream != null) {
                        val outputFile = File(outputFilePath)
                        val outputStream = FileOutputStream(outputFile)
                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback(true)
                    } else {
                        callback(false)
                    }
                } else {
                    callback(false)
                }
            }
        })
    }

    data class SpeechRequest(
        val model: String,
        val voice: String,
        val input: String
    )
}
