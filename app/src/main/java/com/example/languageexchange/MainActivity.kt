package com.example.languageexchange

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // Properties declared but not initialized yet
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var transcriptionTextView: TextView
    private lateinit var responseTextView: TextView
    private lateinit var button: Button
    private lateinit var alternativeButton: Button
    private lateinit var translateButton: Button
    private lateinit var outputFilePath: String
    private lateinit var selectedLanguage: String

    // Properties initialized immediately
    private var isRecording = false
    private var isCancelled = false
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private val apiKey = ""
    private val speechToTextClient = OpenAISpeechToText(apiKey)
    private val chatClient = OpenAIChat(apiKey)
    private val ttsClient = OpenAITTS(apiKey)
    private var initialX: Float = 0f
    private var alternateUserInput: String? = null
    private var alternateGptResponse: String? = null
    private var translationUserInput: String? = null
    private var translationGptResponse: String? = null

    // Mueve la declaración de messages aquí, pero no la inicialices aún
    private lateinit var messages: MutableList<OpenAIChat.Message>

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This line must be called before accessing any view elements
        setContentView(R.layout.activity_main)
        checkAndRequestPermissions()
        // Initialize UI components after setContentView
        button = findViewById(R.id.button)
        alternativeButton = findViewById(R.id.alternativeButton)
        translateButton = findViewById(R.id.translateButton)

        selectedLanguage = intent.getStringExtra("selectedValue") ?: "Default Value"

        // Ahora inicializa messages
        messages = mutableListOf(
            OpenAIChat.Message("system", "You are gonna act as a ${selectedLanguage} teacher who chats with me just for me to learn. So it doesn't matter which language I use you will respond accordingly but in ${selectedLanguage}, for example, if I ask you how to say something in ${selectedLanguage} always reply in ${selectedLanguage} don't use the language I use, always ${selectedLanguage}. No matter what I say, never change the language, ALWAYS speak ${selectedLanguage} with me. If I ask you to speak another language just tell me that is for my good that you will just reply in ${selectedLanguage}.\\n I'm also not good in conversation making so if I don't ask anything you will propose easy topics of conversations.")
        )
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> handleButtonActionDown(event)
                MotionEvent.ACTION_MOVE -> handleButtonActionMove(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleButtonActionUp()
            }
            true
        }
        alternativeButton.setOnClickListener {
            handleAlternativeButtonActionClick()
        }

        translateButton.setOnClickListener {
            handleTranslateButtonActionClick()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    private fun handleButtonActionDown(event: MotionEvent) {
        initialX = event.rawX
        isCancelled = false
        startRecording()
    }

    private fun handleButtonActionMove(event: MotionEvent) {
        val deltaX = event.rawX - initialX
        if (deltaX > 100) {
            isCancelled = true
            stopRecording()
            Toast.makeText(this@MainActivity, "Grabación cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleButtonActionUp() {
        if (isCancelled) {
            cancelRecording()
        } else {
            stopRecording()
            transcribeAudio()
        }
    }

    private fun handleAlternativeButtonActionClick() {
        val messages = listOf(
            OpenAIChat.Message("system", "If I say something in ${selectedLanguage} you will make a correction if it needs one and offer an alternative in this structure 'Correction: the correct phrase - Alternative: an alternative phrase for the same meaning' and if I say something in any other language than ${selectedLanguage} you will translate that to ${selectedLanguage} but only if I say something in other language than ${selectedLanguage}"),
            OpenAIChat.Message("user", transcriptionTextView.text.toString())
        )
        if(alternateUserInput == null){
            chatClient.createChatCompletion(messages) { response ->
                runOnUiThread {
                    if (response != null) {
                        alternateUserInput = transcriptionTextView.text.toString()
                        transcriptionTextView.text = response
                        alternateGptResponse = response
                    } else {
                        showToast("Error al obtener respuesta")
                    }
                }
            }
        }else{
            transcriptionTextView.text = if (transcriptionTextView.text == alternateUserInput) alternateGptResponse else alternateUserInput
        }
    }

    private fun handleTranslateButtonActionClick() {
        val messages = listOf(
            OpenAIChat.Message("system", "You are gonna translate to ${selectedLanguage} what I say in this structure 'the translated phrase' so nothing more than the translation"),
            OpenAIChat.Message("user", transcriptionTextView.text.toString())
        )
        if(translationUserInput == null){
            chatClient.createChatCompletion(messages) { response ->
                runOnUiThread {
                    if (response != null) {
                        translationUserInput = transcriptionTextView.text.toString()
                        transcriptionTextView.text = response
                        translationGptResponse = response
                    } else {
                        showToast("Error al obtener respuesta")
                    }
                }
            }
        }else{
            transcriptionTextView.text = if (transcriptionTextView.text == translationUserInput) translationGptResponse else translationUserInput
        }
    }

    private fun getOutputFilePath(): String {
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir!!.exists()) {
            downloadsDir.mkdirs()
        }
        return "${downloadsDir.absolutePath}/audiorecordtest.mp3"
    }

    private fun startRecording() {
        if (isRecording) return

        outputFilePath = getOutputFilePath()
        mediaRecorder = MediaRecorder().apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC) // or VOICE_RECOGNITION
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFilePath)
                prepare()
                start()
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to start recording")
                release()
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder.apply {
                stop()
                release()
            }
            isRecording = false
        } catch (e: RuntimeException) {
            e.printStackTrace()
            showToast("Error stopping recording")
        }
    }

    private fun cancelRecording() {
        if (isRecording) {
            mediaRecorder.apply {
                stop()
                release()
            }
            isRecording = false
            val file = File(outputFilePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun transcribeAudio() {
        speechToTextClient.transcribeAudio(outputFilePath) { transcription ->
            runOnUiThread {
                if (transcription != null) {
                    // Create a new TextView to display the transcription
                    val transcriptionTextView = TextView(this).apply {
                        text = transcription
                        textSize = 18f // Same as 18sp
                        setTextColor(resources.getColor(android.R.color.black, null))
                        background = resources.getDrawable(R.drawable.user_prompt_bubble, null)
                        setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                        setShadowLayer(4f, 2f, 2f, resources.getColor(android.R.color.darker_gray, null))
                        elevation = 2f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                            gravity = Gravity.START
                        }
                    }

                    // Find the chatContainer and add the new TextView to it
                    val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
                    chatContainer.addView(transcriptionTextView)

                    // Scroll to the bottom to show the new message
                    val chatScrollView = findViewById<ScrollView>(R.id.chatScrollView)
                    chatScrollView.post {
                        chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }

                    getAnswer(transcription)
                } else {
                    showToast("Error al transcribir el audio")
                }
            }
        }
    }

    // Extension function to convert dp to px
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }



    private fun getAnswer(transcription: String) {
        messages.add(OpenAIChat.Message("user", transcription))

        chatClient.createChatCompletion(messages) { response ->
            runOnUiThread {
                if (response != null) {
                    val responseTextView = TextView(this).apply {
                        text = response
                        textSize = 18f // Same as 18sp
                        setTextColor(resources.getColor(android.R.color.black, null))
                        background = resources.getDrawable(R.drawable.openai_response_bubble, null)
                        setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                        setShadowLayer(4f, 2f, 2f, resources.getColor(android.R.color.darker_gray, null))
                        elevation = 2f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                            gravity = Gravity.START
                        }
                    }
                    // Find the chatContainer and add the new TextView to it
                    val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
                    chatContainer.addView(responseTextView)

                    // Scroll to the bottom to show the new message
                    val chatScrollView = findViewById<ScrollView>(R.id.chatScrollView)
                    chatScrollView.post {
                        chatScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                    messages.add(OpenAIChat.Message("assistant", response))
                    getSpeech(response)
                } else {
                    showToast("Error al obtener respuesta")
                }
            }
        }
    }

    private fun getSpeech(response: String) {
        val outputFilePath = getOutputFilePath()
        ttsClient.createSpeech(response, outputFilePath = outputFilePath) { success ->
            runOnUiThread {
                if (success) {
                    playAudio(outputFilePath)
                } else {
                    showToast("Error al generar el audio")
                }
            }
        }
    }

    private fun playAudio(filePath: String) {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (e: IOException) {
            e.printStackTrace()
            showToast("Error al reproducir el audio")
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                prepareRecording()
            } else {
                // Permission denied
                showToast("Recording permission denied")
            }
        }
    }
    private fun prepareRecording() {
        if (isRecording) return

        outputFilePath = getOutputFilePath() // Set the output file path for the recording

        mediaRecorder = MediaRecorder().apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC) // Set the audio source to the microphone
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Set the output format to MP4
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // Set the audio encoder to AAC
                setOutputFile(outputFilePath) // Set the output file where the recording will be saved
                prepare() // Prepare the MediaRecorder for recording
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to prepare recording") // Display an error message if preparation fails
            }
        }
    }
}
