package com.exynosbess.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.exynosbess.app.logging.GaspLog
import com.exynosbess.app.settings.LanguageManager
import com.google.android.material.card.MaterialCardView

class MainActivity :
    LocalizedActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )


        findViewById<MaterialCardView>(
            R.id.toolsDeviceInfoCard
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    DeviceInfoActivity::class.java
                )
            )
        }


        findViewById<MaterialCardView>(
            R.id.toolsDtbFileCard
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    DtbFileActivity::class.java
                )
            )
        }


        findViewById<MaterialCardView>(
            R.id.toolsLogsCard
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    GaspLogsActivity::class.java
                )
            )
        }


        findViewById<MaterialCardView>(
            R.id.toolsLanguageCard
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    LanguageActivity::class.java
                )
            )
        }


        findViewById<TextView>(
            R.id.toolsLanguageValue
        ).text =
            when (
                LanguageManager.current(
                    this
                )
            ) {

                LanguageManager.ENGLISH ->
                    "English"

                else ->
                    "Português (Brasil)"
            }


        GaspLog.info(
            this,
            "Ferramentas avançadas abertas"
        )
    }
}
