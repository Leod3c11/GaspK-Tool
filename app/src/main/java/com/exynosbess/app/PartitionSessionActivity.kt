package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.exynosbess.app.partition.BootSessionRebuildManager
import com.exynosbess.app.partition.BootSessionVerifier
import com.exynosbess.app.partition.DtbSessionExtractor
import com.exynosbess.app.partition.DtboSessionExtractor
import com.exynosbess.app.partition.PartitionBuildManager
import com.exynosbess.app.partition.PartitionDtbCatalog
import com.exynosbess.app.partition.PartitionDtsValidator
import com.exynosbess.app.partition.PartitionFlashManager
import com.exynosbess.app.partition.PartitionImportManager
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionPipeline
import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionSessionState
import com.exynosbess.app.partition.PartitionStateStore
import com.exynosbess.app.partition.PartitionType
import com.exynosbess.app.partition.PartitionVerificationStore
import com.exynosbess.app.partition.PartitionWorkspace

class PartitionSessionActivity : Activity() {

    companion object {
        private const val REQUEST_EXPORT_REBUILT =
            7001
    }

    private lateinit var partition:
        PartitionInfo

    private var state =
        PartitionSessionState.DETECTED

    private lateinit var title:
        TextView

    private lateinit var type:
        TextView

    private lateinit var source:
        TextView

    private lateinit var stateView:
        TextView

    private lateinit var prepareButton:
        Button

    private lateinit var editButton:
        Button

    private lateinit var applyButton:
        Button

    private lateinit var exportButton:
        Button

    private lateinit var flashButton:
        Button


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        val selected =
            PartitionSelection.get()

        if (selected == null) {
            finish()
            return
        }

        partition =
            selected

        setContentView(
            R.layout.activity_partition_session
        )

        bindViews()
        setupListeners()
        restoreState()
        render()
    }


    override fun onResume() {
        super.onResume()

        if (::partition.isInitialized) {
            restoreState()
            render()
        }
    }


    private fun bindViews() {

        title =
            findViewById(
                R.id.partitionSessionTitle
            )

        type =
            findViewById(
                R.id.partitionSessionType
            )

        source =
            findViewById(
                R.id.partitionSessionSource
            )

        stateView =
            findViewById(
                R.id.partitionSessionState
            )

        prepareButton =
            findViewById(
                R.id.partitionPrepareButton
            )

        editButton =
            findViewById(
                R.id.partitionEditButton
            )

        applyButton =
            findViewById(
                R.id.partitionApplyButton
            )

        exportButton =
            findViewById(
                R.id.partitionExportButton
            )

        flashButton =
            findViewById(
                R.id.partitionFlashButton
            )
    }


    private fun setupListeners() {

        prepareButton.setOnClickListener {
            confirmPrepare()
        }

        editButton.setOnClickListener {
            openDtbEditor()
        }

        applyButton.setOnClickListener {
            executeApply()
        }

        exportButton.setOnClickListener {
            requestExport()
        }

        flashButton.setOnClickListener {
            confirmFlash()
        }
    }


    private fun restoreState() {

        state =
            PartitionStateStore.load(
                this,
                partition
            )

        /*
         * Um estado VERIFIED persistido só continua válido
         * se rebuilt.img ainda for exatamente a imagem selada.
         */
        if (
            state ==
            PartitionSessionState.VERIFIED
        ) {

            val verification =
                PartitionVerificationStore.check(
                    this,
                    partition
                )

            if (!verification.valid) {

                state =
                    PartitionSessionState.BUILT

                PartitionStateStore.save(
                    this,
                    partition,
                    state
                )
            }
        }

        /*
         * Se já existem DTBs extraídos, a sessão está
         * no mínimo preparada.
         */
        val editable =
            editableDtbs()

        if (
            editable.isNotEmpty() &&
            state in setOf(
                PartitionSessionState.DETECTED,
                PartitionSessionState.IMPORTED
            )
        ) {

            state =
                PartitionSessionState.EXTRACTED

            PartitionStateStore.save(
                this,
                partition,
                state
            )
        }
    }


    private fun render() {

        val dtbs =
            editableDtbs()

        title.text =
            friendlyPartitionName()

        type.text =
            buildString {

                append(
                    partition.name
                )

                append(
                    "  •  "
                )

                append(
                    partition.sizeDisplay
                )
            }

        source.text =
            buildString {

                append(
                    partition.aliasPath
                )

                if (
                    partition.resolvedPath !=
                    partition.aliasPath
                ) {

                    append(
                        "\n${partition.resolvedPath}"
                    )
                }
            }

        stateView.text =
            stateText(
                dtbs.size
            )

        /*
         * PREPARAR aparece somente enquanto a partição
         * ainda não foi completamente extraída.
         */
        prepareButton.visibility =
            if (
                state in setOf(
                    PartitionSessionState.DETECTED,
                    PartitionSessionState.IMPORTED
                )
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        prepareButton.isEnabled =
            state in setOf(
                PartitionSessionState.DETECTED,
                PartitionSessionState.IMPORTED
            )

        prepareButton.text =
            if (
                state ==
                PartitionSessionState.IMPORTED
            ) {
                "Concluir preparação"
            } else {
                "Preparar partição"
            }


        /*
         * EDITAR aparece depois da extração.
         */
        editButton.visibility =
            if (dtbs.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        editButton.isEnabled =
            dtbs.isNotEmpty()

        editButton.text =
            when {

                partition.type ==
                    PartitionType.DTBO &&
                    dtbs.size == 1 ->

                    "Editar overlay"

                partition.type ==
                    PartitionType.DTBO ->

                    "Editar overlays (${dtbs.size})"

                dtbs.size == 1 ->

                    "Editar Device Tree"

                else ->

                    "Editar Device Trees (${dtbs.size})"
            }


        /*
         * APLICAR substitui os três botões técnicos:
         *
         * Validar
         * Reconstruir
         * Verificar
         *
         * Internamente TODOS continuam sendo executados.
         */
        val canApply =
            dtbs.isNotEmpty() &&
                state in setOf(
                    PartitionSessionState.EXTRACTED,
                    PartitionSessionState.DIRTY,
                    PartitionSessionState.SAVED,
                    PartitionSessionState.VALIDATED,
                    PartitionSessionState.BUILT
                )

        applyButton.visibility =
            if (canApply) {
                View.VISIBLE
            } else {
                View.GONE
            }

        applyButton.isEnabled =
            canApply

        applyButton.text =
            "Aplicar alterações"


        /*
         * Exportar e instalar só aparecem quando a imagem
         * passou por todo o pipeline.
         */
        val ready =
            state ==
                PartitionSessionState.VERIFIED

        exportButton.visibility =
            if (ready) {
                View.VISIBLE
            } else {
                View.GONE
            }

        flashButton.visibility =
            if (ready) {
                View.VISIBLE
            } else {
                View.GONE
            }

        exportButton.isEnabled =
            ready

        flashButton.isEnabled =
            ready
    }


    private fun stateText(
        dtbCount: Int
    ): String {

        return when (state) {

            PartitionSessionState.DETECTED ->
                "Partição encontrada\n" +
                    "Prepare-a para começar."

            PartitionSessionState.IMPORTED ->
                "Cópia local criada\n" +
                    "Conclua a preparação."

            PartitionSessionState.EXTRACTED ->
                buildString {

                    append(
                        "Pronta para editar"
                    )

                    if (dtbCount > 0) {
                        append(
                            "\n$dtbCount Device Tree(s) disponível(is)"
                        )
                    }
                }

            PartitionSessionState.DIRTY ->
                "Alterações detectadas\n" +
                    "Aplique quando terminar."

            PartitionSessionState.SAVED ->
                "Alterações salvas\n" +
                    "Prontas para aplicar."

            PartitionSessionState.VALIDATED ->
                "Alterações validadas\n" +
                    "Reconstrução pendente."

            PartitionSessionState.BUILT ->
                "Imagem reconstruída\n" +
                    "Verificação pendente."

            PartitionSessionState.VERIFIED ->
                "✓ Alterações prontas\n" +
                    "✓ Estrutura validada\n" +
                    "✓ Imagem reconstruída\n" +
                    "✓ Verificação concluída\n" +
                    "✓ SHA-256 confirmado"
        }
    }


    private fun friendlyPartitionName():
        String {

        return when (
            partition.type
        ) {

            PartitionType.BOOT ->
                "Boot"

            PartitionType.DTB ->
                "Device Tree"

            PartitionType.DTBO ->
                "Device Tree Overlays"
        }
    }


    private fun editableDtbs() =
        PartitionDtbCatalog.load(
            PartitionWorkspace.get(
                this,
                partition
            ).extracted.absolutePath
        )


    // ============================================================
    // PREPARAÇÃO
    // ============================================================

    private fun confirmPrepare() {

        val message =
            if (
                state ==
                PartitionSessionState.IMPORTED
            ) {
                "A cópia local já existe.\n\n" +
                    "O GaspKernel vai extrair e preparar " +
                    "o conteúdo para edição."
            } else {
                "O GaspKernel criará uma cópia local da " +
                    "partição e preparará seu conteúdo para edição.\n\n" +
                    "Nenhuma gravação será feita na partição."
            }

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Preparar ${friendlyPartitionName()}"
            )
            .setMessage(
                message
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Preparar"
            ) { _, _ ->

                executePrepare()
            }
            .show()
    }


    private fun executePrepare() {

        prepareButton.isEnabled =
            false

        prepareButton.text =
            "Preparando..."

        Thread {

            var failure:
                String? = null

            /*
             * Etapa 1: importar.
             */
            if (
                state ==
                PartitionSessionState.DETECTED
            ) {

                val importResult =
                    PartitionImportManager.import(
                        this,
                        partition
                    )

                if (!importResult.success) {

                    failure =
                        importResult.message

                } else {

                    state =
                        PartitionSessionState.IMPORTED

                    PartitionStateStore.save(
                        this,
                        partition,
                        state
                    )
                }
            }

            /*
             * Etapa 2: extrair.
             */
            if (
                failure == null &&
                state ==
                PartitionSessionState.IMPORTED
            ) {

                val extraction =
                    when (
                        partition.type
                    ) {

                        PartitionType.BOOT ->
                            PartitionPipeline.extract(
                                this,
                                partition
                            )

                        PartitionType.DTB ->
                            DtbSessionExtractor.extract(
                                this,
                                partition
                            )

                        PartitionType.DTBO ->
                            DtboSessionExtractor.extract(
                                this,
                                partition
                            )
                    }

                if (
                    !extraction.success ||
                    extraction.newState == null
                ) {

                    failure =
                        extraction.message

                } else {

                    state =
                        extraction.newState

                    PartitionStateStore.save(
                        this,
                        partition,
                        state
                    )
                }
            }

            runOnUiThread {

                prepareButton.text =
                    "Preparar partição"

                render()

                if (failure != null) {

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            "Não foi possível preparar"
                        )
                        .setMessage(
                            failure
                        )
                        .setPositiveButton(
                            "OK",
                            null
                        )
                        .show()

                } else {

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            "Partição preparada"
                        )
                        .setMessage(
                            "${friendlyPartitionName()} está pronta para edição."
                        )
                        .setPositiveButton(
                            "Editar"
                        ) { _, _ ->

                            openDtbEditor()
                        }
                        .setNegativeButton(
                            "Depois",
                            null
                        )
                        .show()
                }
            }

        }.start()
    }


    // ============================================================
    // EDITOR
    // ============================================================

    private fun openDtbEditor() {

        val entries =
            editableDtbs()

        if (entries.isEmpty()) {

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Editor"
                )
                .setMessage(
                    "Nenhum Device Tree editável foi encontrado."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        /*
         * Abrir o editor invalida qualquer imagem verificada
         * anteriormente.
         *
         * Mesmo que o usuário apenas abra e feche sem editar,
         * ele terá que aplicar/verificar novamente.
         *
         * É conservador, mas seguro.
         */
        PartitionVerificationStore.invalidate(
            this,
            partition
        )

        state =
            PartitionSessionState.EXTRACTED

        PartitionStateStore.save(
            this,
            partition,
            state
        )

        render()

        startActivity(
            Intent(
                this,
                PartitionDtbEditorActivity::class.java
            )
        )
    }


    // ============================================================
    // APLICAR
    //
    // validar -> rebuild -> verify -> SHA seal
    // ============================================================

    private fun executeApply() {

        if (
            editableDtbs().isEmpty()
        ) {
            return
        }

        PartitionVerificationStore.invalidate(
            this,
            partition
        )

        applyButton.isEnabled =
            false

        applyButton.text =
            "Validando..."

        Thread {

            /*
             * 1. VALIDAR DTS
             */
            val validation =
                PartitionDtsValidator.validate(
                    this,
                    partition
                )

            if (!validation.success) {

                showApplyFailure(
                    "Validação falhou",
                    validation.message
                )

                return@Thread
            }

            state =
                PartitionSessionState.VALIDATED

            PartitionStateStore.save(
                this,
                partition,
                state
            )

            runOnUiThread {
                applyButton.text =
                    "Reconstruindo..."
            }


            /*
             * 2. REBUILD
             *
             * BOOT:
             * preservamos o backend que você já testou
             * fisicamente com sucesso.
             *
             * DTB/DTBO:
             * usam PartitionBuildManager.
             */
            val rebuildResult:
                Pair<Boolean, String> =

                when (
                    partition.type
                ) {

                    PartitionType.BOOT -> {

                        val result =
                            BootSessionRebuildManager.rebuild(
                                this,
                                partition
                            )

                        result.success to
                            result.message
                    }

                    PartitionType.DTB,
                    PartitionType.DTBO -> {

                        val result =
                            PartitionBuildManager.rebuild(
                                this,
                                partition
                            )

                        result.success to
                            result.message
                    }
                }

            if (!rebuildResult.first) {

                showApplyFailure(
                    "Reconstrução falhou",
                    rebuildResult.second
                )

                return@Thread
            }

            state =
                PartitionSessionState.BUILT

            PartitionStateStore.save(
                this,
                partition,
                state
            )

            runOnUiThread {
                applyButton.text =
                    "Verificando..."
            }


            /*
             * 3. ROUND-TRIP VERIFY
             */
            val verifyResult:
                Pair<Boolean, String> =

                when (
                    partition.type
                ) {

                    PartitionType.BOOT -> {

                        val result =
                            BootSessionVerifier.verify(
                                this,
                                partition
                            )

                        result.success to
                            result.message
                    }

                    PartitionType.DTB,
                    PartitionType.DTBO -> {

                        val result =
                            PartitionBuildManager.verify(
                                this,
                                partition
                            )

                        result.success to
                            result.message
                    }
                }

            if (!verifyResult.first) {

                showApplyFailure(
                    "Verificação falhou",
                    verifyResult.second
                )

                return@Thread
            }


            /*
             * 4. SELAR rebuilt.img COM SHA-256
             */
            val seal =
                PartitionVerificationStore.markVerified(
                    this,
                    partition
                )

            if (!seal.valid) {

                showApplyFailure(
                    "Verificação incompleta",
                    seal.message
                )

                return@Thread
            }

            state =
                PartitionSessionState.VERIFIED

            PartitionStateStore.save(
                this,
                partition,
                state
            )

            runOnUiThread {

                applyButton.text =
                    "Aplicar alterações"

                render()

                AlertDialog.Builder(
                    this
                )
                    .setTitle(
                        "Alterações prontas"
                    )
                    .setMessage(
                        buildString {

                            append(
                                "${friendlyPartitionName()} foi reconstruída e verificada com sucesso."
                            )

                            seal.expectedHash
                                ?.let {

                                    append(
                                        "\n\nSHA-256:\n$it"
                                    )
                                }

                            append(
                                "\n\nAgora você pode exportar ou instalar a imagem."
                            )
                        }
                    )
                    .setPositiveButton(
                        "OK",
                        null
                    )
                    .show()
            }

        }.start()
    }


    private fun showApplyFailure(
        title: String,
        message: String
    ) {

        runOnUiThread {

            applyButton.text =
                "Aplicar alterações"

            render()

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    title
                )
                .setMessage(
                    message
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()
        }
    }


    // ============================================================
    // EXPORTAR
    // ============================================================

    private fun requestExport() {

        val verification =
            PartitionVerificationStore.check(
                this,
                partition
            )

        if (!verification.valid) {

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Exportação bloqueada"
                )
                .setMessage(
                    verification.message
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        val workspace =
            PartitionWorkspace.get(
                this,
                partition
            )

        if (
            !workspace.rebuilt.exists() ||
            workspace.rebuilt.length() <= 0L
        ) {
            return
        }

        val fileName =
            "${partition.name}_modified.img"

        val intent =
            Intent(
                Intent.ACTION_CREATE_DOCUMENT
            )
                .addCategory(
                    Intent.CATEGORY_OPENABLE
                )
                .setType(
                    "application/octet-stream"
                )
                .putExtra(
                    Intent.EXTRA_TITLE,
                    fileName
                )

        startActivityForResult(
            intent,
            REQUEST_EXPORT_REBUILT
        )
    }


    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode !=
            REQUEST_EXPORT_REBUILT ||
            resultCode !=
            RESULT_OK
        ) {
            return
        }

        val uri =
            data?.data
                ?: return

        val file =
            PartitionWorkspace.get(
                this,
                partition
            ).rebuilt

        try {

            contentResolver
                .openOutputStream(
                    uri
                )
                ?.use { output ->

                    file.inputStream()
                        .use { input ->

                            input.copyTo(
                                output
                            )
                        }
                }
                ?: throw IllegalStateException(
                    "Não foi possível abrir o destino."
                )

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Imagem exportada"
                )
                .setMessage(
                    "${partition.name}_modified.img foi exportada com sucesso."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

        } catch (e: Exception) {

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Falha na exportação"
                )
                .setMessage(
                    e.message
                        ?: "Erro desconhecido."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()
        }
    }


    // ============================================================
    // FLASH
    // ============================================================

    private fun confirmFlash() {

        if (
            state !=
            PartitionSessionState.VERIFIED
        ) {
            return
        }

        val verification =
            PartitionVerificationStore.check(
                this,
                partition
            )

        if (!verification.valid) {

            state =
                PartitionSessionState.BUILT

            PartitionStateStore.save(
                this,
                partition,
                state
            )

            render()

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Instalação bloqueada"
                )
                .setMessage(
                    verification.message +
                        "\n\nAplique as alterações novamente."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        val workspace =
            PartitionWorkspace.get(
                this,
                partition
            )

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Instalar modificação?"
            )
            .setMessage(
                buildString {

                    append(
                        friendlyPartitionName()
                    )

                    append(
                        "\n${partition.name}"
                    )

                    append(
                        "\n${workspace.rebuilt.length()} bytes"
                    )

                    append(
                        "\n\nO GaspKernel criará um backup da partição atual antes da gravação."
                    )

                    append(
                        "\n\nDepois do flash, a partição será lida novamente e comparada por SHA-256."
                    )

                    append(
                        "\n\nO dispositivo NÃO será reiniciado automaticamente."
                    )
                }
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "INSTALAR"
            ) { _, _ ->

                executeFlash()
            }
            .show()
    }


    private fun executeFlash() {

        if (
            state !=
            PartitionSessionState.VERIFIED
        ) {
            return
        }

        flashButton.isEnabled =
            false

        flashButton.text =
            "Instalando..."

        Thread {

            val result =
                PartitionFlashManager.flash(
                    this,
                    partition
                )

            runOnUiThread {

                flashButton.text =
                    "Instalar modificação"

                render()

                AlertDialog.Builder(
                    this
                )
                    .setTitle(
                        if (result.success) {
                            "Instalação concluída"
                        } else {
                            "Instalação falhou"
                        }
                    )
                    .setMessage(
                        result.message
                    )
                    .setPositiveButton(
                        "OK",
                        null
                    )
                    .show()
            }

        }.start()
    }
}
