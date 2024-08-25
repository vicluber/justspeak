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
import android.view.MotionEvent
import android.widget.Button
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

    private lateinit var handler: Handler
    private lateinit var mediaRecorder: MediaRecorder
    private var isRecording = false
    private var isCancelled = false
    private lateinit var outputFilePath: String
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private val apiKey = ""
    private val speechToTextClient = OpenAISpeechToText(apiKey)
    private val chatClient = OpenAIChat(apiKey)
    private val ttsClient = OpenAITTS(apiKey)
    private lateinit var transcriptionTextView: TextView
    private lateinit var responseTextView: TextView
    private lateinit var button: Button
    private lateinit var alternativeButton: Button
    private lateinit var translateButton: Button
    private lateinit var scaleUp: ObjectAnimator
    private lateinit var scaleDown: ObjectAnimator
    private var initialX: Float = 0f
    private lateinit var selectedLanguage: String
    private var alternateUserInput: String? = null
    private var alternateGptResponse: String? = null
    private var translationUserInput: String? = null
    private var translationGptResponse: String? = null

    // Mueve la declaración de messages aquí, pero no la inicialices aún
    private lateinit var messages: MutableList<OpenAIChat.Message>

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguage = intent.getStringExtra("selectedValue") ?: "Default Value"

        // Ahora inicializa messages
        messages = mutableListOf(
            OpenAIChat.Message("system", "You are gonna act as a ${selectedLanguage} teacher who chats with me just for me to learn. So it doesn't matter which language I use you will respond accordingly but in ${selectedLanguage}, for example, if I ask you how to say something in ${selectedLanguage} always reply in ${selectedLanguage} don't use the language I use, always ${selectedLanguage}. No matter what I say, never change the language, ALWAYS speak ${selectedLanguage} with me. If I ask you to speak another language just tell me that is for my good that you will just reply in ${selectedLanguage}.\\n I'm also not good in conversation making so if I don't ask anything you will propose easy topics of conversations.")
        )

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        transcriptionTextView = findViewById(R.id.transcriptionTextView)
        responseTextView = findViewById(R.id.responseTextView)
        requestAudioPermissions()
        handler = Handler(Looper.getMainLooper())
        outputFilePath = getOutputFilePath()

        button = findViewById(R.id.button)
        alternativeButton = findViewById(R.id.alternativeButton)
        translateButton = findViewById(R.id.translateButton)

        // Define the animations
        fun createScaleAnimation(property: String): ObjectAnimator {
            return ObjectAnimator.ofFloat(button, property, 1.2f).apply {
                duration = 500
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
        }

        scaleUp = createScaleAnimation("scaleX")
        scaleDown = createScaleAnimation("scaleY")

        Toast.makeText(this@MainActivity, "Mantén presionado para grabar y suelta para enviar.", Toast.LENGTH_LONG).show()
        Toast.makeText(this@MainActivity, "Desliza a la derecha para cancelar.", Toast.LENGTH_LONG).show()

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

    private fun handleButtonActionDown(event: MotionEvent) {
        initialX = event.rawX
        isCancelled = false
        startRecording()
        handler.post(runnable)
        startButtonAnimation()
    }

    private fun handleButtonActionMove(event: MotionEvent) {
        val deltaX = event.rawX - initialX
        if (deltaX > 100) {
            isCancelled = true
            stopRecording()
            stopButtonAnimation()
            Toast.makeText(this@MainActivity, "Grabación cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleButtonActionUp() {
        handler.removeCallbacks(runnable)
        stopButtonAnimation()
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


    private fun startButtonAnimation() {
        scaleUp.start()
        scaleDown.start()
    }

    private fun stopButtonAnimation() {
        scaleUp.cancel()
        scaleDown.cancel()
        button.scaleX = 1.0f
        button.scaleY = 1.0f
    }

    private val runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, 1000)
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
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFilePath)
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: IOException) {
                e.printStackTrace()
                showToast("Error al iniciar la grabación")
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
            showToast("Error al detener la grabación")
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
                    transcriptionTextView.text = transcription
                    getAnswer(transcription)
                } else {
                    showToast("Error al transcribir el audio")
                }
            }
        }
    }

    private fun getAnswer(transcription: String) {
        messages.add(OpenAIChat.Message("user", transcription))

        chatClient.createChatCompletion(messages) { response ->
            runOnUiThread {
                if (response != null) {
                    responseTextView.text = response
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

    private fun requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
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
            } else {
                // Permission denied
                showToast("Permiso de grabación denegado")
            }
        }
    }
}
