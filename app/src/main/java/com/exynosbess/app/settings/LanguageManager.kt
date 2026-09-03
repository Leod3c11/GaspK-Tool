package com.exynosbess.app.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {

    const val PORTUGUESE =
        "pt-BR"

    const val ENGLISH =
        "en"

    private const val PREFS =
        "gaspkernel_settings"

    private const val KEY_LANGUAGE =
        "language"


    fun current(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_LANGUAGE,
                PORTUGUESE
            )
            ?: PORTUGUESE
    }


    fun set(
        context: Context,
        language: String
    ) {

        val normalized =
            when (language) {

                ENGLISH ->
                    ENGLISH

                else ->
                    PORTUGUESE
            }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_LANGUAGE,
                normalized
            )
            .apply()
    }


    fun wrap(
        context: Context
    ): Context {

        val language =
            current(
                context
            )

        val locale =
            when (language) {

                ENGLISH ->
                    Locale.ENGLISH

                else ->
                    Locale(
                        "pt",
                        "BR"
                    )
            }

        Locale.setDefault(
            locale
        )

        val configuration =
            Configuration(
                context.resources.configuration
            )

        configuration.setLocale(
            locale
        )

        configuration.setLayoutDirection(
            locale
        )

        return context.createConfigurationContext(
            configuration
        )
    }


    fun isEnglish(
        context: Context
    ): Boolean {

        return current(context) ==
            ENGLISH
    }
}
