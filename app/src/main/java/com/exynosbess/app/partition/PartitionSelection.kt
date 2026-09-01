package com.exynosbess.app.partition

object PartitionSelection {

    @Volatile
    private var selected:
        PartitionInfo? = null

    fun set(
        partition: PartitionInfo
    ) {
        selected = partition
    }

    fun get():
        PartitionInfo? {
        return selected
    }

    fun clear() {
        selected = null
    }
}
