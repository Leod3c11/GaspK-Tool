package com.exynosbess.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.exynosbess.app.device.DeviceInfoProvider
import com.exynosbess.app.logging.GaspLog

class DeviceInfoActivity : LocalizedActivity() {

    private lateinit var status:
        TextView

    private lateinit var hardware:
        TextView

    private lateinit var software:
        TextView

    private lateinit var security:
        TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_device_info
        )

        status =
            findViewById(
                R.id.deviceInfoStatus
            )

        hardware =
            findViewById(
                R.id.deviceInfoHardware
            )

        software =
            findViewById(
                R.id.deviceInfoSoftware
            )

        security =
            findViewById(
                R.id.deviceInfoSecurity
            )

        load()
    }

    private fun load() {

        status.text =
            "Coletando informações..."

        Thread {

            try {

                val info =
                    DeviceInfoProvider.collect(
                        this
                    )

                GaspLog.info(
                    this,
                    "Informações do dispositivo consultadas"
                )

                runOnUiThread {

                    hardware.text =
                        buildString {
                            appendLine(
                                "Fabricante"
                            )
                            appendLine(
                                info.manufacturer
                            )

                            appendLine()
                            appendLine(
                                "Modelo"
                            )
                            appendLine(
                                info.model
                            )

                            appendLine()
                            appendLine(
                                "Device"
                            )
                            appendLine(
                                info.device
                            )

                            appendLine()
                            appendLine(
                                "Produto"
                            )
                            appendLine(
                                info.product
                            )

                            appendLine()
                            appendLine(
                                "SoC"
                            )
                            appendLine(
                                info.soc
                            )

                            appendLine()
                            appendLine(
                                "Board"
                            )
                            appendLine(
                                info.board
                            )

                            appendLine()
                            appendLine(
                                "Hardware"
                            )
                            appendLine(
                                info.hardware
                            )

                            appendLine()
                            appendLine(
                                "Arquitetura"
                            )
                            append(
                                info.architecture
                            )
                        }

                    software.text =
                        buildString {

                            appendLine(
                                "Android"
                            )
                            appendLine(
                                "${info.androidVersion} (API ${info.sdk})"
                            )

                            appendLine()
                            appendLine(
                                "Patch de segurança"
                            )
                            appendLine(
                                info.securityPatch
                            )

                            appendLine()
                            appendLine(
                                "Kernel"
                            )
                            appendLine(
                                info.kernel
                            )

                            appendLine()
                            appendLine(
                                "Bootloader"
                            )
                            appendLine(
                                info.bootloader
                            )

                            appendLine()
                            appendLine(
                                "Slot ativo"
                            )
                            appendLine(
                                info.slot
                            )

                            appendLine()
                            appendLine(
                                "Dispositivo A/B"
                            )
                            appendLine(
                                info.abDevice
                            )

                            appendLine()
                            appendLine(
                                "Armazenamento"
                            )
                            append(
                                info.storage
                            )
                        }

                    security.text =
                        buildString {

                            appendLine(
                                "Root"
                            )
                            appendLine(
                                info.root
                            )

                            appendLine()
                            appendLine(
                                "Magisk"
                            )
                            appendLine(
                                info.magisk
                            )

                            appendLine()
                            appendLine(
                                "SELinux"
                            )
                            appendLine(
                                info.selinux
                            )

                            appendLine()
                            appendLine(
                                "Verified Boot"
                            )
                            append(
                                info.verifiedBoot
                            )
                        }

                    status.text =
                        "✓ Informações atualizadas"
                }

            } catch (e: Exception) {

                GaspLog.error(
                    this,
                    "Falha ao consultar dispositivo: " +
                        (e.message ?: "erro desconhecido")
                )

                runOnUiThread {

                    status.text =
                        "Falha ao coletar informações"
                }
            }

        }.start()
    }
}
