package com.exynosbess.app.boot

object ShellExecutor {

    data class Result(
        val success: Boolean,
        val output: String,
        val exitCode: Int
    )

    fun root(command: String): Result {
        return try {
            val process = ProcessBuilder(
                "su",
                "-c",
                command
            )
                .redirectErrorStream(true)
                .start()

            val output = process
                .inputStream
                .bufferedReader()
                .readText()
                .trim()

            val exitCode = process.waitFor()

            Result(
                success = exitCode == 0,
                output = output,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Result(
                success = false,
                output = e.message ?: "Erro desconhecido",
                exitCode = -1
            )
        }
    }
}
