package com.exynosbess.app.partition

import android.content.Context
import com.exynosbess.app.boot.DtbDecompiler
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DtboSessionExtractor {

    private const val DTBO_MAGIC =
        0xD7B7AB1E.toInt()

    private const val FDT_MAGIC =
        0xD00DFEED.toInt()

    private data class Header(
        val totalSize: Long,
        val headerSize: Int,
        val entrySize: Int,
        val entryCount: Int,
        val entriesOffset: Int,
        val pageSize: Int,
        val version: Int
    )

    private data class Entry(
        val index: Int,
        val size: Long,
        val offset: Long
    )


    fun extract(
        context: Context,
        partition: PartitionInfo
    ): PartitionPipeline.Result {

        if (
            partition.type !=
            PartitionType.DTBO
        ) {

            return PartitionPipeline.Result(
                false,
                "Extrator DTBO chamado para ${partition.type}.",
                null
            )
        }

        val workspace =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val source =
            workspace.original

        if (
            !source.exists() ||
            source.length() < 32L
        ) {

            return PartitionPipeline.Result(
                false,
                "Importe a partição DTBO primeiro.",
                null
            )
        }

        val bytes =
            try {
                source.readBytes()
            } catch (e: Exception) {

                return PartitionPipeline.Result(
                    false,
                    "Falha lendo DTBO: ${e.message}",
                    null
                )
            }

        val header =
            parseHeader(bytes)
                ?: return PartitionPipeline.Result(
                    false,
                    "Imagem DTBO inválida ou formato não reconhecido.",
                    null
                )

        val entries =
            parseEntries(
                bytes,
                header
            )

        if (entries.isEmpty()) {

            return PartitionPipeline.Result(
                false,
                "DTBO válido, mas nenhuma entrada foi encontrada.",
                null
            )
        }

        if (workspace.extracted.exists()) {
            workspace.extracted.deleteRecursively()
        }

        val dtbDir =
            File(
                workspace.extracted,
                "dtbs"
            )

        dtbDir.mkdirs()

        val sourceMap =
            mutableListOf<String>()

        var rawFdtCount =
            0

        var unsupportedCount =
            0

        for (entry in entries) {

            val end =
                entry.offset +
                    entry.size

            if (
                entry.offset < 0L ||
                entry.size <= 0L ||
                entry.offset >
                    Int.MAX_VALUE.toLong() ||
                entry.size >
                    Int.MAX_VALUE.toLong() ||
                end >
                    bytes.size.toLong() ||
                end >
                    Int.MAX_VALUE.toLong()
            ) {

                unsupportedCount++
                continue
            }

            val blob =
                bytes.copyOfRange(
                    entry.offset.toInt(),
                    (
                        entry.offset +
                            entry.size
                    ).toInt()
                )

            if (
                blob.size < 8 ||
                readIntBe(
                    blob,
                    0
                ) != FDT_MAGIC
            ) {

                /*
                 * Entradas comprimidas ou formatos não-FDT
                 * não são passadas ao dtc silenciosamente.
                 */
                unsupportedCount++
                continue
            }

            val out =
                File(
                    dtbDir,
                    "dtb_${entry.index}.dtb"
                )

            out.writeBytes(
                blob
            )

            sourceMap.add(
                "${entry.index}|" +
                    "dtbo_entry_${entry.index}|" +
                    "${entry.offset}|" +
                    "${entry.size}"
            )

            rawFdtCount++
        }

        File(
            dtbDir,
            "dtb_sources.txt"
        ).writeText(
            sourceMap.joinToString(
                "\n"
            )
        )

        if (rawFdtCount == 0) {

            return PartitionPipeline.Result(
                false,
                buildString {

                    append(
                        "DTBO detectado corretamente, mas nenhuma entrada FDT crua pôde ser editada."
                    )

                    append(
                        "\n\nVersão: ${header.version}"
                    )

                    append(
                        "\nEntradas: ${header.entryCount}"
                    )

                    append(
                        "\nEntradas comprimidas/não suportadas: $unsupportedCount"
                    )
                },
                null
            )
        }

        val decompile =
            DtbDecompiler.decompileAll(
                context,
                workspace.extracted.absolutePath
            )

        if (!decompile.success) {

            return PartitionPipeline.Result(
                false,
                decompile.message,
                null
            )
        }

        return PartitionPipeline.Result(
            true,
            buildString {

                append(
                    "DTBO Android extraído."
                )

                append(
                    "\n\nVersão: ${header.version}"
                )

                append(
                    "\nPage size: ${header.pageSize}"
                )

                append(
                    "\nEntradas totais: ${header.entryCount}"
                )

                append(
                    "\nOverlays editáveis: $rawFdtCount"
                )

                if (
                    unsupportedCount > 0
                ) {

                    append(
                        "\nNão-FDT/comprimidos: $unsupportedCount"
                    )
                }

                append(
                    "\nDTS gerados: ${decompile.files.size}"
                )
            },
            PartitionSessionState.EXTRACTED
        )
    }


    private fun parseHeader(
        data: ByteArray
    ): Header? {

        if (data.size < 32) {
            return null
        }

        val buffer =
            ByteBuffer
                .wrap(
                    data,
                    0,
                    32
                )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        val magic =
            buffer.int

        if (magic != DTBO_MAGIC) {
            return null
        }

        val totalSize =
            buffer.int
                .toLong() and
                0xFFFFFFFFL

        val headerSize =
            buffer.int

        val entrySize =
            buffer.int

        val entryCount =
            buffer.int

        val entriesOffset =
            buffer.int

        val pageSize =
            buffer.int

        val version =
            buffer.int

        if (
            headerSize < 32 ||
            entrySize < 8 ||
            entryCount < 0 ||
            entryCount > 16384 ||
            entriesOffset < 0 ||
            totalSize <= 0L ||
            totalSize > data.size.toLong()
        ) {
            return null
        }

        val tableEnd =
            entriesOffset.toLong() +
                entrySize.toLong() *
                entryCount.toLong()

        if (
            tableEnd >
            data.size.toLong()
        ) {
            return null
        }

        return Header(
            totalSize = totalSize,
            headerSize = headerSize,
            entrySize = entrySize,
            entryCount = entryCount,
            entriesOffset = entriesOffset,
            pageSize = pageSize,
            version = version
        )
    }


    private fun parseEntries(
        data: ByteArray,
        header: Header
    ): List<Entry> {

        val result =
            mutableListOf<Entry>()

        for (
            index in
            0 until header.entryCount
        ) {

            val position =
                header.entriesOffset +
                    index *
                    header.entrySize

            if (
                position < 0 ||
                position + 8 >
                data.size
            ) {
                break
            }

            val size =
                readUInt32Be(
                    data,
                    position
                )

            val offset =
                readUInt32Be(
                    data,
                    position + 4
                )

            if (
                size <= 0L ||
                offset >
                    Int.MAX_VALUE.toLong() ||
                size >
                    Int.MAX_VALUE.toLong() ||
                offset + size >
                    data.size.toLong()
            ) {
                continue
            }

            result.add(
                Entry(
                    index = index,
                    size = size,
                    offset = offset
                )
            )
        }

        return result
    }


    private fun readUInt32Be(
        data: ByteArray,
        offset: Int
    ): Long {

        return readIntBe(
            data,
            offset
        ).toLong() and
            0xFFFFFFFFL
    }


    private fun readIntBe(
        data: ByteArray,
        offset: Int
    ): Int {

        return ByteBuffer
            .wrap(
                data,
                offset,
                4
            )
            .order(
                ByteOrder.BIG_ENDIAN
            )
            .int
    }
}
