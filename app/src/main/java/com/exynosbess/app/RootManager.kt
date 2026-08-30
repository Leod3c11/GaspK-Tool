package com.exynosbess.app

object RootManager {

    data class RootResult(
        val granted: Boolean,
        val output: String
    )

    fun checkRoot(): RootResult {
        return try {

            val process = ProcessBuilder(
                "su",
                "-c",
                "id"
            )
                .redirectErrorStream(true)
                .start()

            val output = process
                .inputStream
                .bufferedReader()
                .readText()
                .trim()

            val exitCode = process.waitFor()

            val rootGranted =
                exitCode == 0 &&
                output.contains("uid=0")

            RootResult(
                granted = rootGranted,
                output = output
            )

        } catch (e: Exception) {

            RootResult(
                granted = false,
                output = e.message ?: "Erro desconhecido"
            )
        }
    }
}
