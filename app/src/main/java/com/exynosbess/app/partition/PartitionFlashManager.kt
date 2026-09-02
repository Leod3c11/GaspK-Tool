package com.exynosbess.app.partition

import android.content.Context
import android.os.Process
import com.exynosbess.app.boot.ShellExecutor
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PartitionFlashManager {

    data class Result(
        val success: Boolean,
        val message: String,
        val backupPath: String? = null,
        val expectedSha256: String? = null,
        val actualSha256: String? = null
    )

    fun flash(
        context: Context,
        partition: PartitionInfo
    ): Result {

        try {

            val workspace =
                PartitionWorkspace.prepare(
                    context,
                    partition
                )

            /*
             * Flash somente da imagem reconstruída da sessão.
             * Nunca usamos original.img nem arquivos do editor.
             */
            val rebuilt =
                workspace.rebuilt

            if (
                !rebuilt.exists() ||
                !rebuilt.isFile ||
                rebuilt.length() <= 0L
            ) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "rebuilt.img não foi encontrado ou está vazio."
                )
            }

            /*
             * A imagem só pode ser instalada se existe uma
             * importação original correspondente.
             */
            if (
                !workspace.original.exists() ||
                workspace.original.length() <= 0L
            ) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "A imagem original da partição não está disponível."
                )
            }

            /*
             * O estado VERIFIED não é suficiente.
             *
             * rebuilt.img precisa ser byte-a-byte o mesmo
             * arquivo que passou pelo verificador.
             */
            val verification =
                PartitionVerificationStore.check(
                    context,
                    partition
                )

            if (!verification.valid) {

                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        verification.message +
                        "\n\nExecute Verificar reconstrução novamente."
                )
            }

            /*
             * Resolver novamente a partição.
             *
             * Não confiamos cegamente no resolvedPath salvo quando
             * a tela foi aberta.
             */
            val resolve =
                ShellExecutor.root(
                    "readlink -f '${shellEscape(partition.aliasPath)}' 2>/dev/null"
                )

            var currentPath =
                if (resolve.success) {
                    resolve.output
                        .lineSequence()
                        .lastOrNull {
                            it.isNotBlank()
                        }
                        ?.trim()
                        .orEmpty()
                } else {
                    ""
                }

            /*
             * Algumas árvores /dev/block possuem nó real sem symlink.
             */
            if (currentPath.isBlank()) {

                val fallback =
                    ShellExecutor.root(
                        "[ -e '${shellEscape(partition.resolvedPath)}' ] && " +
                            "echo '${shellEscape(partition.resolvedPath)}'"
                    )

                if (fallback.success) {
                    currentPath =
                        fallback.output.trim()
                }
            }

            if (currentPath.isBlank()) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "A partição não pôde ser localizada novamente."
                )
            }

            /*
             * Proteção contra mudança inesperada do destino.
             */
            val expectedPath =
                partition.resolvedPath.trim()

            if (
                expectedPath.isNotBlank() &&
                currentPath != expectedPath
            ) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "O destino da partição mudou desde a detecção.\n\n" +
                        "Esperado:\n$expectedPath\n\n" +
                        "Atual:\n$currentPath\n\n" +
                        "Volte ao Gerenciador de Partições e detecte novamente."
                )
            }

            /*
             * Confirmar que o block device ainda existe.
             */
            val deviceCheck =
                ShellExecutor.root(
                    "[ -b '${shellEscape(currentPath)}' ]"
                )

            if (!deviceCheck.success) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "O destino não é um block device válido:\n$currentPath"
                )
            }

            val partitionSize =
                getPartitionSize(
                    currentPath
                )

            if (partitionSize <= 0L) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "Não foi possível determinar o tamanho da partição."
                )
            }

            val imageSize =
                rebuilt.length()

            val originalSize =
                workspace.original.length()

            /*
             * Nenhuma imagem pode exceder a partição física.
             */
            if (imageSize > partitionSize) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "A imagem reconstruída é maior que a partição.\n\n" +
                        "Imagem: $imageSize bytes\n" +
                        "Partição: $partitionSize bytes"
                )
            }

            /*
             * DTB/DTBO são reconstruídos preservando o container
             * físico da partição. Para esses tipos exigimos tamanho
             * idêntico ao original importado.
             *
             * BOOT pode ser menor que a partição física.
             */
            if (
                partition.type != PartitionType.BOOT &&
                imageSize != originalSize
            ) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "${partition.type.name} reconstruído mudou o tamanho " +
                        "da imagem da partição.\n\n" +
                        "Original: $originalSize bytes\n" +
                        "Reconstruída: $imageSize bytes"
                )
            }

            /*
             * Hash da imagem ANTES de tocar na partição.
             *
             * Calculado dentro do app, sem depender de sha256sum
             * existir no Android.
             */
            val expectedHash =
                sha256(
                    rebuilt
                )

            if (expectedHash.isBlank()) {
                return fail(
                    "FLASH BLOQUEADO.\n\n" +
                        "Falha ao calcular SHA-256 da imagem reconstruída."
                )
            }

            /*
             * Backup pré-flash.
             *
             * Timestamp: nunca sobrescrever um backup anterior.
             */
            val backupDirectory =
                File(
                    workspace.root,
                    "preflash_backups"
                )

            if (!backupDirectory.exists()) {
                backupDirectory.mkdirs()
            }

            val timestamp =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(
                    Date()
                )

            val backup =
                File(
                    backupDirectory,
                    "${sanitize(partition.name)}_preflash_$timestamp.img"
                )

            val backupTmp =
                File(
                    backupDirectory,
                    ".${backup.name}.tmp"
                )

            if (backupTmp.exists()) {
                backupTmp.delete()
            }

            /*
             * Lemos a partição inteira antes de escrever qualquer byte.
             */
            val backupResult =
                ShellExecutor.root(
                    "dd if='${shellEscape(currentPath)}' " +
                        "of='${shellEscape(backupTmp.absolutePath)}' " +
                        "bs=4096 && sync"
                )

            if (!backupResult.success) {

                backupTmp.delete()

                return fail(
                    "FLASH CANCELADO.\n\n" +
                        "Não foi possível criar o backup pré-flash.\n\n" +
                        backupResult.output
                )
            }

            /*
             * O arquivo foi criado como root.
             */
            ShellExecutor.root(
                "chown ${Process.myUid()}:${Process.myUid()} " +
                    "'${shellEscape(backupTmp.absolutePath)}' && " +
                    "chmod 600 '${shellEscape(backupTmp.absolutePath)}'"
            )

            if (
                !backupTmp.exists() ||
                backupTmp.length() != partitionSize
            ) {

                val backupLength =
                    if (backupTmp.exists()) {
                        backupTmp.length()
                    } else {
                        0L
                    }

                return fail(
                    "FLASH CANCELADO.\n\n" +
                        "O backup pré-flash não corresponde à partição.\n\n" +
                        "Backup: $backupLength bytes\n" +
                        "Partição: $partitionSize bytes"
                )
            }

            if (!backupTmp.renameTo(backup)) {
                return fail(
                    "FLASH CANCELADO.\n\n" +
                        "Não foi possível finalizar o backup pré-flash."
                )
            }

            /*
             * Sincronizar antes da gravação.
             */
            val preSync =
                ShellExecutor.root(
                    "sync"
                )

            if (!preSync.success) {
                return fail(
                    "FLASH CANCELADO.\n\n" +
                        "Falha ao sincronizar o sistema antes da gravação.\n\n" +
                        "Backup preservado em:\n${backup.absolutePath}"
                )
            }

            /*
             * FLASH.
             *
             * Esta é a primeira operação que escreve no block device.
             */
            val flash =
                ShellExecutor.root(
                    "dd if='${shellEscape(rebuilt.absolutePath)}' " +
                        "of='${shellEscape(currentPath)}' " +
                        "bs=4096 && sync"
                )

            if (!flash.success) {
                return Result(
                    success = false,
                    backupPath = backup.absolutePath,
                    expectedSha256 = expectedHash,
                    message =
                        "FALHA DURANTE O FLASH.\n\n" +
                            flash.output +
                            "\n\nBackup pré-flash:\n" +
                            backup.absolutePath +
                            "\n\nNÃO REINICIE O APARELHO."
                )
            }

            /*
             * READBACK.
             *
             * Releitura da quantidade de dados correspondente à imagem.
             * Lemos em blocos de 4096; o último pode passar alguns bytes.
             * O hash abaixo considera somente imageSize bytes.
             */
            val readback =
                File(
                    workspace.root,
                    "postflash_readback.img"
                )

            if (readback.exists()) {
                readback.delete()
            }

            val blocks =
                (imageSize + 4095L) / 4096L

            val readbackResult =
                ShellExecutor.root(
                    "dd if='${shellEscape(currentPath)}' " +
                        "of='${shellEscape(readback.absolutePath)}' " +
                        "bs=4096 count=$blocks"
                )

            if (!readbackResult.success) {
                return Result(
                    success = false,
                    backupPath = backup.absolutePath,
                    expectedSha256 = expectedHash,
                    message =
                        "A partição foi gravada, mas a leitura de verificação falhou.\n\n" +
                            readbackResult.output +
                            "\n\nBackup pré-flash:\n" +
                            backup.absolutePath +
                            "\n\nNÃO REINICIE O APARELHO."
                )
            }

            ShellExecutor.root(
                "chown ${Process.myUid()}:${Process.myUid()} " +
                    "'${shellEscape(readback.absolutePath)}' && " +
                    "chmod 600 '${shellEscape(readback.absolutePath)}'"
            )

            if (
                !readback.exists() ||
                readback.length() < imageSize
            ) {
                return Result(
                    success = false,
                    backupPath = backup.absolutePath,
                    expectedSha256 = expectedHash,
                    message =
                        "A partição foi gravada, mas o readback está incompleto.\n\n" +
                            "NÃO REINICIE O APARELHO."
                )
            }

            val actualHash =
                sha256Prefix(
                    readback,
                    imageSize
                )

            /*
             * Não precisamos manter a leitura temporária.
             */
            readback.delete()

            if (
                actualHash.isBlank() ||
                actualHash != expectedHash
            ) {
                return Result(
                    success = false,
                    backupPath = backup.absolutePath,
                    expectedSha256 = expectedHash,
                    actualSha256 = actualHash,
                    message =
                        "ATENÇÃO: VERIFICAÇÃO PÓS-FLASH FALHOU.\n\n" +
                            "Esperado:\n$expectedHash\n\n" +
                            "Lido da partição:\n$actualHash\n\n" +
                            "Backup pré-flash:\n" +
                            backup.absolutePath +
                            "\n\nNÃO REINICIE O APARELHO."
                )
            }

            return Result(
                success = true,
                backupPath = backup.absolutePath,
                expectedSha256 = expectedHash,
                actualSha256 = actualHash,
                message =
                    "${partition.name} gravada e verificada com sucesso.\n\n" +
                        "Tipo: ${partition.type.name}\n\n" +
                        "Partição:\n$currentPath\n\n" +
                        "Imagem:\n${rebuilt.absolutePath}\n\n" +
                        "SHA-256:\n$expectedHash\n\n" +
                        "Backup pré-flash:\n" +
                        backup.absolutePath +
                        "\n\nNenhum reboot foi executado."
            )

        } catch (e: Exception) {

            return fail(
                "Falha inesperada durante preparação do flash.\n\n" +
                    (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    private fun getPartitionSize(
        path: String
    ): Long {

        /*
         * Primeira tentativa: blockdev.
         */
        val blockdev =
            ShellExecutor.root(
                "blockdev --getsize64 '${shellEscape(path)}' 2>/dev/null"
            )

        if (blockdev.success) {

            val value =
                blockdev.output
                    .lineSequence()
                    .map {
                        it.trim()
                    }
                    .firstOrNull {
                        it.toLongOrNull() != null
                    }
                    ?.toLongOrNull()

            if (
                value != null &&
                value > 0L
            ) {
                return value
            }
        }

        /*
         * Fallback via sysfs.
         * /sys/class/block/<device>/size fornece setores de 512 bytes.
         */
        val deviceName =
            path.substringAfterLast('/')

        val sysfs =
            ShellExecutor.root(
                "cat '/sys/class/block/${shellEscape(deviceName)}/size' 2>/dev/null"
            )

        if (sysfs.success) {

            val sectors =
                sysfs.output
                    .trim()
                    .toLongOrNull()

            if (
                sectors != null &&
                sectors > 0L
            ) {
                return sectors * 512L
            }
        }

        return 0L
    }

    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        FileInputStream(file).use { input ->

            val buffer =
                ByteArray(
                    1024 * 1024
                )

            while (true) {

                val read =
                    input.read(buffer)

                if (read <= 0) {
                    break
                }

                digest.update(
                    buffer,
                    0,
                    read
                )
            }
        }

        return digest.digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun sha256Prefix(
        file: File,
        bytesToRead: Long
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        var remaining =
            bytesToRead

        FileInputStream(file).use { input ->

            val buffer =
                ByteArray(
                    1024 * 1024
                )

            while (remaining > 0L) {

                val request =
                    minOf(
                        buffer.size.toLong(),
                        remaining
                    ).toInt()

                val read =
                    input.read(
                        buffer,
                        0,
                        request
                    )

                if (read <= 0) {
                    return ""
                }

                digest.update(
                    buffer,
                    0,
                    read
                )

                remaining -=
                    read.toLong()
            }
        }

        return digest.digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun sanitize(
        value: String
    ): String =
        value.replace(
            Regex("[^A-Za-z0-9._-]"),
            "_"
        )

    /*
     * Paths usados aqui são originados da detecção interna do app,
     * mas escapamos aspas simples por segurança.
     */
    private fun shellEscape(
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
            success = false,
            message = message
        )
}
