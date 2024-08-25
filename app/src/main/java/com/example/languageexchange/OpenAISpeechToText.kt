package com.example.languageexchange

import okhttp3.*
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException

class OpenAISpeechToText(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val transcriptionUrl = "https://api.openai.com/v1/audio/transcriptions"

    fun transcribeAudio(audioFilePath: String, model: String = "whisper-1", callback: (String?) -> Unit) {
        val audioFile = File(audioFilePath)
        val mediaType = "audio/mp3".toMediaTypeOrNull()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", audioFile.name, RequestBody.create(mediaType, audioFile))
            .build()

        val request = Request.Builder()
            .url(transcriptionUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
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
                        val transcriptionResponse = gson.fromJson(responseBody, TranscriptionResponse::class.java)
                        callback(transcriptionResponse.text)
                    } else {
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
        })
    }

    data class TranscriptionResponse(val text: String)
}
