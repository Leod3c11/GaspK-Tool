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

class PartitionSessionActivity : LocalizedActivity() {

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
                getString(R.string.session_finish_preparation)
            } else {
                getString(R.string.session_prepare)
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

                    getString(R.string.session_edit_overlay)

                partition.type ==
                    PartitionType.DTBO ->

                    getString(R.string.session_edit_overlays_count, dtbs.size)

                dtbs.size == 1 ->

                    getString(R.string.session_edit_device_trees)

                else ->

                    getString(R.string.session_edit_count, dtbs.size)
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
            getString(R.string.session_apply_changes)


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
                getString(R.string.session_found_message)

            PartitionSessionState.IMPORTED ->
                getString(R.string.session_local_copy_message)

            PartitionSessionState.EXTRACTED ->
                buildString {

                    append(
                        getString(R.string.session_ready_edit)
                    )

                    if (dtbCount > 0) {
                        append(
                            "\n" + getString(R.string.session_dtb_count, dtbCount)
                        )
                    }
                }

            PartitionSessionState.DIRTY ->
                getString(R.string.session_changes_detected)

            PartitionSessionState.SAVED ->
                getString(R.string.session_changes_saved_apply)

            PartitionSessionState.VALIDATED ->
                getString(R.string.session_validated_rebuild_pending)

            PartitionSessionState.BUILT ->
                getString(R.string.session_rebuilt_verify_pending)

            PartitionSessionState.VERIFIED ->
                getString(R.string.session_verified_summary)
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
                getString(R.string.session_existing_copy_message)
            } else {
                getString(R.string.session_new_copy_message)
            }

        AlertDialog.Builder(
            this
        )
            .setTitle(
                getString(R.string.session_prepare_named, friendlyPartitionName())
            )
            .setMessage(
                message
            )
            .setNegativeButton(
                getString(R.string.action_cancel),
                null
            )
            .setPositiveButton(
                getString(R.string.session_prepare_action)
            ) { _, _ ->

                executePrepare()
            }
            .show()
    }


    private fun executePrepare() {

        prepareButton.isEnabled =
            false

        prepareButton.text =
            getString(R.string.session_preparing)

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
                    getString(R.string.session_prepare)

                render()

                if (failure != null) {

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            getString(R.string.session_prepare_failed_title)
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
                            getString(R.string.session_prepared_title)
                        )
                        .setMessage(
                            getString(R.string.session_prepared_message, friendlyPartitionName())
                        )
                        .setPositiveButton(
                            getString(R.string.session_edit_action)
                        ) { _, _ ->

                            openDtbEditor()
                        }
                        .setNegativeButton(
                            getString(R.string.session_later_action),
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
                    getString(R.string.session_editor_title)
                )
                .setMessage(
                    getString(R.string.session_no_editable_dtb)
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
            getString(R.string.session_validating)

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
                    getString(R.string.session_validation_failed),
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
                    getString(R.string.session_rebuilding)
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
                    getString(R.string.session_rebuild_failed),
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
                    getString(R.string.session_verifying)
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
                    getString(R.string.session_verification_failed),
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
                    getString(R.string.session_verification_incomplete),
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
                    getString(R.string.session_apply_changes)

                render()

                AlertDialog.Builder(
                    this
                )
                    .setTitle(
                        getString(R.string.session_changes_ready)
                    )
                    .setMessage(
                        buildString {

                            append(
                                getString(R.string.session_rebuild_verify_success, friendlyPartitionName())
                            )

                            seal.expectedHash
                                ?.let {

                                    append(
                                        "\n\nSHA-256:\n$it"
                                    )
                                }

                            append(
                                "\n\n" + getString(R.string.session_now_export_install)
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
                getString(R.string.session_apply_changes)

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
                    getString(R.string.session_export_blocked)
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
                    getString(R.string.session_export_destination_failed)
                )

            AlertDialog.Builder(
                this
            )
                .setTitle(
                    getString(R.string.session_image_exported)
                )
                .setMessage(
                    getString(R.string.session_image_exported_message, "${partition.name}_modified.img")
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
                    getString(R.string.session_export_failed)
                )
                .setMessage(
                    e.message
                        ?: getString(R.string.session_unknown_error_period)
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
                    getString(R.string.session_install_blocked)
                )
                .setMessage(
                    verification.message +
                        "\n\n" + getString(R.string.session_apply_again_notice)
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
                getString(R.string.session_install_modification_title)
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
                        "\n\n" + getString(R.string.session_install_backup_notice)
                    )

                    append(
                        "\n\n" + getString(R.string.session_install_verify_notice)
                    )

                    append(
                        "\n\n" + getString(R.string.session_install_reboot_notice)
                    )
                }
            )
            .setNegativeButton(
                getString(R.string.action_cancel),
                null
            )
            .setPositiveButton(
                getString(R.string.session_install_confirm)
            ) { _, _ ->

                executeFlash()
            }
            .show()
    }


    private fun rebootDevice() {

        Thread {

            val result =
                com.exynosbess.app.boot.ShellExecutor.root(
                    "reboot"
                )

            if (!result.success) {

                runOnUiThread {

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            getString(
                                R.string.session_reboot_failed
                            )
                        )
                        .setMessage(
                            buildString {

                                append(
                                    getString(
                                        R.string.session_reboot_failed_message
                                    )
                                )

                                if (
                                    result.output.isNotBlank()
                                ) {

                                    append(
                                        "\n\n"
                                    )

                                    append(
                                        result.output
                                    )
                                }

                                append(
                                    "\n\nExit code: "
                                )

                                append(
                                    result.exitCode
                                )
                            }
                        )
                        .setPositiveButton(
                            "OK",
                            null
                        )
                        .show()
                }
            }

        }.start()
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
            getString(R.string.session_installing)

        Thread {

            val result =
                PartitionFlashManager.flash(
                    this,
                    partition
                )

            runOnUiThread {

                flashButton.text =
                    getString(R.string.session_install_modification)

                render()

                if (result.success) {

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            getString(
                                R.string.session_install_success
                            )
                        )
                        .setMessage(
                            result.message +
                                "\n\n" +
                                getString(
                                    R.string.session_reboot_prompt
                                )
                        )
                        .setPositiveButton(
                            getString(
                                R.string.session_reboot_now
                            )
                        ) { _, _ ->

                            rebootDevice()
                        }
                        .setNegativeButton(
                            getString(
                                R.string.session_reboot_later
                            ),
                            null
                        )
                        .show()

                } else {

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            getString(
                                R.string.session_install_failed
                            )
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
            }

        }.start()
    }
}
