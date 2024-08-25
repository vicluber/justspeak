package com.example.languageexchange

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LanguageSelectionActivity : AppCompatActivity() {
    private lateinit var selectedLanguage: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)


        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup_languages)

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            selectedLanguage = when (checkedId) {
                R.id.radio_english -> "English"
                R.id.radio_spanish -> "Spanish"
                R.id.radio_german -> "German"
                R.id.radio_italian -> "Italian"
                R.id.radio_portuguese -> "Portuguese"
                R.id.radio_french -> "French"
                else -> ""
            }
            Toast.makeText(this, "Selected language: $selectedLanguage", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("selectedValue", selectedLanguage)
            startActivity(intent)
            finish()
        }

    }
}