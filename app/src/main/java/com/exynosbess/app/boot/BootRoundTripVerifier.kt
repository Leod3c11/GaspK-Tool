package com.exynosbess.app.boot

import android.content.Context
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile

object BootRoundTripVerifier {

    data class DtbCheck(
        val index: Int,
        val originalDts: String,
        val roundTripDts: String,
        val equal: Boolean,
        val message: String
    )

    data class VerifyResult(
        val success: Boolean,
        val checks: List<DtbCheck>,
        val message: String
    )

    fun verify(context: Context): VerifyResult {

        try {

            val modifiedBoot =
                File(
                    context.filesDir,
                    "boot_output/boot_modified.img"
                )

            if (!modifiedBoot.exists()) {
                return fail(
                    "boot_modified.img não encontrado"
                )
            }

            val originalWorkspace =
                File(
                    context.filesDir,
                    "workspace"
                )

            val originalDtbDir =
                File(
                    originalWorkspace,
                    "dtbs"
                )

            if (!originalDtbDir.exists()) {
                return fail(
                    "Diretório original de DTS não encontrado"
                )
            }

            val magiskboot =
                ToolManager.installMagiskBoot(context)

            if (
                !magiskboot.success ||
                magiskboot.path == null
            ) {
                return fail(
                    "Falha preparando magiskboot:\n" +
                        magiskboot.message
                )
            }

            val dtc =
                ToolManager.installDtc(context)

            if (
                !dtc.success ||
                dtc.path == null
            ) {
                return fail(
                    "Falha preparando dtc:\n" +
                        dtc.message
                )
            }

            /*
             * Diretório limpo para verificar o
             * boot já reconstruído.
             */
            val verifyWorkspace =
                File(
                    context.filesDir,
                    "roundtrip_workspace"
                )

            if (verifyWorkspace.exists()) {
                verifyWorkspace.deleteRecursively()
            }

            verifyWorkspace.mkdirs()

            /*
             * Unpack do boot_modified.img.
             */
            val unpack =
                ShellExecutor.root(
                    "cd '${verifyWorkspace.absolutePath}' && " +
                    "'${magiskboot.path}' unpack " +
                    "'${modifiedBoot.absolutePath}'"
                )

            if (!unpack.success) {
                return fail(
                    "Falha desempacotando boot_modified.img:\n\n" +
                        unpack.output
                )
            }

            val extra =
                File(
                    verifyWorkspace,
                    "extra"
                )

            if (!extra.exists()) {
                return fail(
                    "O boot reconstruído não contém extra"
                )
            }

            /*
             * Localizar DTBs no EXTRA reconstruído.
             */
            val scan =
                DtbScanner.scan(
                    verifyWorkspace.absolutePath
                )

            if (!scan.success) {
                return fail(
                    "Nenhum DTB localizado após round-trip:\n" +
                        scan.message
                )
            }

            val entries =
                scan.entries
                    .filter {
                        it.sourceFile == "extra"
                    }
                    .sortedBy {
                        it.offset
                    }

            if (entries.isEmpty()) {
                return fail(
                    "Nenhum DTB encontrado no extra reconstruído"
                )
            }

            /*
             * Quantidade esperada = quantidade de DTS
             * usados na reconstrução.
             */
            val originalDtsFiles =
                originalDtbDir
                    .listFiles()
                    ?.filter {
                        it.isFile &&
                        it.name.matches(
                            Regex("""dtb_\d+\.dts""")
                        )
                    }
                    ?.sortedBy {
                        extractIndex(it.name)
                    }
                    ?: emptyList()

            if (
                entries.size !=
                originalDtsFiles.size
            ) {
                return fail(
                    "Quantidade de DTBs mudou.\n\n" +
                    "Antes: ${originalDtsFiles.size}\n" +
                    "Depois: ${entries.size}"
                )
            }

            val extractedDir =
                File(
                    verifyWorkspace,
                    "roundtrip_dtbs"
                )

            extractedDir.mkdirs()

            val checks =
                mutableListOf<DtbCheck>()

            entries.forEachIndexed { index, entry ->

                val extractedDtb =
                    File(
                        extractedDir,
                        "dtb_${index}.dtb"
                    )

                extractRegion(
                    extra,
                    extractedDtb,
                    entry.offset,
                    entry.size
                )

                if (!hasValidMagic(extractedDtb)) {
                    return fail(
                        "DTB $index extraído no round-trip possui magic inválido"
                    )
                }

                val roundTripDts =
                    File(
                        extractedDir,
                        "dtb_${index}.dts"
                    )

                val decompile =
                    ShellExecutor.root(
                        "'${dtc.path}' " +
                        "-I dtb " +
                        "-O dts " +
                        "-o '${roundTripDts.absolutePath}' " +
                        "'${extractedDtb.absolutePath}'"
                    )

                if (
                    !decompile.success ||
                    !roundTripDts.exists()
                ) {
                    return fail(
                        "Falha decompilando DTB $index do round-trip:\n\n" +
                        decompile.output
                    )
                }

                val originalDts =
                    File(
                        originalDtbDir,
                        "dtb_${index}.dts"
                    )

                if (!originalDts.exists()) {
                    return fail(
                        "DTS original $index não encontrado"
                    )
                }

                val originalNormalized =
                    normalizeDts(
                        originalDts.readText()
                    )

                val roundTripNormalized =
                    normalizeDts(
                        roundTripDts.readText()
                    )

                val equal =
                    originalNormalized ==
                        roundTripNormalized

                checks.add(
                    DtbCheck(
                        index = index,
                        originalDts =
                            originalDts.absolutePath,
                        roundTripDts =
                            roundTripDts.absolutePath,
                        equal = equal,
                        message =
                            if (equal)
                                "Estrutura DTS preservada"
                            else
                                "Conteúdo DTS diferente após round-trip"
                    )
                )
            }

            val allEqual =
                checks.all {
                    it.equal
                }

            return if (allEqual) {

                VerifyResult(
                    success = true,
                    checks = checks,
                    message =
                        "Round-trip concluído com sucesso.\n" +
                        "${checks.size} DTB(s) verificado(s)."
                )

            } else {

                VerifyResult(
                    success = false,
                    checks = checks,
                    message =
                        "Round-trip concluído, mas existem diferenças."
                )
            }

        } catch (e: Exception) {

            return fail(
                e.stackTraceToString()
            )
        }
    }

    private fun extractRegion(
        source: File,
        output: File,
        offset: Long,
        size: Long
    ) {

        RandomAccessFile(
            source,
            "r"
        ).use { input ->

            output
                .outputStream()
                .buffered()
                .use { out ->

                    input.seek(offset)

                    var remaining =
                        size

                    val buffer =
                        ByteArray(
                            1024 * 1024
                        )

                    while (remaining > 0) {

                        val wanted =
                            minOf(
                                buffer.size.toLong(),
                                remaining
                            ).toInt()

                        val read =
                            input.read(
                                buffer,
                                0,
                                wanted
                            )

                        if (read <= 0) {
                            throw IllegalStateException(
                                "Fim inesperado durante extração"
                            )
                        }

                        out.write(
                            buffer,
                            0,
                            read
                        )

                        remaining -=
                            read
                    }
                }
        }
    }

    private fun hasValidMagic(
        file: File
    ): Boolean {

        if (
            !file.exists() ||
            file.length() < 4
        ) {
            return false
        }

        RandomAccessFile(
            file,
            "r"
        ).use { raf ->

            val magic =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            return magic ==
                0xD00DFEEDL
        }
    }

    private fun normalizeDts(
        text: String
    ): String {

        return text
            .lineSequence()
            .map {
                it.trim()
            }
            .filter {
                it.isNotEmpty()
            }
            .joinToString("\n")
    }

    private fun extractIndex(
        name: String
    ): Int {

        return Regex("""dtb_(\d+)\.dts""")
            .find(name)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE
    }

    private fun fail(
        message: String
    ): VerifyResult {

        return VerifyResult(
            success = false,
            checks = emptyList(),
            message = message
        )
    }
}
