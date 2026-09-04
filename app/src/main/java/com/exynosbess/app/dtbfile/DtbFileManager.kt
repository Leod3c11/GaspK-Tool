package com.exynosbess.app.dtbfile

import android.content.Context
import android.net.Uri
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.security.MessageDigest

object DtbFileManager {

    data class Info(
        val valid: Boolean,
        val file: File?,
        val dts: File?,
        val size: Long,
        val declaredSize: Long?,
        val sha256: String?,
        val model: String?,
        val compatible: String?,
        val message: String
    )

    private data class FdtBlock(
        val offset: Int,
        val size: Int
    )

    private fun workspace(
        context: Context
    ): File {
        return File(
            context.filesDir,
            "offline_dtb"
        ).apply {
            mkdirs()
        }
    }

    fun importedFile(
        context: Context
    ): File {
        return File(
            workspace(context),
            "imported.dtb"
        )
    }

    fun dtsFile(
        context: Context
    ): File {
        return File(
            workspace(context),
            "imported.dts"
        )
    }

    fun dtsParts(
        context: Context
    ): List<File> {

        val dir =
            File(
                workspace(context),
                "fdt_blocks"
            )

        if (!dir.exists()) {
            return emptyList()
        }

        return dir.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.matches(
                        Regex("fdt_\\d+\\.dts")
                    ) &&
                    it.length() > 0L
            }
            ?.sortedBy {
                it.name
                    .removePrefix("fdt_")
                    .removeSuffix(".dts")
                    .toIntOrNull()
                    ?: Int.MAX_VALUE
            }
            ?: emptyList()
    }


    fun import(
        context: Context,
        uri: Uri
    ): Info {

        return try {

            val target =
                importedFile(context)

            val temp =
                File(
                    target.parentFile,
                    "imported.dtb.tmp"
                )

            if (temp.exists()) {
                temp.delete()
            }

            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    temp.outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }
                ?: return Info(
                    false,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    "Não foi possível abrir o arquivo."
                )

            val blocks =
                scanFdtBlocks(temp)

            if (blocks.isEmpty()) {

                temp.delete()

                return Info(
                    false,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    "O arquivo não possui nenhum FDT válido."
                )
            }

            if (target.exists()) {
                target.delete()
            }

            if (!temp.renameTo(target)) {

                temp.copyTo(
                    target,
                    overwrite = true
                )

                temp.delete()
            }

            decompile(context)

        } catch (e: Exception) {

            Info(
                false,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                e.message
                    ?: "Falha importando DTB."
            )
        }
    }

    fun inspect(
        context: Context
    ): Info {

        val dtb =
            importedFile(context)

        if (
            !dtb.exists() ||
            dtb.length() == 0L
        ) {
            return Info(
                false,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                "Nenhum DTB importado."
            )
        }

        if (scanFdtBlocks(dtb).isEmpty()) {

            return Info(
                false,
                dtb,
                null,
                dtb.length(),
                null,
                null,
                null,
                null,
                "Nenhum FDT válido encontrado."
            )
        }

        return decompile(context)
    }

    private fun decompile(
        context: Context
    ): Info {

        val dtb =
            importedFile(context)

        val dts =
            dtsFile(context)

        if (dts.exists()) {
            dts.delete()
        }

        val tool =
            ToolManager.installDtc(context)

        if (
            !tool.success ||
            tool.path == null
        ) {
            return baseInfo(
                dtb,
                null,
                "DTB válido, mas dtc não pôde ser preparado: ${tool.message}"
            )
        }

        val blocks =
            scanFdtBlocks(dtb)

        if (blocks.isEmpty()) {
            return baseInfo(
                dtb,
                null,
                "Nenhum FDT válido encontrado."
            )
        }

        val splitDir =
            File(
                workspace(context),
                "fdt_blocks"
            )

        if (splitDir.exists()) {
            splitDir.deleteRecursively()
        }

        splitDir.mkdirs()

        val source =
            try {
                dtb.readBytes()
            } catch (e: Exception) {
                return baseInfo(
                    dtb,
                    null,
                    e.message
                        ?: "Falha lendo DTB."
                )
            }

        val combined =
            StringBuilder()

        var successCount = 0

        blocks.forEachIndexed {
                index,
                block ->

            val part =
                File(
                    splitDir,
                    "fdt_${index + 1}.dtb"
                )

            try {

                part.outputStream()
                    .use { output ->
                        output.write(
                            source,
                            block.offset,
                            block.size
                        )
                    }

            } catch (_: Exception) {
                return@forEachIndexed
            }

            val partDts =
                File(
                    splitDir,
                    "fdt_${index + 1}.dts"
                )

            val result =
                execute(
                    listOf(
                        tool.path,
                        "-I",
                        "dtb",
                        "-O",
                        "dts",
                        "-o",
                        partDts.absolutePath,
                        part.absolutePath
                    )
                )

            if (
                result.first &&
                partDts.exists() &&
                partDts.length() > 0L
            ) {

                if (combined.isNotEmpty()) {
                    combined.append("\n\n")
                }

                combined.append(
                    "/* ========================================\n"
                )

                combined.append(
                    " * FDT ${index + 1} / ${blocks.size}\n"
                )

                combined.append(
                    " * Offset: 0x${block.offset.toString(16).uppercase()}\n"
                )

                combined.append(
                    " * Size: ${block.size} bytes\n"
                )

                combined.append(
                    " * ======================================== */\n\n"
                )

                combined.append(
                    partDts.readText()
                )

                successCount++
            }
        }

        if (
            successCount == 0 ||
            combined.isEmpty()
        ) {
            return baseInfo(
                dtb,
                null,
                "Foram encontrados ${blocks.size} FDT(s), mas nenhum pôde ser decompilado."
            )
        }

        dts.writeText(
            combined.toString()
        )

        return baseInfo(
            dtb,
            dts,
            if (blocks.size == 1) {
                "DTB lido e decompilado com sucesso."
            } else {
                "$successCount de ${blocks.size} Device Trees foram lidos e decompilados."
            }
        )
    }

    private fun scanFdtBlocks(
        file: File
    ): List<FdtBlock> {

        if (
            !file.exists() ||
            file.length() < 40L
        ) {
            return emptyList()
        }

        val data =
            try {
                file.readBytes()
            } catch (_: Exception) {
                return emptyList()
            }

        val result =
            mutableListOf<FdtBlock>()

        var offset = 0

        while (
            offset <=
            data.size - 8
        ) {

            if (
                data[offset] ==
                    0xD0.toByte() &&
                data[offset + 1] ==
                    0x0D.toByte() &&
                data[offset + 2] ==
                    0xFE.toByte() &&
                data[offset + 3] ==
                    0xED.toByte()
            ) {

                val totalSize =
                    readBe32(
                        data,
                        offset + 4
                    )

                if (
                    totalSize >= 40 &&
                    totalSize <=
                        data.size - offset
                ) {

                    result.add(
                        FdtBlock(
                            offset,
                            totalSize
                        )
                    )

                    /*
                     * Não precisamos percorrer byte por byte
                     * dentro de um FDT já validado.
                     */
                    offset += totalSize

                    continue
                }
            }

            offset++
        }

        return result
    }

    private fun readBe32(
        data: ByteArray,
        offset: Int
    ): Int {

        if (
            offset < 0 ||
            offset + 4 > data.size
        ) {
            return -1
        }

        val value =
            (
                (
                    data[offset]
                        .toLong() and 0xFF
                ) shl 24
            ) or
            (
                (
                    data[offset + 1]
                        .toLong() and 0xFF
                ) shl 16
            ) or
            (
                (
                    data[offset + 2]
                        .toLong() and 0xFF
                ) shl 8
            ) or
            (
                data[offset + 3]
                    .toLong() and 0xFF
            )

        if (
            value <= 0L ||
            value > Int.MAX_VALUE
        ) {
            return -1
        }

        return value.toInt()
    }

    private fun baseInfo(
        dtb: File,
        dts: File?,
        message: String
    ): Info {

        val text =
            try {
                dts?.readText()
                    ?: ""
            } catch (_: Exception) {
                ""
            }

        val blocks =
            scanFdtBlocks(dtb)

        return Info(
            valid =
                blocks.isNotEmpty(),

            file =
                dtb,

            dts =
                dts,

            size =
                dtb.length(),

            declaredSize =
                blocks.firstOrNull()
                    ?.size
                    ?.toLong(),

            sha256 =
                sha256(dtb),

            model =
                findQuotedProperty(
                    text,
                    "model"
                ),

            compatible =
                findQuotedProperty(
                    text,
                    "compatible"
                ),

            message =
                message
        )
    }

    private fun findQuotedProperty(
        text: String,
        property: String
    ): String? {

        if (text.isBlank()) {
            return null
        }

        val matches =
            Regex(
                """(?m)^\s*${Regex.escape(property)}\s*=\s*([^;]+);"""
            )
                .findAll(text)

        val values =
            matches.flatMap { match ->

                Regex(
                    "\"([^\"]+)\""
                )
                    .findAll(
                        match.groupValues[1]
                    )
                    .map {
                        it.groupValues[1]
                    }
            }
                .distinct()
                .toList()

        return values
            .takeIf {
                it.isNotEmpty()
            }
            ?.joinToString(", ")
    }

    private fun sha256(
        file: File
    ): String? {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            file.inputStream()
                .use { input ->

                    val buffer =
                        ByteArray(
                            64 * 1024
                        )

                    while (true) {

                        val read =
                            input.read(buffer)

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

            digest.digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        } catch (_: Exception) {
            null
        }
    }

    private fun execute(
        command: List<String>
    ): Pair<Boolean, String> {

        return try {

            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()

            val exitCode =
                process.waitFor()

            Pair(
                exitCode == 0,
                output
            )

        } catch (e: Exception) {

            Pair(
                false,
                e.message
                    ?: "Falha executando dtc."
            )
        }
    }
}
