package com.exynosbess.app.boot

import android.content.Context
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

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

    private val supportedSources =
        setOf(
            "kernel",
            "extra",
            "dtb",
            "kernel_dtb"
        )

    fun verify(context: Context): VerifyResult {

        try {

            val workspace =
                File(
                    context.filesDir,
                    "workspace"
                )

            val modifiedBoot =
                File(
                    context.filesDir,
                    "boot_output/boot_modified.img"
                )

            if (
                !modifiedBoot.exists() ||
                modifiedBoot.length() == 0L
            ) {
                return fail(
                    "boot_modified.img não encontrado"
                )
            }

            if (!workspace.exists()) {
                return fail(
                    "Workspace original não encontrado"
                )
            }

            /*
             * Ler o mapa criado durante a extração original.
             *
             * Aqui sabemos exatamente:
             *
             * dtb_0 -> extra
             * dtb_1 -> dtb
             * dtb_2 -> kernel_dtb
             * etc.
             *
             * dtb_partition NÃO pertence ao boot.
             */
            val expectedEntries =
                DtbSourceMap
                    .bootEntries(
                        workspace.absolutePath
                    )
                    .filter {
                        it.sourceFile in
                            supportedSources
                    }

            if (expectedEntries.isEmpty()) {
                return fail(
                    "Nenhum DTB pertencente ao boot para verificar"
                )
            }

            val magiskboot =
                ToolManager.installMagiskBoot(
                    context
                )

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
                ToolManager.installDtc(
                    context
                )

            if (
                !dtc.success ||
                dtc.path == null
            ) {
                return fail(
                    "Falha preparando dtc:\n" +
                        dtc.message
                )
            }

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
             * Desempacotar o boot recém-gerado.
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

            /*
             * NÃO exigimos extra.
             *
             * O scanner encontrará qualquer combinação:
             *
             * kernel
             * extra
             * dtb
             * kernel_dtb
             */
            val scan =
                DtbScanner.scan(
                    verifyWorkspace.absolutePath
                )

            if (
                !scan.success ||
                scan.entries.isEmpty()
            ) {
                return fail(
                    "Nenhum DTB localizado após round-trip:\n" +
                        scan.message
                )
            }

            val actualEntries =
                scan.entries
                    .filter {
                        it.sourceFile in
                            supportedSources
                    }

            val expectedBySource =
                expectedEntries
                    .groupBy {
                        it.sourceFile
                    }

            val actualBySource =
                actualEntries
                    .groupBy {
                        it.sourceFile
                    }

            /*
             * As origens que possuíam DTB antes precisam
             * continuar existindo depois do repack.
             */
            expectedBySource.forEach {
                sourceName,
                expectedSourceEntries ->

                val actualSourceEntries =
                    actualBySource[
                        sourceName
                    ]
                        ?.sortedBy {
                            it.offset
                        }
                        ?: emptyList()

                if (
                    actualSourceEntries.size !=
                    expectedSourceEntries.size
                ) {
                    return fail(
                        "Quantidade de DTBs mudou em $sourceName.\n\n" +
                            "Antes: ${expectedSourceEntries.size}\n" +
                            "Depois: ${actualSourceEntries.size}"
                    )
                }
            }

            val extractedDir =
                File(
                    verifyWorkspace,
                    "roundtrip_dtbs"
                )

            if (extractedDir.exists()) {
                extractedDir.deleteRecursively()
            }

            extractedDir.mkdirs()

            val checks =
                mutableListOf<DtbCheck>()

            /*
             * Verificar cada componente separadamente.
             *
             * O índice global original é preservado através
             * do DtbSourceMap.
             */
            expectedBySource
                .toSortedMap()
                .forEach {
                    sourceName,
                    expectedUnsorted ->

                    val expected =
                        expectedUnsorted
                            .sortedBy {
                                it.offset
                            }

                    val actual =
                        actualBySource[
                            sourceName
                        ]
                            ?.sortedBy {
                                it.offset
                            }
                            ?: emptyList()

                    val sourceFile =
                        File(
                            verifyWorkspace,
                            sourceName
                        )

                    if (!sourceFile.exists()) {
                        return fail(
                            "Componente $sourceName desapareceu após repack"
                        )
                    }

                    expected.forEachIndexed {
                        localIndex,
                        expectedEntry ->

                        val actualEntry =
                            actual[
                                localIndex
                            ]

                        val globalIndex =
                            expectedEntry.index

                        val originalDts =
                            File(
                                workspace,
                                "dtbs/dtb_${globalIndex}.dts"
                            )

                        if (!originalDts.exists()) {
                            return fail(
                                "DTS original dtb_${globalIndex}.dts não encontrado"
                            )
                        }

                        /*
                         * Extrair o DTB realmente presente
                         * no boot reconstruído.
                         */
                        val extractedDtb =
                            File(
                                extractedDir,
                                "actual_${globalIndex}.dtb"
                            )

                        extractRegion(
                            sourceFile,
                            extractedDtb,
                            actualEntry.offset,
                            actualEntry.size
                        )

                        if (
                            !hasValidMagic(
                                extractedDtb
                            )
                        ) {
                            return fail(
                                "DTB $globalIndex de $sourceName " +
                                    "possui magic inválido"
                            )
                        }

                        /*
                         * Recompilar o DTS salvo pelo editor.
                         *
                         * Essa é a representação binária que
                         * esperamos encontrar no boot.
                         */
                        val expectedDtb =
                            File(
                                extractedDir,
                                "expected_${globalIndex}.dtb"
                            )

                        val compile =
                            ShellExecutor.root(
                                "'${dtc.path}' " +
                                    "-I dts " +
                                    "-O dtb " +
                                    "-o '${expectedDtb.absolutePath}' " +
                                    "'${originalDts.absolutePath}'"
                            )

                        if (
                            !compile.success ||
                            !expectedDtb.exists() ||
                            expectedDtb.length() == 0L
                        ) {
                            return fail(
                                "Falha recompilando dtb_${globalIndex}.dts:\n\n" +
                                    compile.output
                            )
                        }

                        /*
                         * Gerar também DTS round-trip para
                         * diagnóstico visual.
                         */
                        val roundTripDts =
                            File(
                                extractedDir,
                                "roundtrip_${globalIndex}.dts"
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
                                "Falha decompilando DTB $globalIndex:\n\n" +
                                    decompile.output
                            )
                        }

                        /*
                         * Comparação binária.
                         *
                         * É mais confiável que comparar
                         * formatação textual do DTS.
                         */
                        val expectedHash =
                            sha256(
                                expectedDtb
                            )

                        val actualHash =
                            sha256(
                                extractedDtb
                            )

                        val equal =
                            expectedHash ==
                                actualHash

                        checks.add(
                            DtbCheck(
                                index =
                                    globalIndex,

                                originalDts =
                                    originalDts.absolutePath,

                                roundTripDts =
                                    roundTripDts.absolutePath,

                                equal =
                                    equal,

                                message =
                                    if (equal) {
                                        "$sourceName: DTB preservado"
                                    } else {
                                        "$sourceName: DTB diferente após round-trip"
                                    }
                            )
                        )

                        if (!equal) {
                            return VerifyResult(
                                false,
                                checks,
                                "Round-trip falhou no DTB " +
                                    "$globalIndex de $sourceName"
                            )
                        }
                    }
                }

            return VerifyResult(
                true,
                checks,
                "Round-trip concluído com sucesso.\n" +
                    "${checks.size} DTB(s) verificado(s) em " +
                    "${expectedBySource.size} componente(s)."
            )

        } catch (
            e: Exception
        ) {

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

        if (
            offset < 0 ||
            size <= 0 ||
            offset + size >
            source.length()
        ) {
            throw IllegalStateException(
                "Região DTB inválida em ${source.name}"
            )
        }

        RandomAccessFile(
            source,
            "r"
        ).use { input ->

            output
                .outputStream()
                .buffered()
                .use { out ->

                    input.seek(
                        offset
                    )

                    var remaining =
                        size

                    val buffer =
                        ByteArray(
                            1024 * 1024
                        )

                    while (
                        remaining > 0
                    ) {

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

    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file
            .inputStream()
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

                    if (read <= 0) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        read
                    )
                }
            }

        return digest
            .digest()
            .joinToString(
                ""
            ) {
                "%02x".format(it)
            }
    }

    private fun fail(
        message: String
    ): VerifyResult {

        return VerifyResult(
            false,
            emptyList(),
            message
        )
    }
}
