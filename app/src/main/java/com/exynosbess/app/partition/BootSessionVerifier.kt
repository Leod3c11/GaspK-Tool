package com.exynosbess.app.partition

import android.content.Context
import com.exynosbess.app.boot.DtbScanner
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.tools.ToolManager
import java.io.File

object BootSessionVerifier {

    data class Result(
        val success: Boolean,
        val message: String
    )

    fun verify(
        context: Context,
        partition: PartitionInfo
    ): Result {

        if (partition.type != PartitionType.BOOT) {
            return Result(
                false,
                "Verificação BOOT chamada para ${partition.type}."
            )
        }

        val session =
            PartitionWorkspace.get(
                context,
                partition
            )

        val image =
            session.rebuilt

        if (!image.exists() || image.length() <= 0L) {
            return Result(
                false,
                "rebuilt.img não encontrado."
            )
        }

        if (
            partition.sizeBytes != null &&
            image.length() > partition.sizeBytes
        ) {
            return Result(
                false,
                "Imagem reconstruída é maior que a partição.\n\n" +
                    "Imagem: ${image.length()} bytes\n" +
                    "Partição: ${partition.sizeBytes} bytes"
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

        val verify =
            File(
                session.root,
                "verify"
            )

        if (verify.exists()) {
            verify.deleteRecursively()
        }

        if (!verify.mkdirs()) {
            return Result(
                false,
                "Não foi possível criar diretório de verificação."
            )
        }

        val unpack =
            ShellExecutor.root(
                "cd ${quote(verify.absolutePath)} && " +
                    "${quote(tool.path)} unpack " +
                    "${quote(image.absolutePath)}"
            )

        if (!unpack.success) {
            return Result(
                false,
                "rebuilt.img não passou no unpack:\n\n" +
                    unpack.output
            )
        }

        val files =
            verify.listFiles()
                ?.filter {
                    it.isFile &&
                        it.length() > 0L
                }
                ?: emptyList()

        if (files.isEmpty()) {
            return Result(
                false,
                "Imagem foi aberta, mas nenhum componente válido foi extraído."
            )
        }

        val dtbScan =
            DtbScanner.scan(
                verify.absolutePath
            )

        val dtbText =
            if (dtbScan.success) {
                "${dtbScan.entries.size} DTB(s) detectado(s) após round-trip."
            } else {
                "Nenhum DTB embutido detectado após round-trip."
            }

        return Result(
            true,
            "rebuilt.img passou na verificação estrutural.\n\n" +
                "${files.size} componente(s) extraído(s).\n" +
                dtbText
        )
    }

    private fun quote(
        value: String
    ): String =
        "'" + value.replace("'", "'\\''") + "'"
}
