package com.exynosbess.app.ai

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object GaspAiModelDownloader {

    data class Result(
        val success: Boolean,
        val message: String
    )

    fun download(
        context: Context,
        progress: (Int) -> Unit
    ): Result {

        val target =
            GaspAiModelManager.modelFile(
                context
            )

        val temporary =
            GaspAiModelManager.temporaryFile(
                context
            )

        return try {

            temporary.delete()

            val connection =
                URL(
                    GaspAiModelManager.MODEL_URL
                ).openConnection()
                    as HttpURLConnection

            connection.instanceFollowRedirects =
                true

            connection.connectTimeout =
                30_000

            connection.readTimeout =
                60_000

            connection.setRequestProperty(
                "User-Agent",
                "GaspKernel-Editor"
            )

            connection.connect()

            if (
                connection.responseCode !in 200..299
            ) {

                connection.disconnect()

                return Result(
                    false,
                    "HTTP ${connection.responseCode}"
                )
            }

            val total =
                connection.contentLengthLong

            connection.inputStream.use {
                input ->

                temporary.outputStream()
                    .buffered(
                        1024 * 1024
                    )
                    .use {
                        output ->

                        val buffer =
                            ByteArray(
                                1024 * 1024
                            )

                        var downloaded =
                            0L

                        var lastPercent =
                            -1

                        while (true) {

                            val read =
                                input.read(
                                    buffer
                                )

                            if (read <= 0) {
                                break
                            }

                            output.write(
                                buffer,
                                0,
                                read
                            )

                            downloaded +=
                                read

                            if (total > 0L) {

                                val percent =
                                    (
                                        downloaded *
                                            100L /
                                            total
                                        ).toInt()

                                if (
                                    percent !=
                                    lastPercent
                                ) {

                                    lastPercent =
                                        percent

                                    progress(
                                        percent
                                    )
                                }
                            }
                        }
                    }
            }

            connection.disconnect()

            if (
                temporary.length() <
                400L * 1024L * 1024L
            ) {

                temporary.delete()

                return Result(
                    false,
                    "Modelo baixado está incompleto."
                )
            }

            val hash =
                sha256(
                    temporary
                )

            if (
                !hash.equals(
                    GaspAiModelManager.MODEL_SHA256,
                    ignoreCase = true
                )
            ) {

                temporary.delete()

                return Result(
                    false,
                    "SHA-256 do modelo não confere."
                )
            }

            target.delete()

            if (
                !temporary.renameTo(
                    target
                )
            ) {

                temporary.copyTo(
                    target,
                    overwrite = true
                )

                temporary.delete()
            }

            Result(
                true,
                "Modelo instalado com sucesso."
            )

        } catch (
            e: Exception
        ) {

            Result(
                false,
                e.message
                    ?: e.javaClass.simpleName
            )
        }
    }

    private fun sha256(
        file: java.io.File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file.inputStream()
            .buffered(
                1024 * 1024
            )
            .use {
                input ->

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
            .joinToString("") {
                "%02x".format(it)
            }
    }
}
