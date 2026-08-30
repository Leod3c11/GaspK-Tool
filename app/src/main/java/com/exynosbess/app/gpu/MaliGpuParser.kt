package com.exynosbess.app.gpu

object MaliGpuParser {

    fun isSupported(dts: String): Boolean {
        return Regex(
            """(?i)\bmali@[0-9a-f]+\s*\{"""
        ).containsMatchIn(dts)
    }

    fun parse(dts: String): GpuProfile? {

        val node = extractMaliNode(dts)
            ?: return null

        val maxClock =
            readScalar(node.text, "gpu_max_clock")
                ?: 0L

        val maxClockLimit =
            readScalar(node.text, "gpu_max_clock_limit")
                ?: maxClock

        val minClock =
            readScalar(node.text, "gpu_min_clock")
                ?: 0L

        val startClock =
            readScalar(node.text, "gpu_dvfs_start_clock")
                ?: minClock

        val bootClock =
            readScalar(node.text, "gpu_dvfs_bl_config_clock")
                ?: startClock

        val defaultVoltage =
            readScalar(node.text, "gpu_default_voltage")
                ?: 0L

        val voltageMargin =
            readScalar(node.text, "gpu_voltage_margin")
                ?: 0L

        val tableSize =
            readArray(node.text, "gpu_dvfs_table_size")

        val rows =
            tableSize.getOrNull(0)
                ?.toInt()
                ?: 0

        val columns =
            tableSize.getOrNull(1)
                ?.toInt()
                ?: 0

        val table =
            readArray(node.text, "gpu_dvfs_table")

        val levels =
            mutableListOf<GpuDvfsLevel>()

        if (
            rows > 0 &&
            columns > 0 &&
            table.size >= rows * columns
        ) {
            for (row in 0 until rows) {

                val start =
                    row * columns

                val values =
                    table.subList(
                        start,
                        start + columns
                    )

                levels +=
                    GpuDvfsLevel(
                        frequencyKhz =
                            values.first(),
                        extraValues =
                            values.drop(1)
                                .toMutableList()
                    )
            }
        }

        val throttling =
            readArray(
                node.text,
                "gpu_temp_throttling"
            ).toMutableList()

        return GpuProfile(
            vendor = "Mali",
            nodeName = node.name,

            maxClockKhz = maxClock,
            maxClockLimitKhz = maxClockLimit,
            minClockKhz = minClock,
            startClockKhz = startClock,
            bootClockKhz = bootClock,

            defaultVoltageUv = defaultVoltage,
            voltageMarginUv = voltageMargin,

            dvfsLevels = levels,
            throttlingKhz = throttling
        )
    }


    fun apply(
        dts: String,
        profile: GpuProfile
    ): String {

        val node =
            extractMaliNode(dts)
                ?: return dts

        var modified =
            node.text

        modified =
            replaceScalar(
                modified,
                "gpu_max_clock",
                profile.maxClockKhz
            )

        modified =
            replaceScalar(
                modified,
                "gpu_max_clock_limit",
                profile.maxClockLimitKhz
            )

        modified =
            replaceScalar(
                modified,
                "gpu_min_clock",
                profile.minClockKhz
            )

        modified =
            replaceScalar(
                modified,
                "gpu_dvfs_start_clock",
                profile.startClockKhz
            )

        modified =
            replaceScalar(
                modified,
                "gpu_dvfs_bl_config_clock",
                profile.bootClockKhz
            )

        modified =
            replaceScalar(
                modified,
                "gpu_default_voltage",
                profile.defaultVoltageUv
            )

        if (
            Regex("""\bgpu_voltage_margin\s*=""")
                .containsMatchIn(modified)
        ) {
            modified =
                replaceScalar(
                    modified,
                    "gpu_voltage_margin",
                    profile.voltageMarginUv
                )
        }

        if (profile.dvfsLevels.isNotEmpty()) {

            val values =
                mutableListOf<Long>()

            profile.dvfsLevels.forEach {
                level ->

                values +=
                    level.frequencyKhz

                values +=
                    level.extraValues
            }

            modified =
                replaceArray(
                    modified,
                    "gpu_dvfs_table",
                    values
                )
        }

        if (
            profile.throttlingKhz
                .isNotEmpty()
        ) {
            modified =
                replaceArray(
                    modified,
                    "gpu_temp_throttling",
                    profile.throttlingKhz
                )
        }

        return dts.substring(
            0,
            node.start
        ) +
            modified +
            dts.substring(
                node.end
            )
    }


    private data class NodeBlock(
        val name: String,
        val start: Int,
        val end: Int,
        val text: String
    )


    private fun extractMaliNode(
        dts: String
    ): NodeBlock? {

        val match =
            Regex(
                """(?i)\bmali@[0-9a-f]+\s*\{"""
            ).find(dts)
                ?: return null

        val openBrace =
            dts.indexOf(
                '{',
                match.range.first
            )

        if (openBrace < 0)
            return null

        var depth = 0

        var inString = false
        var escaped = false

        var index =
            openBrace

        while (index < dts.length) {

            val c =
                dts[index]

            if (inString) {

                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }

            } else {

                when (c) {

                    '"' ->
                        inString = true

                    '{' ->
                        depth++

                    '}' -> {

                        depth--

                        if (depth == 0) {

                            var end =
                                index + 1

                            if (
                                end < dts.length &&
                                dts[end] == ';'
                            ) {
                                end++
                            }

                            return NodeBlock(
                                name =
                                    match.value
                                        .substringBefore('{')
                                        .trim(),
                                start =
                                    match.range.first,
                                end =
                                    end,
                                text =
                                    dts.substring(
                                        match.range.first,
                                        end
                                    )
                            )
                        }
                    }
                }
            }

            index++
        }

        return null
    }


    private fun readScalar(
        node: String,
        property: String
    ): Long? {

        val regex =
            Regex(
                """(?m)\b${Regex.escape(property)}\s*=\s*<\s*(0[xX][0-9a-fA-F]+|\d+)\s*>\s*;"""
            )

        val token =
            regex.find(node)
                ?.groupValues
                ?.getOrNull(1)
                ?: return null

        return parseNumber(token)
    }


    private fun readArray(
        node: String,
        property: String
    ): List<Long> {

        val regex =
            Regex(
                """(?s)\b${Regex.escape(property)}\s*=\s*<(.*?)>\s*;"""
            )

        val content =
            regex.find(node)
                ?.groupValues
                ?.getOrNull(1)
                ?: return emptyList()

        return Regex(
            """0[xX][0-9a-fA-F]+|\d+"""
        )
            .findAll(content)
            .mapNotNull {
                parseNumber(
                    it.value
                )
            }
            .toList()
    }


    private fun replaceScalar(
        node: String,
        property: String,
        value: Long
    ): String {

        val regex =
            Regex(
                """(?m)(\b${Regex.escape(property)}\s*=\s*<\s*)(0[xX][0-9a-fA-F]+|\d+)(\s*>\s*;)"""
            )

        if (!regex.containsMatchIn(node))
            return node

        return regex.replaceFirst(
            node
        ) {
            match ->

            match.groupValues[1] +
                toHex(value) +
                match.groupValues[3]
        }
    }


    private fun replaceArray(
        node: String,
        property: String,
        values: List<Long>
    ): String {

        val regex =
            Regex(
                """(?s)(\b${Regex.escape(property)}\s*=\s*<)(.*?)(>\s*;)"""
            )

        if (!regex.containsMatchIn(node))
            return node

        val formatted =
            values.joinToString(
                separator = " "
            ) {
                toHex(it)
            }

        return regex.replaceFirst(
            node
        ) {
            match ->

            match.groupValues[1] +
                formatted +
                match.groupValues[3]
        }
    }


    private fun parseNumber(
        value: String
    ): Long? {

        return try {

            if (
                value.startsWith(
                    "0x",
                    ignoreCase = true
                )
            ) {
                value.substring(2)
                    .toLong(16)
            } else {
                value.toLong()
            }

        } catch (_: Exception) {
            null
        }
    }


    private fun toHex(
        value: Long
    ): String {

        return "0x" +
            value.toString(16)
    }
}
