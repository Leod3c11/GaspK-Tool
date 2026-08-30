package com.exynosbess.app.boot

import android.content.Context
import android.os.Process
import java.io.File

object DtbPartitionBackupManager {

    data class Result(
        val success: Boolean,
        val outputPath: String?,
        val message: String
    )

    fun backup(
        context: Context,
        partitionPath: String
    ): Result {

        return try {

            val workspace =
                File(
                    context.filesDir,
                    "workspace"
                )

            if (!workspace.exists()) {
                workspace.mkdirs()
            }

            val output =
                File(
                    workspace,
                    "dtb_partition"
                )

            if (output.exists()) {

                ShellExecutor.root(
                    "rm -f '${output.absolutePath}'"
                )
            }

            val copy =
                ShellExecutor.root(
                    "dd if='$partitionPath' " +
                        "of='${output.absolutePath}' " +
                        "bs=4096"
                )

            if (!copy.success) {

                return Result(
                    false,
                    null,
                    "Falha ao copiar partição DTB:\n${copy.output}"
                )
            }

            if (
                !output.exists() ||
                output.length() == 0L
            ) {

                return Result(
                    false,
                    null,
                    "A cópia da partição DTB ficou vazia"
                )
            }

            val uid =
                Process.myUid()

            val permissions =
                ShellExecutor.root(
                    "chown $uid:$uid '${output.absolutePath}' && " +
                        "chmod 600 '${output.absolutePath}'"
                )

            if (!permissions.success) {

                return Result(
                    false,
                    null,
                    "DTB copiado, mas houve falha ao ajustar permissões"
                )
            }

            Result(
                true,
                output.absolutePath,
                "Partição DTB copiada"
            )

        } catch (
            e: Exception
        ) {

            Result(
                false,
                null,
                e.message
                    ?: "Erro ao copiar partição DTB"
            )
        }
    }
}
