package com.exynosbess.app.partition

import android.content.Context
import java.io.File

object PartitionWorkspace {

    data class Workspace(
        val root: File,
        val original: File,
        val backup: File,
        val extracted: File,
        val edited: File,
        val rebuilt: File,
        val metadata: File
    )

    fun get(
        context: Context,
        partition: PartitionInfo
    ): Workspace {

        val id =
            sanitize(
                partition.name + "_" +
                    partition.resolvedPath
                        .substringAfterLast('/')
            )

        val root =
            File(
                context.filesDir,
                "workspace/partitions/$id"
            )

        return Workspace(
            root = root,
            original =
                File(
                    root,
                    "original.img"
                ),
            backup =
                File(
                    root,
                    "backup.img"
                ),
            extracted =
                File(
                    root,
                    "extracted"
                ),
            edited =
                File(
                    root,
                    "edited"
                ),
            rebuilt =
                File(
                    root,
                    "rebuilt.img"
                ),
            metadata =
                File(
                    root,
                    "metadata.txt"
                )
        )
    }

    fun prepare(
        context: Context,
        partition: PartitionInfo
    ): Workspace {

        val workspace =
            get(
                context,
                partition
            )

        workspace.root.mkdirs()
        workspace.extracted.mkdirs()
        workspace.edited.mkdirs()

        return workspace
    }

    private fun sanitize(
        value: String
    ): String {

        return value
            .replace(
                Regex("[^A-Za-z0-9._-]"),
                "_"
            )
            .take(120)
    }
}
