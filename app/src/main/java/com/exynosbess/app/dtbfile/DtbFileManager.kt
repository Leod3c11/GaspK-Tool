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


    fun import(
        context: Context,
        uri: Uri
    ): Info {

        return try {

            val target =
                importedFile(
                    context
                )

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

                            input.copyTo(
                                output
                            )
                        }

                } ?: return Info(
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

            if (!validateMagic(temp)) {

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
                    "O arquivo não possui o magic FDT 0xD00DFEED."
                )
            }

            val declared =
                readDeclaredSize(
                    temp
                )

            if (
                declared == null ||
                declared < 40L ||
                declared > temp.length()
            ) {

                temp.delete()

                return Info(
                    false,
                    null,
                    null,
                    0,
                    declared,
                    null,
                    null,
                    null,
                    "Header DTB inválido ou incompleto."
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

            decompile(
                context
            )

        } catch (
            e: Exception
        ) {

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
            importedFile(
                context
            )

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

        if (!validateMagic(dtb)) {

            return Info(
                false,
                dtb,
                null,
                dtb.length(),
                null,
                null,
                null,
                null,
                "DTB inválido."
            )
        }

        return decompile(
            context
        )
    }


    private fun decompile(
        context: Context
    ): Info {

        val dtb =
            importedFile(
                context
            )

        val dts =
            dtsFile(
                context
            )

        if (dts.exists()) {
            dts.delete()
        }

        val tool =
            ToolManager.installDtc(
                context
            )

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

        val result =
            execute(
                listOf(
                    tool.path,
                    "-I",
                    "dtb",
                    "-O",
                    "dts",
                    "-o",
                    dts.absolutePath,
                    dtb.absolutePath
                )
            )

        if (
            !result.first ||
            !dts.exists() ||
            dts.length() == 0L
        ) {

            return baseInfo(
                dtb,
                null,
                "DTB válido, mas não foi possível decompilar.\n${result.second}"
            )
        }

        return baseInfo(
            dtb,
            dts,
            "DTB lido e decompilado com sucesso."
        )
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

        return Info(
            valid =
                validateMagic(dtb),

            file =
                dtb,

            dts =
                dts,

            size =
                dtb.length(),

            declaredSize =
                readDeclaredSize(
                    dtb
                ),

            sha256 =
                sha256(
                    dtb
                ),

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

        val match =
            Regex(
                """(?m)^\s*${Regex.escape(property)}\s*=\s*([^;]+);"""
            )
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?: return null

        val values =
            Regex(
                "\"([^\"]+)\""
            )
                .findAll(match)
                .map {
                    it.groupValues[1]
                }
                .toList()

        return values
            .takeIf {
                it.isNotEmpty()
            }
            ?.joinToString(", ")
    }


    private fun validateMagic(
        file: File
    ): Boolean {

        if (
            !file.exists() ||
            file.length() < 8L
        ) {
            return false
        }

        return try {

            file.inputStream()
                .use { input ->

                    val bytes =
                        ByteArray(4)

                    if (
                        input.read(bytes) !=
                        4
                    ) {
                        return false
                    }

                    bytes[0].toInt()
                        .and(0xFF) == 0xD0 &&
                    bytes[1].toInt()
                        .and(0xFF) == 0x0D &&
                    bytes[2].toInt()
                        .and(0xFF) == 0xFE &&
                    bytes[3].toInt()
                        .and(0xFF) == 0xED
                }

        } catch (_: Exception) {

            false
        }
    }


    private fun readDeclaredSize(
        file: File
    ): Long? {

        if (
            !file.exists() ||
            file.length() < 8L
        ) {
            return null
        }

        return try {

            file.inputStream()
                .use { input ->

                    val header =
                        ByteArray(8)

                    if (
                        input.read(header) !=
                        8
                    ) {
                        return null
                    }

                    (
                        (
                            header[4].toLong()
                                .and(0xFF)
                        ) shl 24
                    ) or
                    (
                        (
                            header[5].toLong()
                                .and(0xFF)
                        ) shl 16
                    ) or
                    (
                        (
                            header[6].toLong()
                                .and(0xFF)
                        ) shl 8
                    ) or
                    (
                        header[7].toLong()
                            .and(0xFF)
                    )
                }

        } catch (_: Exception) {

            null
        }
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
                ProcessBuilder(
                    command
                )
                    .redirectErrorStream(
                        true
                    )
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

        } catch (
            e: Exception
        ) {

            Pair(
                false,
                e.message
                    ?: "Falha executando dtc."
            )
        }
    }
}
