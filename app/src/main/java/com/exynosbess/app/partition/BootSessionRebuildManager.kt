package com.exynosbess.app.partition

import android.content.Context
import android.os.Process
import com.exynosbess.app.boot.BootDtbComponentRebuilder
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.tools.ToolManager
import java.io.File

object BootSessionRebuildManager {

    data class Result(
        val success: Boolean,
        val message: String,
        val output: File? = null
    )

    fun rebuild(
        context: Context,
        partition: PartitionInfo
    ): Result {

        if (partition.type != PartitionType.BOOT) {
            return Result(
                false,
                "Reconstrução BOOT chamada para ${partition.type}."
            )
        }

        val session =
            PartitionWorkspace.get(
                context,
                partition
            )

        val original =
            session.original

        val workspace =
            session.extracted

        if (!original.exists() || original.length() <= 0L) {
            return Result(
                false,
                "original.img não encontrado."
            )
        }

        if (!workspace.exists()) {
            return Result(
                false,
                "Boot ainda não foi extraído."
            )
        }

        val dtbResult =
            BootDtbComponentRebuilder.rebuild(
                context,
                workspace
            )

        if (!dtbResult.success) {
            return Result(
                false,
                "Falha reconstruindo Device Trees:\n\n" +
                    dtbResult.message
            )
        }

        val tool =
            ToolManager.installMagiskBoot(
                context
            )

        if (!tool.success || tool.path == null) {
            return Result(
                false,
                "magiskboot indisponível: ${tool.message}"
            )
        }

        val repack =
            File(
                session.root,
                "repack"
            )

        if (repack.exists()) {
            repack.deleteRecursively()
        }

        if (!repack.mkdirs()) {
            return Result(
                false,
                "Não foi possível criar workspace de repack."
            )
        }

        val rebuiltComponents =
            File(
                workspace,
                "rebuilt_boot_components"
            )

        val components =
            listOf(
                "kernel",
                "ramdisk.cpio",
                "second",
                "dtb",
                "kernel_dtb",
                "extra",
                "recovery_dtbo",
                "header"
            )

        try {

            components.forEach { name ->

                val rebuilt =
                    File(
                        rebuiltComponents,
                        name
                    )

                val originalComponent =
                    File(
                        workspace,
                        name
                    )

                val source =
                    when {
                        rebuilt.exists() ->
                            rebuilt

                        originalComponent.exists() ->
                            originalComponent

                        else ->
                            null
                    }

                source?.copyTo(
                    File(repack, name),
                    overwrite = true
                )
            }

        } catch (e: Exception) {

            return Result(
                false,
                "Falha preparando componentes: ${e.message}"
            )
        }

        val appUid =
            Process.myUid()

        ShellExecutor.root(
            "chown -R $appUid:$appUid ${quote(repack.absolutePath)} && " +
                "chmod -R u+rwX ${quote(repack.absolutePath)}"
        )

        val command =
            "cd ${quote(repack.absolutePath)} && " +
                "${quote(tool.path)} repack " +
                "${quote(original.absolutePath)}"

        val result =
            ShellExecutor.root(
                command
            )

        if (!result.success) {
            return Result(
                false,
                "magiskboot repack falhou:\n\n${result.output}"
            )
        }

        val candidates =
            listOf(
                File(repack, "new-boot.img"),
                File(repack, "new_boot.img"),
                File(repack, "boot.img")
            )

        val generated =
            candidates.firstOrNull {
                it.exists() &&
                    it.isFile &&
                    it.length() > 0L
            }
                ?: repack.listFiles()
                    ?.firstOrNull {
                        it.isFile &&
                            it.length() > 0L &&
                            it.name.contains(
                                "boot",
                                ignoreCase = true
                            ) &&
                            it.extension == "img"
                    }

        if (generated == null) {
            return Result(
                false,
                "magiskboot terminou, mas nenhuma imagem reconstruída foi encontrada.\n\n" +
                    result.output
            )
        }

        try {

            generated.copyTo(
                session.rebuilt,
                overwrite = true
            )

        } catch (e: Exception) {

            return Result(
                false,
                "Falha criando rebuilt.img: ${e.message}"
            )
        }

        if (
            !session.rebuilt.exists() ||
            session.rebuilt.length() <= 0L
        ) {
            return Result(
                false,
                "rebuilt.img inválido."
            )
        }

        return Result(
            true,
            "BOOT reconstruído com sucesso.\n\n" +
                "Arquivo: ${session.rebuilt.absolutePath}\n" +
                "Tamanho: ${session.rebuilt.length()} bytes",
            session.rebuilt
        )
    }

    private fun quote(
        value: String
    ): String =
        "'" + value.replace("'", "'\\''") + "'"
}
