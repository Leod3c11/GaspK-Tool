package com.exynosbess.app.startup

import android.content.Context
import com.exynosbess.app.RootManager
import com.exynosbess.app.boot.BootBackupManager
import com.exynosbess.app.boot.BootPartitionResolver
import com.exynosbess.app.boot.BootUnpackManager
import com.exynosbess.app.boot.DtbDecompiler
import com.exynosbess.app.boot.DtbExtractor
import com.exynosbess.app.boot.DtbScanner

object AutoSetupManager {

    data class Result(
        val success: Boolean,
        val rootGranted: Boolean,
        val bootPath: String?,
        val workspacePath: String?,
        val dtbCount: Int,
        val message: String
    )

    fun run(
        context: Context,
        progress: (String) -> Unit
    ): Result {

        progress("Verificando acesso root...")

        val root =
            RootManager.checkRoot()

        if (!root.granted) {
            return Result(
                false,
                false,
                null,
                null,
                0,
                "Acesso root não concedido"
            )
        }

        progress("Acesso root concedido")

        progress("Detectando partição boot...")

        val partition =
            BootPartitionResolver.detect()

        if (
            !partition.found ||
            partition.path == null
        ) {
            return Result(
                false,
                true,
                null,
                null,
                0,
                "Partição boot não encontrada"
            )
        }

        progress(
            "Partição boot encontrada\n${partition.path}"
        )

        progress("Criando backup do boot atual...")

        val backup =
            BootBackupManager.backup(
                context,
                partition.path
            )

        if (
            !backup.success ||
            backup.outputPath == null
        ) {
            return Result(
                false,
                true,
                partition.path,
                null,
                0,
                "Falha ao criar backup:\n${backup.message}"
            )
        }

        progress("Backup concluído")

        progress("Descompactando boot.img...")

        val unpack =
            BootUnpackManager.unpack(
                context
            )

        if (
            !unpack.success ||
            unpack.workspace == null
        ) {
            return Result(
                false,
                true,
                partition.path,
                null,
                0,
                "Falha ao descompactar boot:\n${unpack.output}"
            )
        }

        progress("Boot.img descompactado")

        progress("Procurando Device Trees...")

        val scan =
            DtbScanner.scan(
                unpack.workspace
            )

        if (
            !scan.success ||
            scan.entries.isEmpty()
        ) {
            return Result(
                false,
                true,
                partition.path,
                unpack.workspace,
                0,
                "Nenhum DTB encontrado"
            )
        }

        progress(
            "${scan.entries.size} DTB(s) encontrado(s)"
        )

        progress("Extraindo Device Trees...")

        val extraction =
            DtbExtractor.extract(
                unpack.workspace,
                scan.entries
            )

        if (!extraction.success) {
            return Result(
                false,
                true,
                partition.path,
                unpack.workspace,
                0,
                "Falha extraindo DTBs:\n${extraction.message}"
            )
        }

        progress(
            "${extraction.files.size} DTB(s) extraído(s)"
        )

        progress("Convertendo DTB para DTS...")

        val decompile =
            DtbDecompiler.decompileAll(
                context,
                unpack.workspace
            )

        if (!decompile.success) {
            return Result(
                false,
                true,
                partition.path,
                unpack.workspace,
                extraction.files.size,
                "Falha convertendo DTB para DTS:\n${decompile.message}"
            )
        }

        progress(
            "${decompile.files.size} DTS(s) preparados"
        )

        progress("Preparação concluída")

        return Result(
            true,
            true,
            partition.path,
            unpack.workspace,
            decompile.files.size,
            "Ambiente preparado"
        )
    }
}
