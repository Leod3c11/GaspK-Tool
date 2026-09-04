package com.exynosbess.app.dtbfile

import android.content.Context
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionScanner
import com.exynosbess.app.partition.PartitionType
import java.io.File

object InstalledDtbExporter {

    data class Result(
        val success: Boolean,
        val file: File? = null,
        val partition: PartitionInfo? = null,
        val message: String
    )

    fun dump(
        context: Context
    ): Result {

        val scan =
            PartitionScanner.scan()

        if (!scan.success) {
            return Result(
                success = false,
                message = scan.message
            )
        }

        val candidates =
            scan.partitions.filter {
                it.type == PartitionType.DTB
            }

        if (candidates.isEmpty()) {
            return Result(
                success = false,
                message =
                    "Nenhuma partição DTB encontrada no aparelho."
            )
        }

        val activeSlot =
            scan.activeSlotSuffix

        val partition =
            if (activeSlot != null) {
                candidates.firstOrNull {
                    it.slotSuffix == activeSlot
                } ?: candidates.firstOrNull {
                    it.slotSuffix == null
                } ?: candidates.first()
            } else {
                candidates.firstOrNull {
                    it.slotSuffix == null
                } ?: candidates.first()
            }

        val dir =
            File(
                context.cacheDir,
                "installed_dtb"
            )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val output =
            File(
                dir,
                "installed_dtb.img"
            )

        if (output.exists()) {
            output.delete()
        }

        val uid =
            android.os.Process.myUid()

        val alias =
            shellEscape(
                partition.aliasPath
            )

        val fallback =
            shellEscape(
                partition.resolvedPath
            )

        val destination =
            shellEscape(
                output.absolutePath
            )

        val command =
            """
            set +e

            SRC="$(
                readlink -f '$alias' 2>/dev/null
            )"

            if [ -z "$SRC" ] || [ ! -e "$SRC" ]; then
                SRC='$fallback'
            fi

            [ -e "$SRC" ] || {
                echo "Partição DTB não encontrada."
                return 20 2>/dev/null || true
            }

            dd if="$SRC" of='$destination' bs=1048576 2>/dev/null
            RESULT=$?

            if [ "$RESULT" -ne 0 ]; then
                echo "Falha lendo partição DTB."
            else
                chown $uid:$uid '$destination' 2>/dev/null
                chmod 600 '$destination' 2>/dev/null
            fi
            """.trimIndent()

        val result =
            ShellExecutor.root(
                command
            )

        if (
            !result.success ||
            !output.exists() ||
            output.length() <= 0L
        ) {

            output.delete()

            return Result(
                success = false,
                partition = partition,
                message =
                    "Falha ao ler a partição DTB instalada.\n\n" +
                        result.output
            )
        }

        if (
            partition.sizeBytes != null &&
            partition.sizeBytes > 0L &&
            output.length() != partition.sizeBytes
        ) {

            val actual =
                output.length()

            output.delete()

            return Result(
                success = false,
                partition = partition,
                message =
                    "O dump da partição ficou incompleto.\n\n" +
                        "Esperado: ${partition.sizeBytes} bytes\n" +
                        "Obtido: $actual bytes"
            )
        }

        return Result(
            success = true,
            file = output,
            partition = partition,
            message =
                "Partição ${partition.name} lida com sucesso."
        )
    }

    private fun shellEscape(
        value: String
    ): String {

        return value.replace(
            "'",
            "'\\''"
        )
    }
}
