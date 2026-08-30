package com.exynosbess.app.startup

import java.io.File

object DtbCatalogDetector {

    data class DtbInfo(
        val index: Int,
        val fileName: String,
        val filePath: String,
        val model: String,
        val compatible: String,
        val soc: String
    )

    fun detectAll(
        workspacePath: String
    ): List<DtbInfo> {

        val dtbDir =
            File(
                workspacePath,
                "dtbs"
            )

        if (!dtbDir.exists()) {
            return emptyList()
        }

        return dtbDir
            .listFiles()
            ?.filter {
                it.isFile &&
                    it.name.matches(
                        Regex("""dtb_\d+\.dts""")
                    )
            }
            ?.mapNotNull { file ->

                val index =
                    Regex(
                        """dtb_(\d+)\.dts"""
                    )
                        .find(file.name)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: return@mapNotNull null

                val text =
                    try {
                        file.readText()
                    } catch (_: Exception) {
                        ""
                    }

                DtbInfo(
                    index = index,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    model = detectModel(text),
                    compatible = detectRootCompatible(text),
                    soc = detectSoc(text)
                )
            }
            ?.sortedBy {
                it.index
            }
            ?: emptyList()
    }

    private fun detectModel(
        text: String
    ): String {

        return Regex(
            """(?m)^\s*model\s*=\s*"([^"]+)";"""
        )
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Modelo não informado"
    }

    private fun detectRootCompatible(
        text: String
    ): String {

        val rootStart =
            text.indexOf("/ {")

        val searchArea =
            if (rootStart >= 0) {

                text.substring(
                    rootStart,
                    minOf(
                        text.length,
                        rootStart + 8000
                    )
                )

            } else {
                text.take(8000)
            }

        val match =
            Regex(
                """(?m)^\s*compatible\s*=\s*([^;]+);"""
            )
                .find(searchArea)
                ?.groupValues
                ?.getOrNull(1)
                ?: return "Compatible não informado"

        val values =
            Regex(
                """"([^"]+)""""
            )
                .findAll(match)
                .map {
                    it.groupValues[1]
                }
                .toList()

        return if (values.isNotEmpty()) {
            values.joinToString(", ")
        } else {
            "Compatible não informado"
        }
    }

    private fun detectSoc(
        text: String
    ): String {

        val lower =
            text.lowercase()

        val knownSocs =
            listOf(
                "exynos8895",
                "exynos9810",
                "exynos9820",
                "exynos9825",
                "exynos990",
                "exynos2100",
                "exynos2200",
                "exynos2400"
            )

        val detected =
            knownSocs.firstOrNull {
                lower.contains(it)
            }

        if (detected != null) {
            return detected.replaceFirstChar {
                it.uppercase()
            }
        }

        val generic =
            Regex(
                """exynos[0-9a-z]+""",
                RegexOption.IGNORE_CASE
            )
                .find(text)
                ?.value

        return generic
            ?.replaceFirstChar {
                it.uppercase()
            }
            ?: "SoC não identificado"
    }
}
