package com.exynosbess.app.dtbfile

import android.content.Context
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionScanner
import com.exynosbess.app.partition.PartitionType
import com.exynosbess.app.tools.ToolManager
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

        /*
         * IMPORTANTE:
         *
         * Esta ferramenta NÃO exporta a partição DTB separada.
         *
         * Ela lê a BOOT atualmente instalada e extrai
         * o Device Tree contido nela.
         */
        val candidates =
            scan.partitions.filter {
                it.type == PartitionType.BOOT
            }

        if (candidates.isEmpty()) {
            return Result(
                success = false,
                message =
                    "Nenhuma partição BOOT encontrada no aparelho."
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
                "installed_boot_dtb"
            )

        if (dir.exists()) {
            dir.deleteRecursively()
        }

        dir.mkdirs()

        val bootImage =
            File(
                dir,
                "boot_installed.img"
            )

        val uid =
            android.os.Process.myUid()

        /*
         * 1. Ler a BOOT real instalada.
         */
        val dumpCommand =
            "dd if='${shellEscape(partition.resolvedPath)}' " +
                "of='${shellEscape(bootImage.absolutePath)}' " +
                "bs=1048576 2>/dev/null"

        val dumpResult =
            ShellExecutor.root(
                dumpCommand
            )

        if (
            !dumpResult.success ||
            !bootImage.exists() ||
            bootImage.length() <= 0L
        ) {
            bootImage.delete()

            return Result(
                success = false,
                partition = partition,
                message =
                    "Falha ao ler a BOOT instalada.\n\n" +
                        dumpResult.output
            )
        }

        ShellExecutor.root(
            "chown $uid:$uid '${shellEscape(bootImage.absolutePath)}' 2>/dev/null; " +
                "chmod 600 '${shellEscape(bootImage.absolutePath)}' 2>/dev/null"
        )

        /*
         * 2. Preparar o mesmo magiskboot já usado pelo app.
         */
        val tool =
            ToolManager.installMagiskBoot(
                context
            )

        if (
            !tool.success ||
            tool.path == null
        ) {
            return Result(
                success = false,
                partition = partition,
                message =
                    "Falha preparando magiskboot.\n\n" +
                        tool.message
            )
        }

        val workspace =
            File(
                dir,
                "unpack"
            )

        workspace.mkdirs()

        /*
         * 3. Extrair a BOOT.
         *
         * Quando o Device Tree está anexado ao kernel,
         * magiskboot gera kernel_dtb.
         *
         * Também aceitamos dtb como fallback.
         */
        val unpackCommand =
            "cd '${shellEscape(workspace.absolutePath)}'; " +
                "'${shellEscape(tool.path)}' unpack " +
                "'${shellEscape(bootImage.absolutePath)}'"

        val unpackResult =
            ShellExecutor.root(
                unpackCommand
            )

        /*
         * Não confiar no nome gerado pelo magiskboot.
         *
         * Dependendo do aparelho/boot, o Device Tree pode aparecer como:
         *
         * dtb
         * kernel_dtb
         * extra
         * ou qualquer outro nome.
         *
         * A identificação real é feita pelo magic FDT:
         *
         * D0 0D FE ED
         */
        val extracted =
            findDtbComponent(
                workspace
            )

        if (extracted == null) {
            return Result(
                success = false,
                partition = partition,
                message =
                    "Nenhum DTB foi encontrado dentro da BOOT instalada.\n\n" +
                        "BOOT: ${partition.name}\n\n" +
                        unpackResult.output
            )
        }

        /*
         * 4. Copiar o DTB extraído para um arquivo acessível
         * pelo app.
         */
        val output =
            File(
                dir,
                "installed_boot_dtb.dtb"
            )

        val copyResult =
            ShellExecutor.root(
                "cp '${shellEscape(extracted.absolutePath)}' " +
                    "'${shellEscape(output.absolutePath)}'"
            )

        ShellExecutor.root(
            "chown $uid:$uid '${shellEscape(output.absolutePath)}' 2>/dev/null; " +
                "chmod 600 '${shellEscape(output.absolutePath)}' 2>/dev/null"
        )

        if (
            !copyResult.success ||
            !output.exists() ||
            output.length() <= 0L
        ) {
            output.delete()

            return Result(
                success = false,
                partition = partition,
                message =
                    "Falha ao preparar o DTB extraído da BOOT."
            )
        }

        return Result(
            success = true,
            file = output,
            partition = partition,
            message =
                "DTB extraído da ${partition.name} com sucesso."
        )
    }

    private fun findDtbComponent(
        workspace: File
    ): File? {

        val files =
            workspace.walkTopDown()
                .filter {
                    it.isFile &&
                        it.length() >= 8L
                }
                .toList()

        /*
         * Nomes conhecidos têm prioridade,
         * mas qualquer arquivo pode ser aceito
         * se realmente contiver um FDT.
         */
        val ordered =
            files.sortedWith(
                compareBy<File> {
                    when (
                        it.name.lowercase()
                    ) {
                        "dtb" -> 0
                        "kernel_dtb" -> 1
                        "extra" -> 2
                        else ->
                            if (
                                it.name.lowercase()
                                    .contains("dtb")
                            ) {
                                3
                            } else {
                                4
                            }
                    }
                }.thenBy {
                    it.name
                }
            )

        return ordered.firstOrNull {
            containsFdtMagic(it)
        }
    }

    private fun containsFdtMagic(
        file: File
    ): Boolean {

        val magic =
            byteArrayOf(
                0xD0.toByte(),
                0x0D.toByte(),
                0xFE.toByte(),
                0xED.toByte()
            )

        return try {

            file.inputStream()
                .buffered()
                .use { input ->

                    val buffer =
                        ByteArray(
                            64 * 1024
                        )

                    var carry =
                        ByteArray(0)

                    while (true) {

                        val count =
                            input.read(
                                buffer
                            )

                        if (count <= 0) {
                            break
                        }

                        val data =
                            ByteArray(
                                carry.size + count
                            )

                        carry.copyInto(
                            data,
                            0
                        )

                        buffer.copyInto(
                            data,
                            carry.size,
                            0,
                            count
                        )

                        var i = 0

                        while (
                            i <=
                            data.size -
                                magic.size
                        ) {

                            if (
                                data[i] ==
                                    magic[0] &&
                                data[i + 1] ==
                                    magic[1] &&
                                data[i + 2] ==
                                    magic[2] &&
                                data[i + 3] ==
                                    magic[3]
                            ) {
                                return true
                            }

                            i++
                        }

                        val keep =
                            minOf(
                                3,
                                data.size
                            )

                        carry =
                            data.copyOfRange(
                                data.size - keep,
                                data.size
                            )
                    }

                    false
                }

        } catch (_: Exception) {
            false
        }
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
