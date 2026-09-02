package com.exynosbess.app.device

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.exynosbess.app.boot.ShellExecutor
import java.io.File

object DeviceInfoProvider {

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val board: String,
        val hardware: String,
        val soc: String,
        val architecture: String,
        val androidVersion: String,
        val sdk: Int,
        val securityPatch: String,
        val kernel: String,
        val slot: String,
        val abDevice: String,
        val root: String,
        val magisk: String,
        val selinux: String,
        val verifiedBoot: String,
        val bootloader: String,
        val storage: String
    )

    fun collect(
        context: Context
    ): DeviceInfo {

        val slotSuffix =
            shell(
                "getprop ro.boot.slot_suffix"
            )

        val slot =
            when {
                slotSuffix.isNotBlank() ->
                    slotSuffix.removePrefix("_")

                else -> {
                    val slotProperty =
                        shell(
                            "getprop ro.boot.slot"
                        )

                    if (slotProperty.isBlank()) {
                        "Não aplicável"
                    } else {
                        slotProperty
                    }
                }
            }

        val abUpdate =
            shell(
                "getprop ro.build.ab_update"
            )

        val dynamicPartitions =
            shell(
                "getprop ro.boot.dynamic_partitions"
            )

        val abDevice =
            when {
                abUpdate.equals(
                    "true",
                    ignoreCase = true
                ) ->
                    "Sim"

                slot != "Não aplicável" ->
                    "Provável"

                else ->
                    "Não detectado"
            }

        val rootResult =
            ShellExecutor.root(
                "id"
            )

        val root =
            if (
                rootResult.success &&
                rootResult.output.contains(
                    "uid=0"
                )
            ) {
                "Disponível"
            } else {
                "Indisponível"
            }

        val magiskVersion =
            shell(
                "magisk -v 2>/dev/null"
            )

        val magisk =
            if (magiskVersion.isBlank()) {
                "Não detectado"
            } else {
                magiskVersion
            }

        val enforcing =
            shell(
                "getenforce 2>/dev/null"
            )

        val selinux =
            if (enforcing.isBlank()) {
                "Desconhecido"
            } else {
                enforcing
            }

        val kernel =
            shell(
                "uname -a"
            ).ifBlank {
                System.getProperty(
                    "os.version"
                ) ?: "Desconhecido"
            }

        val verifiedBootState =
            shell(
                "getprop ro.boot.verifiedbootstate"
            )

        val bootloader =
            Build.BOOTLOADER
                .takeUnless {
                    it.isBlank() ||
                        it.equals(
                            "unknown",
                            ignoreCase = true
                        )
                }
                ?: shell(
                    "getprop ro.bootloader"
                )
                    .ifBlank {
                        "Desconhecido"
                    }

        val soc =
            detectSoc()

        val architecture =
            Build.SUPPORTED_ABIS
                .joinToString(", ")
                .ifBlank {
                    Build.CPU_ABI
                }

        val storage =
            storageInfo(context)

        return DeviceInfo(
            manufacturer =
                Build.MANUFACTURER
                    .ifBlank {
                        "Desconhecido"
                    },

            model =
                Build.MODEL
                    .ifBlank {
                        "Desconhecido"
                    },

            device =
                Build.DEVICE
                    .ifBlank {
                        "Desconhecido"
                    },

            product =
                Build.PRODUCT
                    .ifBlank {
                        "Desconhecido"
                    },

            board =
                Build.BOARD
                    .ifBlank {
                        "Desconhecido"
                    },

            hardware =
                Build.HARDWARE
                    .ifBlank {
                        "Desconhecido"
                    },

            soc = soc,

            architecture =
                architecture,

            androidVersion =
                Build.VERSION.RELEASE
                    .ifBlank {
                        "Desconhecido"
                    },

            sdk =
                Build.VERSION.SDK_INT,

            securityPatch =
                Build.VERSION.SECURITY_PATCH
                    .ifBlank {
                        "Desconhecido"
                    },

            kernel =
                kernel,

            slot =
                slot,

            abDevice =
                abDevice,

            root =
                root,

            magisk =
                magisk,

            selinux =
                selinux,

            verifiedBoot =
                verifiedBootState
                    .ifBlank {
                        "Desconhecido"
                    },

            bootloader =
                bootloader,

            storage =
                storage
        )
    }

    private fun detectSoc(): String {

        if (
            Build.VERSION.SDK_INT >= 31 &&
            Build.SOC_MODEL.isNotBlank()
        ) {
            return Build.SOC_MODEL
        }

        val candidates =
            listOf(
                "getprop ro.soc.model",
                "getprop ro.board.platform",
                "getprop ro.hardware"
            )

        for (command in candidates) {

            val value =
                shell(command)

            if (value.isNotBlank()) {
                return value
            }
        }

        return Build.HARDWARE
            .ifBlank {
                "Desconhecido"
            }
    }

    private fun storageInfo(
        context: Context
    ): String {

        return try {

            val stat =
                StatFs(
                    context.filesDir.absolutePath
                )

            val total =
                stat.totalBytes

            val free =
                stat.availableBytes

            "${formatBytes(free)} livres / " +
                "${formatBytes(total)}"

        } catch (_: Exception) {
            "Desconhecido"
        }
    }

    private fun formatBytes(
        bytes: Long
    ): String {

        val gb =
            bytes.toDouble() /
                (1024.0 * 1024.0 * 1024.0)

        return String.format(
            "%.2f GB",
            gb
        )
    }

    private fun shell(
        command: String
    ): String {

        val result =
            ShellExecutor.root(
                command
            )

        return if (result.success) {
            result.output.trim()
        } else {
            ""
        }
    }
}
