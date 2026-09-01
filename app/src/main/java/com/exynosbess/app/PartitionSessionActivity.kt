package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.exynosbess.app.partition.PartitionImportManager
import com.exynosbess.app.partition.PartitionPipeline
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionSessionState
import com.exynosbess.app.partition.PartitionStateStore
import com.exynosbess.app.partition.PartitionWorkspace

class PartitionSessionActivity : Activity() {

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

    private lateinit var importButton:
        Button

    private lateinit var extractButton:
        Button

    private lateinit var editButton:
        Button

    private lateinit var validateButton:
        Button

    private lateinit var rebuildButton:
        Button

    private lateinit var verifyButton:
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

        importButton =
            findViewById(
                R.id.partitionImportButton
            )

        extractButton =
            findViewById(
                R.id.partitionExtractButton
            )

        editButton =
            findViewById(
                R.id.partitionEditButton
            )

        validateButton =
            findViewById(
                R.id.partitionValidateButton
            )

        rebuildButton =
            findViewById(
                R.id.partitionRebuildButton
            )

        verifyButton =
            findViewById(
                R.id.partitionVerifyButton
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

        importButton.setOnClickListener {
            confirmImport()
        }

        extractButton.setOnClickListener {
            executeExtraction()
        }

        editButton.setOnClickListener {
            showPending(
                "Editor"
            )
        }

        validateButton.setOnClickListener {
            showPending(
                "Validação"
            )
        }

        rebuildButton.setOnClickListener {
            showPending(
                "Reconstrução"
            )
        }

        verifyButton.setOnClickListener {
            showPending(
                "Verificação"
            )
        }

        exportButton.setOnClickListener {
            showPending(
                "Exportação"
            )
        }

        flashButton.setOnClickListener {
            showPending(
                "Flash"
            )
        }
    }

    private fun restoreState() {

        state =
            PartitionStateStore.load(
                this,
                partition
            )
    }

    private fun render() {

        title.text =
            partition.name

        type.text =
            buildString {

                append(
                    partition.type.name
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
                        "\n→ ${partition.resolvedPath}"
                    )
                }
            }

        stateView.text =
            stateText()

        importButton.isEnabled =
            state ==
                PartitionSessionState.DETECTED

        extractButton.isEnabled =
            state in setOf(
                PartitionSessionState.IMPORTED,
                PartitionSessionState.EXTRACTED
            )

        editButton.isEnabled =
            state in setOf(
                PartitionSessionState.EXTRACTED,
                PartitionSessionState.DIRTY,
                PartitionSessionState.SAVED,
                PartitionSessionState.VALIDATED
            )

        validateButton.isEnabled =
            state ==
                PartitionSessionState.SAVED ||
                state ==
                PartitionSessionState.DIRTY

        rebuildButton.isEnabled =
            state ==
                PartitionSessionState.VALIDATED

        verifyButton.isEnabled =
            state ==
                PartitionSessionState.BUILT

        exportButton.isEnabled =
            state ==
                PartitionSessionState.VERIFIED

        flashButton.isEnabled =
            state ==
                PartitionSessionState.VERIFIED
    }

    private fun stateText():
        String {

        return when (state) {

            PartitionSessionState.DETECTED ->
                "● Detectada"

            PartitionSessionState.IMPORTED ->
                "● Importada\n○ Extraída\n○ Validada\n○ Reconstruída\n○ Verificada"

            PartitionSessionState.EXTRACTED ->
                "● Importada\n● Extraída\n○ Validada\n○ Reconstruída\n○ Verificada"

            PartitionSessionState.DIRTY ->
                "● Alterações não salvas"

            PartitionSessionState.SAVED ->
                "● Alterações salvas\n○ Validada\n○ Reconstruída\n○ Verificada"

            PartitionSessionState.VALIDATED ->
                "● Validada\n○ Reconstruída\n○ Verificada"

            PartitionSessionState.BUILT ->
                "● Reconstruída\n○ Verificada"

            PartitionSessionState.VERIFIED ->
                "● Verificada\n✓ Pronta para exportação ou flash"
        }
    }

    private fun confirmImport() {

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "Importar ${partition.name}"
            )
            .setMessage(
                "Será criada uma cópia local da partição.\n\n" +
                    "Nenhuma gravação será feita no dispositivo."
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Importar"
            ) {
                _,
                _ ->

                executeImport()
            }
            .show()
    }

    private fun executeImport() {

        importButton.isEnabled =
            false

        importButton.text =
            "Importando..."

        Thread {

            val result =
                PartitionImportManager.import(
                    this,
                    partition
                )

            runOnUiThread {

                importButton.text =
                    "Importar"

                if (result.success) {

                    state =
                        PartitionSessionState.IMPORTED

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
                            "Importação concluída"
                        )
                        .setMessage(
                            buildString {

                                append(
                                    result.message
                                )

                                result.sha256
                                    ?.let {
                                        append(
                                            "\n\nSHA-256:\n$it"
                                        )
                                    }
                            }
                        )
                        .setPositiveButton(
                            "OK",
                            null
                        )
                        .show()

                } else {

                    render()

                    AlertDialog.Builder(
                        this
                    )
                        .setTitle(
                            "Falha"
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

    private fun executeExtraction() {

        extractButton.isEnabled =
            false

        extractButton.text =
            "Extraindo..."

        Thread {

            val result =
                PartitionPipeline.extract(
                    this,
                    partition
                )

            runOnUiThread {

                extractButton.text =
                    "Extrair / descompactar"

                if (
                    result.success &&
                    result.newState != null
                ) {

                    state =
                        result.newState

                    PartitionStateStore.save(
                        this,
                        partition,
                        state
                    )

                    render()

                } else {

                    render()
                }

                AlertDialog.Builder(
                    this
                )
                    .setTitle(
                        if (result.success) {
                            "Extração concluída"
                        } else {
                            "Extração"
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

    private fun showPending(
        operation: String
    ) {

        AlertDialog.Builder(
            this
        )
            .setTitle(
                operation
            )
            .setMessage(
                "Backend ainda não conectado para ${partition.type.name}."
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }
}
