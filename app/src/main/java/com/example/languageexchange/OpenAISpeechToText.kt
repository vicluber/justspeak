package com.example.languageexchange

import okhttp3.*
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.util.Log
import java.io.File
import java.io.IOException

class OpenAISpeechToText(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val transcriptionUrl = "https://api.openai.com/v1/audio/transcriptions"

    fun transcribeAudio(audioFilePath: String, model: String = "whisper-1", callback: (String?) -> Unit) {
        val audioFile = File(audioFilePath)
        val mediaType = "audio/mp3".toMediaTypeOrNull()

        Log.d("OpenAISpeechToText", "Preparing request to transcribe audio file: ${audioFilePath}")

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

        Log.d("OpenAISpeechToText", "Sending request to OpenAI transcription API")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OpenAISpeechToText", "Request failed: ${e.message}", e)
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("OpenAISpeechToText", "Received response from OpenAI transcription API")
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("OpenAISpeechToText", "Response is successful, body: $responseBody")
                    if (responseBody != null) {
                        val transcriptionResponse = gson.fromJson(responseBody, TranscriptionResponse::class.java)
                        Log.d("OpenAISpeechToText", "Transcription text: ${transcriptionResponse.text}")
                        callback(transcriptionResponse.text)
                    } else {
                        Log.e("OpenAISpeechToText", "Response body is null")
                        callback(null)
                    }
                } else {
                    Log.e("OpenAISpeechToText", "Response failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    data class TranscriptionResponse(val text: String)
}
