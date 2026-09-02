package com.exynosbess.app.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GaspLog {

    enum class Level {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        EDIT,
        EXPORT,
        FLASH
    }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val message: String
    )

    private const val FILE_NAME =
        "gaspkernel.log"

    private const val MAX_SIZE =
        1024L * 1024L

    @Synchronized
    fun write(
        context: Context,
        level: Level,
        message: String
    ) {
        try {
            val file =
                logFile(context)

            rotateIfNeeded(file)

            val safeMessage =
                message
                    .replace("\r", " ")
                    .trim()

            val line =
                "${System.currentTimeMillis()}|" +
                    "${level.name}|" +
                    safeMessage +
                    "\n"

            file.appendText(line)

        } catch (_: Exception) {
            // Logging nunca deve derrubar o app.
        }
    }

    fun info(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.INFO,
            message
        )

    fun success(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.SUCCESS,
            message
        )

    fun warning(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.WARNING,
            message
        )

    fun error(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.ERROR,
            message
        )

    fun edit(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.EDIT,
            message
        )

    fun export(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.EXPORT,
            message
        )

    fun flash(
        context: Context,
        message: String
    ) =
        write(
            context,
            Level.FLASH,
            message
        )

    @Synchronized
    fun read(
        context: Context
    ): List<Entry> {

        val file =
            logFile(context)

        if (!file.exists()) {
            return emptyList()
        }

        return file
            .readLines()
            .mapNotNull { line ->
                parse(line)
            }
            .sortedByDescending {
                it.timestamp
            }
    }

    @Synchronized
    fun clear(
        context: Context
    ) {
        val file =
            logFile(context)

        if (file.exists()) {
            file.writeText("")
        }
    }

    fun formatted(
        context: Context
    ): String {

        val formatter =
            SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
            )

        val entries =
            read(context)

        if (entries.isEmpty()) {
            return "Nenhum registro disponível."
        }

        return entries.joinToString(
            separator = "\n\n"
        ) { entry ->

            val time =
                formatter.format(
                    Date(entry.timestamp)
                )

            "$time  ${entry.level.name}\n" +
                entry.message
        }
    }

    fun file(
        context: Context
    ): File =
        logFile(context)

    private fun parse(
        line: String
    ): Entry? {

        val parts =
            line.split(
                "|",
                limit = 3
            )

        if (parts.size != 3) {
            return null
        }

        val timestamp =
            parts[0].toLongOrNull()
                ?: return null

        val level =
            try {
                Level.valueOf(
                    parts[1]
                )
            } catch (_: Exception) {
                return null
            }

        return Entry(
            timestamp = timestamp,
            level = level,
            message = parts[2]
        )
    }

    private fun logFile(
        context: Context
    ): File {

        val directory =
            File(
                context.filesDir,
                "logs"
            )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        return File(
            directory,
            FILE_NAME
        )
    }

    private fun rotateIfNeeded(
        file: File
    ) {
        if (
            file.exists() &&
            file.length() >= MAX_SIZE
        ) {
            val old =
                File(
                    file.parentFile,
                    "gaspkernel.old.log"
                )

            if (old.exists()) {
                old.delete()
            }

            file.renameTo(old)
        }
    }
}
