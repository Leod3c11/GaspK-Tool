package com.exynosbess.app.boot

import android.content.Context
import java.io.File

object BootBackupManager {

    data class BackupResult(
        val success: Boolean,
        val outputPath: String?,
        val message: String
    )

    fun backup(
        context: Context,
        bootPath: String
    ): BackupResult {

        val backupDir = File(
            context.filesDir,
            "boot_backups"
        )

        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val output = File(
            backupDir,
            "boot_original.img"
        )

        val command =
            "dd if='$bootPath' of='${output.absolutePath}' bs=4096"

        val result = ShellExecutor.root(command)

        if (!result.success) {
            return BackupResult(
                success = false,
                outputPath = null,
                message = result.output
            )
        }

        if (!output.exists() || output.length() == 0L) {
            return BackupResult(
                success = false,
                outputPath = null,
                message = "Backup criado, mas arquivo está vazio ou inacessível"
            )
        }

        return BackupResult(
            success = true,
            outputPath = output.absolutePath,
            message =
                "Backup criado com sucesso\n" +
                "Tamanho: ${output.length()} bytes"
        )
    }
}
