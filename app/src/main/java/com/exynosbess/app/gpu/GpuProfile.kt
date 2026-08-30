package com.exynosbess.app.gpu

data class GpuDvfsLevel(
    var frequencyKhz: Long,
    val extraValues: MutableList<Long>
)

data class GpuProfile(
    val vendor: String,
    val nodeName: String,

    var maxClockKhz: Long,
    var maxClockLimitKhz: Long,
    var minClockKhz: Long,
    var startClockKhz: Long,
    var bootClockKhz: Long,

    var defaultVoltageUv: Long,
    var voltageMarginUv: Long,

    val dvfsLevels: MutableList<GpuDvfsLevel>,
    val throttlingKhz: MutableList<Long>
)
