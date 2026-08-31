package com.exynosbess.app.ai

object GaspAiLocalAnalyzer {

    fun analyze(
        action: AiAction,
        context: GaspAiContext
    ): AiSuggestion {

        val properties =
            Regex(
                """(?m)^\s*([A-Za-z0-9,_@+\-]+)\s*="""
            )
                .findAll(
                    context.nodeContent
                )
                .map {
                    it.groupValues[1]
                }
                .distinct()
                .toList()

        val clockProperties =
            properties.filter {
                it.contains(
                    "clock",
                    ignoreCase = true
                ) ||
                    it.contains(
                        "freq",
                        ignoreCase = true
                    ) ||
                    it.contains(
                        "dvfs",
                        ignoreCase = true
                    )
            }

        val voltageProperties =
            properties.filter {
                it.contains(
                    "volt",
                    ignoreCase = true
                )
            }

        val summary =
            buildString {

                append(
                    "DTB ${context.dtbIndex}"
                )

                append(
                    "\nNó: ${context.nodeName}"
                )

                append(
                    "\nPropriedades detectadas: ${properties.size}"
                )

                if (clockProperties.isNotEmpty()) {

                    append(
                        "\n\nRelacionadas a clock/DVFS:\n"
                    )

                    append(
                        clockProperties
                            .take(12)
                            .joinToString("\n") {
                                "• $it"
                            }
                    )
                }

                if (voltageProperties.isNotEmpty()) {

                    append(
                        "\n\nRelacionadas a tensão:\n"
                    )

                    append(
                        voltageProperties
                            .take(12)
                            .joinToString("\n") {
                                "• $it"
                            }
                    )
                }
            }

        return when (action) {

            AiAction.EXPLAIN ->

                AiSuggestion(
                    title =
                        "Análise da configuração GPU",
                    explanation =
                        "$summary\n\n" +
                        "A Gasp AI online poderá interpretar " +
                        "cada propriedade e explicar como ela " +
                        "participa do funcionamento da GPU.",
                    risk =
                        AiRiskLevel.LOW
                )

            AiAction.PERFORMANCE ->

                AiSuggestion(
                    title =
                        "Análise de desempenho",
                    explanation =
                        "$summary\n\n" +
                        "Para recomendar alterações de desempenho " +
                        "com segurança, esta ação será processada " +
                        "pelo modelo de IA.",
                    risk =
                        AiRiskLevel.MEDIUM
                )

            AiAction.EFFICIENCY ->

                AiSuggestion(
                    title =
                        "Análise de eficiência",
                    explanation =
                        "$summary\n\n" +
                        "A análise online poderá procurar níveis " +
                        "DVFS e relações entre frequência e tensão.",
                    risk =
                        AiRiskLevel.MEDIUM
                )

            AiAction.FIND_PROBLEMS ->

                AiSuggestion(
                    title =
                        "Procurar inconsistências",
                    explanation =
                        "$summary\n\n" +
                        "A análise avançada ainda será conectada " +
                        "ao provedor da Gasp AI.",
                    risk =
                        AiRiskLevel.LOW
                )

            AiAction.SUGGEST_GPU_TUNING ->

                AiSuggestion(
                    title =
                        "Sugestão de ajuste GPU",
                    explanation =
                        "$summary\n\n" +
                        "Nenhuma alteração será aplicada " +
                        "automaticamente. A futura resposta da IA " +
                        "será apresentada como uma proposta antes " +
                        "de qualquer modificação.",
                    risk =
                        AiRiskLevel.HIGH
                )

            AiAction.CUSTOM ->

                AiSuggestion(
                    title =
                        "Pergunta para Gasp AI",
                    explanation =
                        "$summary\n\n" +
                        "Pergunta:\n" +
                        (
                            context.userQuestion
                                ?: "Nenhuma pergunta informada."
                            ) +
                        "\n\nO backend de IA ainda não está conectado.",
                    risk =
                        AiRiskLevel.LOW
                )
        }
    }
}
