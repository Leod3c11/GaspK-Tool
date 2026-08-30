package com.exynosbess.app.startup

import com.exynosbess.app.boot.ShellExecutor
import java.io.File

object DeviceTreeInfoDetector {

    data class Info(
        val model: String,
        val compatible: String,
        val gpu: String,
        val gpuCompatible: String
    )

    fun detect(
        workspacePath: String
    ): Info {

        val dtsDir =
            File(
                workspacePath,
                "dtbs"
            )

        val dtsFiles =
            dtsDir.listFiles()
                ?.filter {
                    it.isFile &&
                    it.name.endsWith(".dts")
                }
                ?.sortedBy {
                    it.name
                }
                ?: emptyList()

        if (dtsFiles.isEmpty()) {
            return Info(
                model = "Desconhecido",
                compatible = "Desconhecido",
                gpu = detectSystemGpu(),
                gpuCompatible = "Desconhecido"
            )
        }

        val contents =
            dtsFiles.map {
                it.readText()
            }

        val main =
            contents.first()

        val model =
            Regex(
                """(?m)^\s*model\s*=\s*"([^"]+)";"""
            )
                .find(main)
                ?.groupValues
                ?.get(1)
                ?: "Não informado no DTB"

        val compatible =
            detectRootCompatible(
                main
            )

        val gpuCompatible =
            detectGpuCompatible(
                contents
            )

        var gpu =
            detectSystemGpu()

        if (
            gpu == "Não identificado" ||
            gpu.isBlank()
        ) {

            gpu =
                inferGpu(
                    compatible,
                    gpuCompatible
                )
        }

        return Info(
            model = model,
            compatible = compatible,
            gpu = gpu,
            gpuCompatible = gpuCompatible
        )
    }

    private fun detectRootCompatible(
        text: String
    ): String {

        val rootStart =
            text.indexOf("/ {")

        if (rootStart == -1) {
            return "Não identificado"
        }

        val searchArea =
            text.substring(
                rootStart,
                minOf(
                    text.length,
                    rootStart + 5000
                )
            )

        return Regex(
            """compatible\s*=\s*"([^"]+)"""
        )
            .find(searchArea)
            ?.groupValues
            ?.get(1)
            ?: "Não identificado"
    }

    private fun detectGpuCompatible(
        texts: List<String>
    ): String {

        for (text in texts) {

            val lines =
                text.lines()

            for (i in lines.indices) {

                val line =
                    lines[i]
                        .trim()
                        .lowercase()

                val looksLikeGpu =
                    line.contains("gpu@") ||
                    line.startsWith("gpu {") ||
                    line.contains("mali@") ||
                    line.startsWith("mali {")

                if (!looksLikeGpu) {
                    continue
                }

                val end =
                    minOf(
                        lines.size,
                        i + 120
                    )

                for (j in i until end) {

                    val compatible =
                        Regex(
                            """compatible\s*=\s*"([^"]+)"""
                        )
                            .find(lines[j])
                            ?.groupValues
                            ?.get(1)

                    if (!compatible.isNullOrBlank()) {
                        return compatible
                    }

                    if (
                        j > i &&
                        lines[j]
                            .trim() == "};"
                    ) {
                        break
                    }
                }
            }
        }

        return "Não identificado"
    }

    private fun detectSystemGpu(): String {

        val result =
            ShellExecutor.root(
                "dumpsys SurfaceFlinger 2>/dev/null | " +
                    "grep -i 'GLES' | head -n 1"
            )

        if (
            !result.success ||
            result.output.isBlank()
        ) {
            return "Não identificado"
        }

        val line =
            result.output
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?: return "Não identificado"

        return line
            .replace(
                Regex(
                    """(?i)^.*GLES[:\s]*"""
                ),
                ""
            )
            .trim()
            .ifBlank {
                line
            }
    }

    private fun inferGpu(
        socCompatible: String,
        gpuCompatible: String
    ): String {

        val combined =
            "$socCompatible $gpuCompatible"
                .lowercase()

        if (
            combined.contains(
                "exynos8895"
            )
        ) {
            return "ARM Mali-G71 MP20"
        }

        if (
            combined.contains("mali")
        ) {
            return gpuCompatible
        }

        return "Não identificado"
    }
}
