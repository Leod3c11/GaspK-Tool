package com.exynosbess.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var rootStatus: TextView
    private lateinit var rootOutput: TextView
    private lateinit var checkRootButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        rootStatus = findViewById(R.id.rootStatus)
        rootOutput = findViewById(R.id.rootOutput)
        checkRootButton = findViewById(R.id.checkRootButton)

        checkRootButton.setOnClickListener {

            rootStatus.text = "Solicitando acesso root..."
            rootOutput.text = ""

            checkRootButton.isEnabled = false

            Thread {

                val result = RootManager.checkRoot()

                runOnUiThread {

                    checkRootButton.isEnabled = true

                    if (result.granted) {

                        rootStatus.text = "✓ Acesso root concedido"

                    } else {

                        rootStatus.text = "✗ Acesso root não concedido"
                    }

                    rootOutput.text =
                        "Resultado:\n${result.output}"
                }

            }.start()
        }
    }
}
