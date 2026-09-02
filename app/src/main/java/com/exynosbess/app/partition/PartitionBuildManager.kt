package com.exynosbess.app.partition

import android.content.Context
import android.os.Process
import com.exynosbess.app.boot.BootDtbComponentRebuilder
import com.exynosbess.app.boot.DtbScanner
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object PartitionBuildManager {

    data class Result(
        val success: Boolean,
        val message: String,
        val output: File? = null
    )

    private data class SourceEntry(
        val index: Int,
        val source: String,
        val offset: Long,
        val size: Long
    )

    fun rebuild(
        context: Context,
        partition: PartitionInfo
    ): Result {

        return try {

            when (partition.type) {

                PartitionType.BOOT ->
                    rebuildBoot(
                        context,
                        partition
                    )

                PartitionType.DTB ->
                    rebuildDtb(
                        context,
                        partition
                    )

                PartitionType.DTBO ->
                    rebuildDtbo(
                        context,
                        partition
                    )
            }

        } catch (e: Exception) {

            Result(
                false,
                "Falha na reconstrução:\n" +
                    (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    fun verify(
        context: Context,
        partition: PartitionInfo
    ): Result {

        return try {

            when (partition.type) {

                PartitionType.BOOT ->
                    verifyBoot(
                        context,
                        partition
                    )

                PartitionType.DTB ->
                    verifyDtb(
                        context,
                        partition
                    )

                PartitionType.DTBO ->
                    verifyDtbo(
                        context,
                        partition
                    )
            }

        } catch (e: Exception) {

            Result(
                false,
                "Falha na verificação:\n" +
                    (e.message ?: e.javaClass.simpleName)
            )
        }
    }


    // ============================================================
    // BOOT
    // ============================================================

    private fun rebuildBoot(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val session =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val original =
            session.original

        val extracted =
            session.extracted

        if (
            !original.exists() ||
            original.length() <= 0L
        ) {
            return fail(
                "original.img não encontrado."
            )
        }

        if (
            !extracted.exists() ||
            !extracted.isDirectory
        ) {
            return fail(
                "BOOT ainda não foi extraído."
            )
        }

        val magiskboot =
            ToolManager.installMagiskBoot(
                context
            )

        if (
            !magiskboot.success ||
            magiskboot.path == null
        ) {
            return fail(
                "Falha preparando magiskboot:\n" +
                    magiskboot.message
            )
        }

        /*
         * Reconstruir somente os componentes que possuem DTBs.
         *
         * O rebuilder já trata:
         * kernel
         * extra
         * dtb
         * kernel_dtb
         */
        val componentResult =
            BootDtbComponentRebuilder.rebuild(
                context,
                extracted
            )

        if (!componentResult.success) {
            return fail(
                "Falha reconstruindo componentes do boot:\n" +
                    componentResult.message
            )
        }

        val repackDir =
            File(
                session.root,
                "repack"
            )

        if (repackDir.exists()) {
            repackDir.deleteRecursively()
        }

        repackDir.mkdirs()

        val components =
            listOf(
                "kernel",
                "ramdisk.cpio",
                "second",
                "dtb",
                "kernel_dtb",
                "extra",
                "recovery_dtbo",
                "header"
            )

        var copied = 0

        for (name in components) {

            val rebuiltComponent =
                componentResult.components[
                    name
                ]

            val originalComponent =
                File(
                    extracted,
                    name
                )

            val source =
                when {

                    rebuiltComponent != null &&
                        rebuiltComponent.exists() ->
                        rebuiltComponent

                    originalComponent.exists() ->
                        originalComponent

                    else ->
                        null
                }

            if (source != null) {

                source.copyTo(
                    File(
                        repackDir,
                        name
                    ),
                    overwrite = true
                )

                copied++
            }
        }

        if (copied == 0) {
            return fail(
                "Nenhum componente de boot disponível para repack."
            )
        }

        val newBoot =
            File(
                repackDir,
                "new-boot.img"
            )

        if (newBoot.exists()) {
            newBoot.delete()
        }

        val repack =
            ShellExecutor.root(
                "cd '${escape(repackDir.absolutePath)}' && " +
                    "'${escape(magiskboot.path)}' repack " +
                    "'${escape(original.absolutePath)}'"
            )

        if (
            !repack.success ||
            !newBoot.exists() ||
            newBoot.length() <= 0L
        ) {
            return fail(
                "magiskboot não conseguiu reconstruir o boot.\n\n" +
                    repack.output
            )
        }

        if (session.rebuilt.exists()) {
            session.rebuilt.delete()
        }

        newBoot.copyTo(
            session.rebuilt,
            overwrite = true
        )

        if (
            !session.rebuilt.exists() ||
            session.rebuilt.length() <= 0L
        ) {
            return fail(
                "rebuilt.img não foi criado."
            )
        }

        /*
         * Teste estrutural imediato.
         */
        val verifyDir =
            File(
                session.root,
                "rebuild_structure_test"
            )

        if (verifyDir.exists()) {
            verifyDir.deleteRecursively()
        }

        verifyDir.mkdirs()

        val unpack =
            ShellExecutor.root(
                "cd '${escape(verifyDir.absolutePath)}' && " +
                    "'${escape(magiskboot.path)}' unpack " +
                    "'${escape(session.rebuilt.absolutePath)}'"
            )

        if (!unpack.success) {

            session.rebuilt.delete()

            return fail(
                "O boot foi criado, mas não pôde ser " +
                    "descompactado novamente.\n\n" +
                    unpack.output
            )
        }

        verifyDir.deleteRecursively()

        return Result(
            true,
            "BOOT reconstruído com sucesso.\n\n" +
                "Imagem:\n${session.rebuilt.absolutePath}\n\n" +
                "Tamanho: ${session.rebuilt.length()} bytes",
            session.rebuilt
        )
    }


    private fun verifyBoot(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val session =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val rebuilt =
            session.rebuilt

        if (
            !rebuilt.exists() ||
            rebuilt.length() <= 0L
        ) {
            return fail(
                "rebuilt.img não encontrado."
            )
        }

        val magiskboot =
            ToolManager.installMagiskBoot(
                context
            )

        if (
            !magiskboot.success ||
            magiskboot.path == null
        ) {
            return fail(
                "magiskboot indisponível."
            )
        }

        val verifyDir =
            File(
                session.root,
                "verify_boot"
            )

        if (verifyDir.exists()) {
            verifyDir.deleteRecursively()
        }

        verifyDir.mkdirs()

        val unpack =
            ShellExecutor.root(
                "cd '${escape(verifyDir.absolutePath)}' && " +
                    "'${escape(magiskboot.path)}' unpack " +
                    "'${escape(rebuilt.absolutePath)}'"
            )

        if (!unpack.success) {
            return fail(
                "BOOT reconstruído é estruturalmente inválido.\n\n" +
                    unpack.output
            )
        }

        /*
         * Conferir os DTBs editados contra os DTBs existentes
         * depois do repack.
         */
        val originalEntries =
            readSourceMap(
                session.extracted
            )

        val bootEntries =
            originalEntries.filter {
                it.source in setOf(
                    "kernel",
                    "extra",
                    "dtb",
                    "kernel_dtb"
                )
            }

        if (bootEntries.isNotEmpty()) {

            val scan =
                DtbScanner.scan(
                    verifyDir.absolutePath
                )

            if (!scan.success) {
                return fail(
                    "Não foi possível localizar os DTBs " +
                        "no boot reconstruído.\n\n" +
                        scan.message
                )
            }

            for (
                group in
                bootEntries.groupBy {
                    it.source
                }
            ) {

                val source =
                    group.key

                val expectedEntries =
                    group.value.sortedBy {
                        it.offset
                    }

                val actualEntries =
                    scan.entries
                        .filter {
                            it.sourceFile == source
                        }
                        .sortedBy {
                            it.offset
                        }

                if (
                    actualEntries.size !=
                    expectedEntries.size
                ) {
                    return fail(
                        "Quantidade de DTBs mudou em $source.\n\n" +
                            "Esperado: ${expectedEntries.size}\n" +
                            "Encontrado: ${actualEntries.size}"
                    )
                }

                expectedEntries.forEachIndexed {
                    position,
                    expectedEntry ->

                    val dts =
                        File(
                            session.extracted,
                            "dtbs/dtb_${expectedEntry.index}.dts"
                        )

                    val compiled =
                        compileDts(
                            context,
                            dts,
                            File(
                                session.root,
                                "verify_expected_" +
                                    "${expectedEntry.index}.dtb"
                            )
                        )

                    if (!compiled.success) {
                        return compiled
                    }

                    val expectedFile =
                        compiled.output
                            ?: return fail(
                                "DTB compilado inexistente."
                            )

                    val actualEntry =
                        actualEntries[
                            position
                        ]

                    val actualBytes =
                        readRegion(
                            File(
                                verifyDir,
                                source
                            ),
                            actualEntry.offset,
                            actualEntry.size
                        )

                    if (
                        sha256(
                            expectedFile.readBytes()
                        ) !=
                        sha256(
                            actualBytes
                        )
                    ) {
                        return fail(
                            "Round-trip falhou para " +
                                "DTB ${expectedEntry.index} " +
                                "em $source."
                        )
                    }

                    expectedFile.delete()
                }
            }
        }

        verifyDir.deleteRecursively()

        return Result(
            true,
            "BOOT verificado com sucesso.\n\n" +
                "O repack pôde ser aberto novamente e " +
                "os Device Trees recompilados correspondem " +
                "ao conteúdo reconstruído.",
            rebuilt
        )
    }


    // ============================================================
    // DTB PARTITION
    // ============================================================

    private fun rebuildDtb(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val session =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val original =
            session.original

        if (!original.exists()) {
            return fail(
                "original.img não encontrado."
            )
        }

        val entries =
            readSourceMap(
                session.extracted
            )

        if (entries.isEmpty()) {
            return fail(
                "Mapa de DTBs não encontrado."
            )
        }

        original.copyTo(
            session.rebuilt,
            overwrite = true
        )

        RandomAccessFile(
            session.rebuilt,
            "rw"
        ).use { output ->

            for (entry in entries) {

                val dts =
                    File(
                        session.extracted,
                        "dtbs/dtb_${entry.index}.dts"
                    )

                val compiled =
                    compileDts(
                        context,
                        dts,
                        File(
                            session.root,
                            "compiled_dtb_" +
                                "${entry.index}.dtb"
                        )
                    )

                if (!compiled.success) {
                    throw IllegalStateException(
                        compiled.message
                    )
                }

                val replacement =
                    compiled.output
                        ?: throw IllegalStateException(
                            "DTB recompilado não encontrado."
                        )

                if (
                    replacement.length() >
                    entry.size
                ) {
                    replacement.delete()

                    throw IllegalStateException(
                        "DTB ${entry.index} ficou maior " +
                            "que o espaço original.\n\n" +
                            "Novo: ${replacement.length()} bytes\n" +
                            "Slot: ${entry.size} bytes"
                    )
                }

                output.seek(
                    entry.offset
                )

                replacement.inputStream()
                    .use {
                        it.copyTo(
                            object :
                                java.io.OutputStream() {

                                override fun write(
                                    b: Int
                                ) {
                                    output.write(b)
                                }

                                override fun write(
                                    b: ByteArray,
                                    off: Int,
                                    len: Int
                                ) {
                                    output.write(
                                        b,
                                        off,
                                        len
                                    )
                                }
                            }
                        )
                    }

                var padding =
                    entry.size -
                        replacement.length()

                val zeros =
                    ByteArray(
                        4096
                    )

                while (padding > 0L) {

                    val amount =
                        minOf(
                            padding,
                            zeros.size.toLong()
                        ).toInt()

                    output.write(
                        zeros,
                        0,
                        amount
                    )

                    padding -=
                        amount
                }

                replacement.delete()
            }
        }

        if (
            session.rebuilt.length() !=
            original.length()
        ) {
            session.rebuilt.delete()

            return fail(
                "Reconstrução alterou o tamanho da partição DTB."
            )
        }

        return Result(
            true,
            "DTB reconstruído preservando o tamanho " +
                "e as regiões externas aos Device Trees.",
            session.rebuilt
        )
    }


    private fun verifyDtb(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val session =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val rebuilt =
            session.rebuilt

        if (!rebuilt.exists()) {
            return fail(
                "rebuilt.img não encontrado."
            )
        }

        if (
            rebuilt.length() !=
            session.original.length()
        ) {
            return fail(
                "Tamanho reconstruído diferente do original."
            )
        }

        val entries =
            readSourceMap(
                session.extracted
            )

        for (entry in entries) {

            val dts =
                File(
                    session.extracted,
                    "dtbs/dtb_${entry.index}.dts"
                )

            val expected =
                compileDts(
                    context,
                    dts,
                    File(
                        session.root,
                        "verify_dtb_" +
                            "${entry.index}.dtb"
                    )
                )

            if (!expected.success) {
                return expected
            }

            val expectedFile =
                expected.output
                    ?: return fail(
                        "Falha compilando DTB."
                    )

            val actual =
                readRegion(
                    rebuilt,
                    entry.offset,
                    expectedFile.length()
                )

            val equal =
                sha256(
                    expectedFile.readBytes()
                ) ==
                    sha256(
                        actual
                    )

            expectedFile.delete()

            if (!equal) {
                return fail(
                    "DTB ${entry.index} não corresponde " +
                        "ao DTS editado."
                )
            }
        }

        return Result(
            true,
            "Partição DTB verificada com sucesso.",
            rebuilt
        )
    }


    // ============================================================
    // DTBO
    // ============================================================

    private fun rebuildDtbo(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val session =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val original =
            session.original

        if (!original.exists()) {
            return fail(
                "original.img não encontrado."
            )
        }

        val header =
            readDtboHeader(
                original
            ) ?: return fail(
                "Header DTBO inválido."
            )

        val entries =
            readSourceMap(
                session.extracted
            )

        if (entries.isEmpty()) {
            return fail(
                "Nenhum overlay DTBO editável encontrado."
            )
        }

        original.copyTo(
            session.rebuilt,
            overwrite = true
        )

        RandomAccessFile(
            session.rebuilt,
            "rw"
        ).use { output ->

            for (entry in entries) {

                if (
                    !entry.source.startsWith(
                        "dtbo_entry_"
                    )
                ) {
                    continue
                }

                val tableIndex =
                    entry.source
                        .removePrefix(
                            "dtbo_entry_"
                        )
                        .toIntOrNull()
                        ?: throw IllegalStateException(
                            "Índice DTBO inválido: ${entry.source}"
                        )

                if (
                    tableIndex < 0 ||
                    tableIndex >=
                    header.entryCount
                ) {
                    throw IllegalStateException(
                        "Entry DTBO fora da tabela."
                    )
                }

                val dts =
                    File(
                        session.extracted,
                        "dtbs/dtb_${entry.index}.dts"
                    )

                val compiled =
                    compileDts(
                        context,
                        dts,
                        File(
                            session.root,
                            "compiled_dtbo_" +
                                "${entry.index}.dtb"
                        )
                    )

                if (!compiled.success) {
                    throw IllegalStateException(
                        compiled.message
                    )
                }

                val replacement =
                    compiled.output
                        ?: throw IllegalStateException(
                            "Overlay recompilado não encontrado."
                        )

                /*
                 * Por segurança não movemos entries nem
                 * reorganizamos o container.
                 */
                if (
                    replacement.length() >
                    entry.size
                ) {
                    replacement.delete()

                    throw IllegalStateException(
                        "Overlay ${entry.index} ficou maior " +
                            "que o slot DTBO original.\n\n" +
                            "Rebuild bloqueado."
                    )
                }

                output.seek(
                    entry.offset
                )

                output.write(
                    replacement.readBytes()
                )

                var remaining =
                    entry.size -
                        replacement.length()

                val zeros =
                    ByteArray(
                        4096
                    )

                while (remaining > 0L) {

                    val amount =
                        minOf(
                            remaining,
                            zeros.size.toLong()
                        ).toInt()

                    output.write(
                        zeros,
                        0,
                        amount
                    )

                    remaining -=
                        amount
                }

                /*
                 * dt_size é o primeiro uint32 da entry.
                 * Formato DTBO usa big endian.
                 */
                val tableOffset =
                    header.entriesOffset +
                        (
                            tableIndex.toLong() *
                                header.entrySize
                        )

                output.seek(
                    tableOffset
                )

                output.writeInt(
                    replacement.length()
                        .toInt()
                )

                replacement.delete()
            }
        }

        if (
            session.rebuilt.length() !=
            original.length()
        ) {
            session.rebuilt.delete()

            return fail(
                "Tamanho do container DTBO foi alterado."
            )
        }

        return Result(
            true,
            "DTBO reconstruído preservando header, " +
                "offsets e tamanho do container.",
            session.rebuilt
        )
    }


    private fun verifyDtbo(
        context: Context,
        partition: PartitionInfo
    ): Result {

        val session =
            PartitionWorkspace.prepare(
                context,
                partition
            )

        val rebuilt =
            session.rebuilt

        val header =
            readDtboHeader(
                rebuilt
            ) ?: return fail(
                "DTBO reconstruído possui header inválido."
            )

        if (
            rebuilt.length() !=
            session.original.length()
        ) {
            return fail(
                "DTBO mudou de tamanho."
            )
        }

        val entries =
            readSourceMap(
                session.extracted
            )

        RandomAccessFile(
            rebuilt,
            "r"
        ).use { input ->

            for (entry in entries) {

                if (
                    !entry.source.startsWith(
                        "dtbo_entry_"
                    )
                ) {
                    continue
                }

                val tableIndex =
                    entry.source
                        .removePrefix(
                            "dtbo_entry_"
                        )
                        .toIntOrNull()
                        ?: return fail(
                            "Índice DTBO inválido."
                        )

                val tableOffset =
                    header.entriesOffset +
                        (
                            tableIndex.toLong() *
                                header.entrySize
                        )

                input.seek(
                    tableOffset
                )

                val storedSize =
                    input.readInt()
                        .toLong() and
                        0xFFFFFFFFL

                val dts =
                    File(
                        session.extracted,
                        "dtbs/dtb_${entry.index}.dts"
                    )

                val expected =
                    compileDts(
                        context,
                        dts,
                        File(
                            session.root,
                            "verify_dtbo_" +
                                "${entry.index}.dtb"
                        )
                    )

                if (!expected.success) {
                    return expected
                }

                val expectedFile =
                    expected.output
                        ?: return fail(
                            "Overlay esperado ausente."
                        )

                if (
                    storedSize !=
                    expectedFile.length()
                ) {
                    expectedFile.delete()

                    return fail(
                        "Tamanho da entry DTBO " +
                            "$tableIndex não corresponde."
                    )
                }

                val actual =
                    readRegion(
                        rebuilt,
                        entry.offset,
                        expectedFile.length()
                    )

                val equal =
                    sha256(
                        actual
                    ) ==
                        sha256(
                            expectedFile.readBytes()
                        )

                expectedFile.delete()

                if (!equal) {
                    return fail(
                        "Overlay DTBO $tableIndex " +
                            "falhou no round-trip."
                    )
                }
            }
        }

        return Result(
            true,
            "DTBO verificado com sucesso.",
            rebuilt
        )
    }


    // ============================================================
    // HELPERS
    // ============================================================

    private data class DtboHeader(
        val totalSize: Long,
        val headerSize: Long,
        val entrySize: Long,
        val entryCount: Int,
        val entriesOffset: Long
    )

    private fun readDtboHeader(
        file: File
    ): DtboHeader? {

        if (
            !file.exists() ||
            file.length() < 32L
        ) {
            return null
        }

        RandomAccessFile(
            file,
            "r"
        ).use { raf ->

            val magic =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            if (
                magic !=
                0xD7B7AB1EL
            ) {
                return null
            }

            val totalSize =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            val headerSize =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            val entrySize =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            val entryCountLong =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            val entriesOffset =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            raf.readInt() // page size
            raf.readInt() // version

            if (
                totalSize <= 0L ||
                totalSize >
                    file.length() ||
                headerSize < 32L ||
                entrySize < 8L ||
                entryCountLong >
                    Int.MAX_VALUE.toLong() ||
                entriesOffset <
                    headerSize
            ) {
                return null
            }

            return DtboHeader(
                totalSize,
                headerSize,
                entrySize,
                entryCountLong.toInt(),
                entriesOffset
            )
        }
    }


    private fun readSourceMap(
        extracted: File
    ): List<SourceEntry> {

        val file =
            File(
                extracted,
                "dtbs/dtb_sources.txt"
            )

        if (!file.exists()) {
            return emptyList()
        }

        return file
            .readLines()
            .mapNotNull { line ->

                val p =
                    line.split(
                        "|"
                    )

                if (p.size != 4) {
                    return@mapNotNull null
                }

                val index =
                    p[0].toIntOrNull()
                        ?: return@mapNotNull null

                val offset =
                    p[2].toLongOrNull()
                        ?: return@mapNotNull null

                val size =
                    p[3].toLongOrNull()
                        ?: return@mapNotNull null

                SourceEntry(
                    index,
                    p[1],
                    offset,
                    size
                )
            }
            .sortedBy {
                it.index
            }
    }


    private fun compileDts(
        context: Context,
        dts: File,
        output: File
    ): Result {

        if (
            !dts.exists() ||
            dts.length() <= 0L
        ) {
            return fail(
                "DTS não encontrado:\n${dts.absolutePath}"
            )
        }

        val dtc =
            ToolManager.installDtc(
                context
            )

        if (
            !dtc.success ||
            dtc.path == null
        ) {
            return fail(
                "Falha preparando dtc:\n" +
                    dtc.message
            )
        }

        if (output.exists()) {
            output.delete()
        }

        val result =
            ShellExecutor.root(
                "'${escape(dtc.path)}' " +
                    "-I dts -O dtb " +
                    "-o '${escape(output.absolutePath)}' " +
                    "'${escape(dts.absolutePath)}'"
            )

        if (
            !result.success ||
            !output.exists() ||
            output.length() < 40L
        ) {
            return fail(
                "Falha compilando ${dts.name}:\n\n" +
                    result.output
            )
        }

        ShellExecutor.root(
            "chown ${Process.myUid()}:${Process.myUid()} " +
                "'${escape(output.absolutePath)}' && " +
                "chmod 600 '${escape(output.absolutePath)}'"
        )

        RandomAccessFile(
            output,
            "r"
        ).use { raf ->

            val magic =
                raf.readInt()
                    .toLong() and
                    0xFFFFFFFFL

            if (
                magic !=
                0xD00DFEEDL
            ) {
                output.delete()

                return fail(
                    "${dts.name} gerou um DTB " +
                        "com magic inválido."
                )
            }
        }

        return Result(
            true,
            "DTB compilado",
            output
        )
    }


    private fun readRegion(
        file: File,
        offset: Long,
        size: Long
    ): ByteArray {

        if (
            offset < 0L ||
            size < 0L ||
            offset + size >
                file.length() ||
            size >
                Int.MAX_VALUE.toLong()
        ) {
            throw IllegalStateException(
                "Região inválida em ${file.name}"
            )
        }

        val bytes =
            ByteArray(
                size.toInt()
            )

        RandomAccessFile(
            file,
            "r"
        ).use { raf ->

            raf.seek(
                offset
            )

            raf.readFully(
                bytes
            )
        }

        return bytes
    }


    private fun sha256(
        bytes: ByteArray
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        return digest
            .digest(bytes)
            .joinToString("") {
                "%02x".format(it)
            }
    }


    private fun escape(
        value: String
    ): String =
        value.replace(
            "'",
            "'\"'\"'"
        )


    private fun fail(
        message: String
    ): Result =
        Result(
            false,
            message
        )
}
