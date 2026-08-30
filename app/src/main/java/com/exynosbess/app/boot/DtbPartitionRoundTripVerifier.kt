package com.exynosbess.app.boot

import android.content.Context
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object DtbPartitionRoundTripVerifier {

    data class DtbCheck(
        val index: Int,
        val equal: Boolean,
        val message: String
    )

    data class Result(
        val success: Boolean,
        val checks: List<DtbCheck>,
        val message: String
    )

    fun verify(
        context: Context
    ): Result {

        try {

            val workspace =
                File(
                    context.filesDir,
                    "workspace"
                )

            val modified =
                File(
                    context.filesDir,
                    "dtb_output/dtb_modified.img"
                )

            if (!modified.exists()) {
                return fail(
                    "dtb_modified.img não encontrado"
                )
            }

            val sourceEntries =
                DtbSourceMap
                    .partitionEntries(
                        workspace.absolutePath
                    )
                    .sortedBy {
                        it.offset
                    }

            if (sourceEntries.isEmpty()) {
                return fail(
                    "Nenhum DTB da partição para verificar"
                )
            }

            val verifyWorkspace =
                File(
                    context.filesDir,
                    "dtb_verify_workspace"
                )

            if (verifyWorkspace.exists()) {
                verifyWorkspace.deleteRecursively()
            }

            verifyWorkspace.mkdirs()

            val verifyImage =
                File(
                    verifyWorkspace,
                    "dtb_partition"
                )

            modified.copyTo(
                verifyImage,
                overwrite = true
            )

            val scan =
                DtbScanner.scan(
                    verifyWorkspace.absolutePath
                )

            if (!scan.success) {
                return fail(
                    "Falha escaneando imagem DTB reconstruída:\n${scan.message}"
                )
            }

            val actualEntries =
                scan.entries
                    .filter {
                        it.sourceFile ==
                            "dtb_partition"
                    }
                    .sortedBy {
                        it.offset
                    }

            if (
                actualEntries.size !=
                sourceEntries.size
            ) {
                return fail(
                    "Quantidade de DTBs mudou após rebuild. " +
                        "Esperado=${sourceEntries.size}, " +
                        "encontrado=${actualEntries.size}"
                )
            }

            val dtc =
                ToolManager.installDtc(
                    context
                )

            if (
                !dtc.success ||
                dtc.path == null
            ) {
                return fail(
                    "Falha preparando dtc:\n${dtc.message}"
                )
            }

            val expectedDir =
                File(
                    verifyWorkspace,
                    "expected"
                )

            expectedDir.mkdirs()

            val checks =
                mutableListOf<DtbCheck>()

            sourceEntries.forEachIndexed {
                localIndex,
                sourceEntry ->

                val actualEntry =
                    actualEntries[
                        localIndex
                    ]

                if (
                    actualEntry.offset !=
                    sourceEntry.offset
                ) {

                    checks.add(
                        DtbCheck(
                            sourceEntry.index,
                            false,
                            "Offset mudou"
                        )
                    )

                    return Result(
                        false,
                        checks,
                        "Offset do DTB ${sourceEntry.index} mudou"
                    )
                }

                val dts =
                    File(
                        workspace,
                        "dtbs/dtb_${sourceEntry.index}.dts"
                    )

                if (!dts.exists()) {
                    return fail(
                        "dtb_${sourceEntry.index}.dts não encontrado"
                    )
                }

                val expected =
                    File(
                        expectedDir,
                        "dtb_${sourceEntry.index}.dtb"
                    )

                val compile =
                    ShellExecutor.root(
                        "'${dtc.path}' " +
                            "-I dts " +
                            "-O dtb " +
                            "-o '${expected.absolutePath}' " +
                            "'${dts.absolutePath}'"
                    )

                if (
                    !compile.success ||
                    !expected.exists()
                ) {
                    return fail(
                        "Falha recompilando DTS ${sourceEntry.index} para verificação:\n${compile.output}"
                    )
                }

                val expectedHash =
                    sha256(
                        expected
                    )

                val actualHash =
                    hashRegion(
                        verifyImage,
                        actualEntry.offset,
                        expected.length()
                    )

                val equal =
                    expectedHash ==
                        actualHash

                checks.add(
                    DtbCheck(
                        index =
                            sourceEntry.index,

                        equal =
                            equal,

                        message =
                            if (equal) {
                                "OK"
                            } else {
                                "Conteúdo diferente"
                            }
                    )
                )

                if (!equal) {
                    return Result(
                        false,
                        checks,
                        "Round-trip falhou no DTB ${sourceEntry.index}"
                    )
                }
            }

            return Result(
                true,
                checks,
                "${checks.size} DTB(s) verificados com sucesso"
            )

        } catch (
            e: Exception
        ) {

            return fail(
                e.message
                    ?: "Erro verificando partição DTB"
            )
        }
    }

    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file.inputStream()
            .buffered()
            .use { input ->

                val buffer =
                    ByteArray(
                        1024 * 1024
                    )

                while (true) {

                    val read =
                        input.read(
                            buffer
                        )

                    if (read <= 0)
                        break

                    digest.update(
                        buffer,
                        0,
                        read
                    )
                }
            }

        return digest.digest()
            .joinToString("") {
                "%02x".format(
                    it
                )
            }
    }

    private fun hashRegion(
        file: File,
        offset: Long,
        length: Long
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        RandomAccessFile(
            file,
            "r"
        ).use { input ->

            input.seek(
                offset
            )

            var remaining =
                length

            val buffer =
                ByteArray(
                    1024 * 1024
                )

            while (
                remaining > 0
            ) {

                val wanted =
                    minOf(
                        remaining,
                        buffer.size.toLong()
                    ).toInt()

                val read =
                    input.read(
                        buffer,
                        0,
                        wanted
                    )

                if (read <= 0) {
                    throw IllegalStateException(
                        "Fim inesperado verificando região DTB"
                    )
                }

                digest.update(
                    buffer,
                    0,
                    read
                )

                remaining -= read
            }
        }

        return digest.digest()
            .joinToString("") {
                "%02x".format(
                    it
                )
            }
    }

    private fun fail(
        message: String
    ): Result {

        return Result(
            false,
            emptyList(),
            message
        )
    }
}
