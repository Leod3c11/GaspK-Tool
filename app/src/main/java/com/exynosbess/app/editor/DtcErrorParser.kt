package com.exynosbess.app.editor

object DtcErrorParser {

    data class Location(
        val line: Int,
        val column: Int
    )

    fun findLocation(
        output: String
    ): Location? {

        if (
            output.isBlank()
        ) {
            return null
        }

        val patterns =
            listOf(
                Regex(
                    """:(\d+)\.(\d+)(?:-\d+(?:\.\d+)?)?(?::|\s)"""
                ),
                Regex(
                    """:(\d+)(?::|\s)"""
                )
            )

        patterns.forEach { pattern ->

            val match =
                pattern.find(
                    output
                )
                    ?: return@forEach

            val line =
                match.groupValues
                    .getOrNull(1)
                    ?.toIntOrNull()
                    ?: return@forEach

            val column =
                match.groupValues
                    .getOrNull(2)
                    ?.toIntOrNull()
                    ?: 1

            if (
                line > 0
            ) {

                return Location(
                    line =
                        line,
                    column =
                        column.coerceAtLeast(1)
                )
            }
        }

        return null
    }
}
