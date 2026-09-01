package com.exynosbess.app.partition

data class PartitionDtbEntry(
    val index: Int,
    val sourceFile: String,
    val offset: Long,
    val size: Long
)
