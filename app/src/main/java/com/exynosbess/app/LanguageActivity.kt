package com.exynosbess.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.exynosbess.app.settings.LanguageManager
import com.google.android.material.card.MaterialCardView

class LanguageActivity :
    LocalizedActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_language
        )

        val current =
            LanguageManager.current(
                this
            )

        findViewById<TextView>(
            R.id.languageCurrent
        ).text =
            when (current) {

                LanguageManager.ENGLISH ->
                    "English"

                else ->
                    "Português (Brasil)"
            }


        findViewById<MaterialCardView>(
            R.id.languagePortugueseCard
        ).setOnClickListener {

            select(
                LanguageManager.PORTUGUESE
            )
        }


        findViewById<MaterialCardView>(
            R.id.languageEnglishCard
        ).setOnClickListener {

            select(
                LanguageManager.ENGLISH
            )
        }
    }


    private fun select(
        language: String
    ) {

        LanguageManager.set(
            this,
            language
        )

        val intent =
            Intent(
                this,
                DashboardActivity::class.java
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        )

        startActivity(
            intent
        )

        finish()
    }
}
