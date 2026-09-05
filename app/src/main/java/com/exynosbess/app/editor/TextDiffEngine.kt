package com.exynosbess.app.editor

object TextDiffEngine {

    data class Result(
        val text: String,
        val added: Int,
        val removed: Int,
        val changed: Boolean
    )

    fun compare(
        original: String,
        modified: String
    ): Result {

        if (original == modified) {
            return Result(
                text = "",
                added = 0,
                removed = 0,
                changed = false
            )
        }

        val oldLines = original.lines()
        val newLines = modified.lines()

        var prefix = 0

        while (
            prefix < oldLines.size &&
            prefix < newLines.size &&
            oldLines[prefix] == newLines[prefix]
        ) {
            prefix++
        }

        var oldEnd = oldLines.lastIndex
        var newEnd = newLines.lastIndex

        while (
            oldEnd >= prefix &&
            newEnd >= prefix &&
            oldLines[oldEnd] == newLines[newEnd]
        ) {
            oldEnd--
            newEnd--
        }

        val builder = StringBuilder()

        appendContextBefore(
            builder,
            oldLines,
            prefix
        )

        var removed = 0

        for (i in prefix..oldEnd) {
            builder.append(
                "- "
            )
            builder.append(
                (i + 1).toString().padStart(5)
            )
            builder.append(
                " │ "
            )
            builder.append(
                oldLines[i]
            )
            builder.append('\n')
            removed++
        }

        var added = 0

        for (i in prefix..newEnd) {
            builder.append(
                "+ "
            )
            builder.append(
                (i + 1).toString().padStart(5)
            )
            builder.append(
                " │ "
            )
            builder.append(
                newLines[i]
            )
            builder.append('\n')
            added++
        }

        appendContextAfter(
            builder,
            newLines,
            newEnd
        )

        return Result(
            text = builder.toString(),
            added = added,
            removed = removed,
            changed = true
        )
    }

    private fun appendContextBefore(
        builder: StringBuilder,
        lines: List<String>,
        changedStart: Int
    ) {

        val start =
            (changedStart - CONTEXT_LINES)
                .coerceAtLeast(0)

        if (start > 0) {
            builder.append("⋯\n")
        }

        for (i in start until changedStart) {
            builder.append(
                "  ${i + 1} │ ${lines[i]}\n"
            )
        }
    }


    private fun appendContextAfter(
        builder: StringBuilder,
        lines: List<String>,
        changedEnd: Int
    ) {

        val start =
            changedEnd + 1

        val end =
            (start + CONTEXT_LINES)
                .coerceAtMost(lines.size)

        for (i in start until end) {
            builder.append(
                "  ${i + 1} │ ${lines[i]}\n"
            )
        }

        if (end < lines.size) {
            builder.append("⋯\n")
        }
    }


    private const val CONTEXT_LINES =
        4
}
