package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.exynosbess.app.logging.GaspLog

class MainActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        findViewById<LinearLayout>(
            R.id.toolsDeviceInfoCard
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    DeviceInfoActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.toolsLogsCard
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    GaspLogsActivity::class.java
                )
            )
        }

        GaspLog.info(
            this,
            "Ferramentas avançadas abertas"
        )
    }
}
