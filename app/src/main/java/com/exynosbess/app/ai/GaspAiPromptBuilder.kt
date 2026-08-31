package com.exynosbess.app.ai

object GaspAiPromptBuilder {

    fun build(
        action: AiAction,
        context: GaspAiContext
    ): String {

        val instruction =
            when (action) {

                AiAction.EXPLAIN ->
                    "Explique esta configuração."

                AiAction.PERFORMANCE ->
                    "Analise o impacto no desempenho."

                AiAction.EFFICIENCY ->
                    "Analise consumo e eficiência."

                AiAction.FIND_PROBLEMS ->
                    "Procure inconsistências, erros e configurações suspeitas."

                AiAction.SUGGEST_GPU_TUNING ->
                    "Sugira ajustes seguros para a GPU, sem executar nada."

                AiAction.CUSTOM ->
                    context.userQuestion
                        ?: "Analise esta configuração."
            }

        val node =
            context.nodeContent.take(
                3000
            )

        val system =
            """
Você é Gasp AI, assistente técnico do GaspKernel Editor.

Especialidades:
- Linux kernel
- Android kernel
- Device Tree DTS/DTB
- DVFS
- GPU
- clocks
- voltage
- thermal

Regras:
- responda em português;
- seja técnico e objetivo;
- não execute comandos;
- não faça flash;
- não use root;
- não use dd;
- não reinicie o dispositivo;
- não invente propriedades;
- diferencie fatos de sugestões.
""".trimIndent()

        val user =
            """
Ação:
$instruction

Arquivo:
${context.dtsFileName}

DTB:
${context.dtbIndex}

Nó:
${context.nodeName}

Conteúdo DTS:
$node
""".trimIndent()

        return buildString {

            append(
                "<|im_start|>system\n"
            )

            append(system)

            append(
                "\n<|im_end|>\n"
            )

            append(
                "<|im_start|>user\n"
            )

            append(user)

            append(
                "\n<|im_end|>\n"
            )

            append(
                "<|im_start|>assistant\n"
            )
        }
    }
}
