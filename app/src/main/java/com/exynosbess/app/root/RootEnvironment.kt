package com.exynosbess.app.root

import java.io.File

object RootEnvironment {

    enum class Provider(
        val displayName: String
    ) {

        MAGISK(
            "Magisk"
        ),

        KERNELSU(
            "KernelSU"
        ),

        APATCH(
            "APatch"
        ),

        GENERIC_SU(
            "SU genérico"
        ),

        NONE(
            "Sem root"
        )
    }


    data class Info(

        val available: Boolean,

        val provider: Provider,

        val version: String?,

        val suPath: String?,

        val uidOutput: String,

        val selinux: String?,

        val details: String
    )


    fun detect(): Info {

        val root =
            executeSu(
                "id"
            )

        val available =
            root.success &&
                root.output.contains(
                    "uid=0"
                )

        if (!available) {

            return Info(

                available = false,

                provider =
                    Provider.NONE,

                version = null,

                suPath =
                    findSuPath(),

                uidOutput =
                    root.output,

                selinux =
                    readSelinux(
                        false
                    ),

                details =
                    if (
                        root.output.isBlank()
                    ) {
                        "Acesso root não disponível."
                    } else {
                        root.output
                    }
            )
        }


        val provider =
            detectProvider()

        val version =
            detectVersion(
                provider
            )

        val suPath =
            findSuPath()

        val selinux =
            readSelinux(
                true
            )


        return Info(

            available = true,

            provider =
                provider,

            version =
                version,

            suPath =
                suPath,

            uidOutput =
                root.output,

            selinux =
                selinux,

            details =
                buildString {

                    append(
                        "Root disponível"
                    )

                    append(
                        "\nProvider: ${provider.displayName}"
                    )

                    version
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {

                            append(
                                "\nVersão: $it"
                            )
                        }

                    suPath
                        ?.let {

                            append(
                                "\nSU: $it"
                            )
                        }

                    selinux
                        ?.let {

                            append(
                                "\nSELinux: $it"
                            )
                        }
                }
        )
    }


    private fun detectProvider():
        Provider {

        /*
         * Ordem importante:
         *
         * APatch e KernelSU também oferecem su.
         * Portanto não podemos identificar simplesmente
         * pela existência do binário.
         */

        if (
            rootExists(
                "/data/adb/ap"
            ) ||
            rootExists(
                "/data/adb/ap/bin/apd"
            ) ||
            rootCommandExists(
                "apd"
            )
        ) {

            return Provider.APATCH
        }


        if (
            rootExists(
                "/data/adb/ksu"
            ) ||
            rootExists(
                "/data/adb/ksu/bin/ksud"
            ) ||
            rootCommandExists(
                "ksud"
            )
        ) {

            return Provider.KERNELSU
        }


        if (
            rootExists(
                "/data/adb/magisk"
            ) ||
            rootCommandExists(
                "magisk"
            )
        ) {

            return Provider.MAGISK
        }


        return Provider.GENERIC_SU
    }


    private fun detectVersion(
        provider: Provider
    ): String? {

        val commands =
            when (
                provider
            ) {

                Provider.MAGISK ->
                    listOf(
                        "magisk -v",
                        "/data/adb/magisk/magisk -v"
                    )

                Provider.KERNELSU ->
                    listOf(
                        "ksud -V",
                        "ksud --version",
                        "/data/adb/ksu/bin/ksud -V",
                        "/data/adb/ksu/bin/ksud --version"
                    )

                Provider.APATCH ->
                    listOf(
                        "apd -V",
                        "apd --version",
                        "/data/adb/ap/bin/apd -V",
                        "/data/adb/ap/bin/apd --version"
                    )

                Provider.GENERIC_SU ->
                    listOf(
                        "su -v",
                        "su --version"
                    )

                Provider.NONE ->
                    emptyList()
            }


        commands.forEach {
            command ->

            val result =
                executeSu(
                    "$command 2>/dev/null"
                )

            if (
                result.success &&
                result.output.isNotBlank()
            ) {

                return result.output
                    .lineSequence()
                    .firstOrNull()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
            }
        }

        return null
    }


    private fun rootExists(
        path: String
    ): Boolean {

        val result =
            executeSu(
                "[ -e '${shellQuote(path)}' ]"
            )

        return result.success
    }


    private fun rootCommandExists(
        command: String
    ): Boolean {

        val result =
            executeSu(
                "command -v ${shellQuote(command)} >/dev/null 2>&1"
            )

        return result.success
    }


    private fun findSuPath():
        String? {

        val candidates =
            listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/debug_ramdisk/su"
            )

        candidates.forEach {
            path ->

            if (
                File(
                    path
                ).exists()
            ) {
                return path
            }
        }


        return try {

            val process =
                ProcessBuilder(
                    "sh",
                    "-c",
                    "command -v su 2>/dev/null"
                )
                    .redirectErrorStream(
                        true
                    )
                    .start()

            val output =
                process
                    .inputStream
                    .bufferedReader()
                    .readText()
                    .trim()

            process.waitFor()

            output
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        } catch (_: Exception) {

            null
        }
    }


    private fun readSelinux(
        rootAvailable: Boolean
    ): String? {

        val command =
            "getenforce 2>/dev/null"

        val result =
            if (rootAvailable) {

                executeSu(
                    command
                )

            } else {

                executeShell(
                    command
                )
            }

        if (
            !result.success ||
            result.output.isBlank()
        ) {
            return null
        }

        return result.output
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }


    private data class CommandResult(

        val success: Boolean,

        val output: String,

        val exitCode: Int
    )


    private fun executeSu(
        command: String
    ): CommandResult {

        return try {

            val process =
                ProcessBuilder(
                    "su",
                    "-c",
                    command
                )
                    .redirectErrorStream(
                        true
                    )
                    .start()

            val output =
                process
                    .inputStream
                    .bufferedReader()
                    .readText()
                    .trim()

            val exitCode =
                process.waitFor()


            CommandResult(

                success =
                    exitCode == 0,

                output =
                    output,

                exitCode =
                    exitCode
            )

        } catch (
            e: Exception
        ) {

            CommandResult(

                success =
                    false,

                output =
                    e.message
                        ?: "Erro desconhecido",

                exitCode =
                    -1
            )
        }
    }


    private fun executeShell(
        command: String
    ): CommandResult {

        return try {

            val process =
                ProcessBuilder(
                    "sh",
                    "-c",
                    command
                )
                    .redirectErrorStream(
                        true
                    )
                    .start()

            val output =
                process
                    .inputStream
                    .bufferedReader()
                    .readText()
                    .trim()

            val exitCode =
                process.waitFor()


            CommandResult(

                success =
                    exitCode == 0,

                output =
                    output,

                exitCode =
                    exitCode
            )

        } catch (
            e: Exception
        ) {

            CommandResult(

                success =
                    false,

                output =
                    e.message
                        ?: "Erro desconhecido",

                exitCode =
                    -1
            )
        }
    }


    private fun shellQuote(
        value: String
    ): String {

        return value.replace(
            "'",
            "'\\''"
        )
    }
}
