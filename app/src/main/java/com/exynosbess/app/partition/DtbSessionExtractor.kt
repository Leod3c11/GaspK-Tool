package com.exynosbess.app.partition

import android.content.Context
import com.exynosbess.app.boot.DtbDecompiler
import com.exynosbess.app.boot.DtbExtractor
import com.exynosbess.app.boot.DtbScanner
import java.io.File

object DtbSessionExtractor {

    fun extract(
        context: Context,
        partition: PartitionInfo
    ): PartitionPipeline.Result {

        if (
            partition.type !=
            PartitionType.DTB
        ) {

            return PartitionPipeline.Result(
                false,
                "Extrator DTB chamado para ${partition.type}.",
                null
            )
        }

        val workspace =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        if (
            !workspace.original.exists() ||
            workspace.original.length() <= 0L
        ) {

            return PartitionPipeline.Result(
                false,
                "Importe a partição primeiro.",
                null
            )
        }

        if (workspace.extracted.exists()) {
            workspace.extracted.deleteRecursively()
        }

        workspace.extracted.mkdirs()

        val source =
            File(
                workspace.extracted,
                "dtb_partition"
            )

        try {

            workspace.original.copyTo(
                source,
                overwrite = true
            )

        } catch (e: Exception) {

            return PartitionPipeline.Result(
                false,
                "Falha preparando DTB local: ${e.message}",
                null
            )
        }

        val scan =
            DtbScanner.scan(
                workspace.extracted.absolutePath
            )

        if (
            !scan.success ||
            scan.entries.isEmpty()
        ) {

            return PartitionPipeline.Result(
                false,
                "Nenhum FDT válido encontrado na partição DTB.\n\n" +
                    scan.message,
                null
            )
        }

        val extraction =
            DtbExtractor.extract(
                workspace.extracted.absolutePath,
                scan.entries
            )

        if (!extraction.success) {

            return PartitionPipeline.Result(
                false,
                extraction.message,
                null
            )
        }

        val decompile =
            DtbDecompiler.decompileAll(
                context,
                workspace.extracted.absolutePath
            )

        if (!decompile.success) {

            return PartitionPipeline.Result(
                false,
                decompile.message,
                null
            )
        }

        return PartitionPipeline.Result(
            true,
            buildString {

                append(
                    "Partição DTB extraída com sucesso."
                )

                append(
                    "\n\n${scan.entries.size} DTB(s) encontrado(s)."
                )

                append(
                    "\n${extraction.files.size} DTB(s) extraído(s)."
                )

                append(
                    "\n${decompile.files.size} DTS editável(is)."
                )
            },
            PartitionSessionState.EXTRACTED
        )
    }
}
