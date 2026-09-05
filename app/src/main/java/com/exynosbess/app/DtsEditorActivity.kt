package com.exynosbess.app

import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionStateStore
import com.exynosbess.app.partition.PartitionSessionState

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.editor.CodeEditorView
import com.exynosbess.app.editor.DtcErrorParser
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.math.BigInteger

class DtsEditorActivity : LocalizedActivity() {

    private lateinit var editorTitle: TextView
    private lateinit var editorPath: TextView
    private lateinit var dtsEditor: CodeEditorView

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var searchNextButton: Button
    private lateinit var searchStatus: TextView

    private lateinit var saveDtsButton: Button
    private lateinit var validateDtsButton: Button
    private lateinit var editorStatus: TextView

    private lateinit var numericBaseButton: Button

    private lateinit var editorUndoButton: Button
    private lateinit var editorRedoButton: Button
    private lateinit var editorGotoButton: Button
    private lateinit var editorFontDownButton: Button
    private lateinit var editorFontUpButton: Button
    private lateinit var editorPositionStatus: TextView
    private lateinit var editorDirtyStatus: TextView

    private var lastSavedContent =
        ""

    private var decimalMode = false

    private var loadingDts = false
    private var validatedContent: String? = null

    private var dtbIndex: Int = 0
    private lateinit var dtsFile: File

    private var sessionMode =
        false

    private var searchResults =
        emptyList<Int>()

    private var currentSearchIndex =
        -1

    private var lastSearchQuery =
        ""



    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_dts_editor
        )

        editorTitle =
            findViewById(
                R.id.editorTitle
            )

        editorPath =
            findViewById(
                R.id.editorPath
            )

        dtsEditor =
            findViewById(
                R.id.dtsEditor
            )

        searchInput =
            findViewById(
                R.id.searchInput
            )

        searchButton =
            findViewById(
                R.id.searchButton
            )

        searchNextButton =
            findViewById(
                R.id.searchNextButton
            )

        searchStatus =
            findViewById(
                R.id.searchStatus
            )

        saveDtsButton =
            findViewById(
                R.id.saveDtsButton
            )

        validateDtsButton =
            findViewById(
                R.id.validateDtsButton
            )

        editorStatus =
            findViewById(
                R.id.editorStatus
            )

        numericBaseButton =
            findViewById(
                R.id.numericBaseButton
            )


        editorUndoButton =
            findViewById(
                R.id.EditorUndoButton
            )

        editorRedoButton =
            findViewById(
                R.id.EditorRedoButton
            )

        editorGotoButton =
            findViewById(
                R.id.EditorGotoButton
            )

        editorFontDownButton =
            findViewById(
                R.id.EditorFontDownButton
            )

        editorFontUpButton =
            findViewById(
                R.id.EditorFontUpButton
            )

        editorPositionStatus =
            findViewById(
                R.id.EditorPositionStatus
            )

        editorDirtyStatus =
            findViewById(
                R.id.EditorDirtyStatus
            )


        dtbIndex =
            intent.getIntExtra(
                "dtb_index",
                0
            )


        val workspacePath =
            intent.getStringExtra(
                "workspace_path"
            )

        sessionMode =
            !workspacePath.isNullOrBlank()


        val workspace =
            if (
                workspacePath.isNullOrBlank()
            ) {

                File(
                    filesDir,
                    "workspace"
                )

            } else {

                File(
                    workspacePath
                )
            }


        dtsFile =
            File(
                workspace,
                "dtbs/dtb_${dtbIndex}.dts"
            )


        editorTitle.text =
            "Editor DTS $dtbIndex"

        editorPath.text =
            dtsFile.absolutePath


        loadDts()


        searchButton.setOnClickListener {
            startSearch()
        }

        searchNextButton.setOnClickListener {
            showNextResult()
        }


        saveDtsButton.setOnClickListener {
            saveDts()
        }


        validateDtsButton.setOnClickListener {
            validateDts()
        }


        numericBaseButton.setOnClickListener {
            toggleNumericBase()
        }


        editorUndoButton.setOnClickListener {
            dtsEditor.undoEdit()
        }

        editorRedoButton.setOnClickListener {
            dtsEditor.redoEdit()
        }

        editorGotoButton.setOnClickListener {
            showGoToLineDialog()
        }

        editorFontDownButton.setOnClickListener {
            dtsEditor.decreaseEditorTextSize()
        }

        editorFontUpButton.setOnClickListener {
            dtsEditor.increaseEditorTextSize()
        }

        dtsEditor.onCursorPositionChanged = {
            updateEditorMeta()
        }

        dtsEditor.onHistoryChanged = {
            updateEditorTools()
        }

        updateEditorTools()
        updateEditorMeta()


        dtsEditor.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit


                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    if (!loadingDts) {
                        invalidateBootFlowIfNeeded()
                        updateEditorMeta()
                    }
                }


                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }


    private fun updateEditorTools() {

        editorUndoButton.isEnabled =
            dtsEditor.canUndoEdit()

        editorRedoButton.isEnabled =
            dtsEditor.canRedoEdit()
    }


    private fun updateEditorMeta() {

        if (
            !::dtsEditor.isInitialized ||
            !::editorPositionStatus.isInitialized
        ) {
            return
        }

        val position =
            dtsEditor.currentLineColumn()

        editorPositionStatus.text =
            getString(
                R.string.editor_position,
                position.first,
                position.second
            )

        editorDirtyStatus.text =
            if (
                hasUnsavedDts()
            ) {
                getString(
                    R.string.editor_modified
                )
            } else {
                getString(
                    R.string.editor_saved
                )
            }
    }


    private fun hasUnsavedDts():
        Boolean {

        if (
            !::dtsEditor.isInitialized
        ) {
            return false
        }

        return dtsEditor.text
            .toString() !=
            lastSavedContent
    }


    private fun showGoToLineDialog() {

        val input =
            EditText(this).apply {

                inputType =
                    InputType.TYPE_CLASS_NUMBER

                hint =
                    getString(
                        R.string.editor_line_number
                    )
            }

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.editor_goto_line_title
                )
            )
            .setView(input)
            .setNegativeButton(
                getString(
                    R.string.action_cancel
                ),
                null
            )
            .setPositiveButton(
                getString(
                    R.string.action_go
                )
            ) {
                _,
                _ ->

                val line =
                    input.text
                        .toString()
                        .toIntOrNull()

                if (
                    line == null ||
                    !dtsEditor.goToLine(
                        line
                    )
                ) {

                    editorStatus.text =
                        getString(
                            R.string.editor_line_not_found
                        )

                } else {

                    updateEditorMeta()
                }
            }
            .show()
    }


    override fun onBackPressed() {

        if (
            !hasUnsavedDts()
        ) {

            super.onBackPressed()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.editor_unsaved_title
                )
            )
            .setMessage(
                getString(
                    R.string.editor_unsaved_message
                )
            )
            .setNegativeButton(
                getString(
                    R.string.action_cancel
                ),
                null
            )
            .setNeutralButton(
                getString(
                    R.string.editor_discard
                )
            ) {
                _,
                _ ->

                super.onBackPressed()
            }
            .setPositiveButton(
                getString(
                    R.string.action_save
                )
            ) {
                _,
                _ ->

                saveDts()

                if (
                    !hasUnsavedDts()
                ) {
                    super.onBackPressed()
                }
            }
            .show()
    }


    private fun startSearch() {

        val query =
            searchInput.text
                .toString()
                .trim()

        if (query.isEmpty()) {

            searchResults =
                emptyList()

            currentSearchIndex =
                -1

            searchNextButton.isEnabled =
                false

            searchStatus.text =
                getString(R.string.search_type_query)

            return
        }

        val content =
            dtsEditor.text
                .toString()

        val lowerContent =
            content.lowercase()

        val lowerQuery =
            query.lowercase()

        val results =
            mutableListOf<Int>()

        var startIndex =
            0

        while (startIndex < lowerContent.length) {

            val found =
                lowerContent.indexOf(
                    lowerQuery,
                    startIndex
                )

            if (found == -1) {
                break
            }

            results.add(found)

            startIndex =
                found +
                maxOf(
                    1,
                    lowerQuery.length
                )
        }

        searchResults =
            results

        lastSearchQuery =
            query

        if (searchResults.isEmpty()) {

            currentSearchIndex =
                -1

            searchNextButton.isEnabled =
                false

            searchStatus.text =
                getString(R.string.search_no_occurrence)

            return
        }

        currentSearchIndex =
            0

        searchNextButton.isEnabled =
            searchResults.size > 1

        selectCurrentResult()
    }

    private fun showNextResult() {

        val query =
            searchInput.text
                .toString()
                .trim()

        /*
         * Se o usuário alterou a palavra,
         * refazer a busca.
         */
        if (
            query != lastSearchQuery ||
            searchResults.isEmpty()
        ) {
            startSearch()
            return
        }

        currentSearchIndex++

        if (
            currentSearchIndex >=
            searchResults.size
        ) {
            currentSearchIndex = 0
        }

        selectCurrentResult()
    }

    private fun selectCurrentResult() {

        if (
            currentSearchIndex < 0 ||
            currentSearchIndex >=
            searchResults.size
        ) {
            return
        }

        val query =
            lastSearchQuery

        val start =
            searchResults[
                currentSearchIndex
            ]

        val end =
            start +
            query.length

        try {

            dtsEditor.requestFocus()

            dtsEditor.setSelection(
                start,
                minOf(
                    end,
                    dtsEditor.text.length
                )
            )

            searchStatus.text =
                getString(
                    R.string.search_result,
                    currentSearchIndex + 1,
                    searchResults.size
                )

        } catch (e: Exception) {

            searchStatus.text =
                getString(R.string.search_select_failed)
        }
    }

    private fun loadDts() {

        if (!dtsFile.exists()) {

            editorStatus.text =
                getString(R.string.dts_not_found)

            saveDtsButton.isEnabled =
                false

            validateDtsButton.isEnabled =
                false

            searchButton.isEnabled =
                false

            searchNextButton.isEnabled =
                false

            return
        }

        try {

            val content =
                dtsFile.readText()

            loadingDts = true
            dtsEditor.setText(content)
            loadingDts = false

            lastSavedContent =
                content

            dtsEditor.clearEditHistory()

            updateEditorTools()
            updateEditorMeta()

            editorStatus.text =
                getString(R.string.dts_loaded)

        } catch (e: Exception) {

            editorStatus.text =
                getString(R.string.dts_load_failed, e.message ?: "")
        }
    }



    private fun saveDts() {

        ensureHexModeBeforePersistence()

        try {

            dtsFile.writeText(
                dtsEditor.text.toString()
            )

            lastSavedContent =
                dtsEditor.text.toString()

            updateEditorMeta()

            validatedContent =
                null

            editorStatus.text =
                getString(R.string.dts_saved_needs_validation)


            if (sessionMode) {

                PartitionSelection.get()
                    ?.let { selected ->

                        PartitionStateStore.save(
                            this,
                            selected,
                            PartitionSessionState.SAVED
                        )
                    }
            }

        } catch (
            e: Exception
        ) {

            editorStatus.text =
                getString(R.string.dts_save_failed, e.message ?: "")
        }
    }


    private fun validateDts() {

        ensureHexModeBeforePersistence()

        editorStatus.text =
            getString(R.string.dts_saving_validating)

        saveDtsButton.isEnabled =
            false

        validateDtsButton.isEnabled =
            false


        try {

            dtsFile.writeText(
                dtsEditor.text.toString()
            )

            lastSavedContent =
                dtsEditor.text.toString()

            updateEditorMeta()

        } catch (
            e: Exception
        ) {

            editorStatus.text =
                getString(R.string.dts_presave_failed, e.message ?: "")

            saveDtsButton.isEnabled =
                true

            validateDtsButton.isEnabled =
                true

            return
        }


        Thread {

            val tool =
                ToolManager.installDtc(
                    this
                )

            if (
                !tool.success ||
                tool.path == null
            ) {

                runOnUiThread {

                    editorStatus.text =
                        getString(R.string.dts_prepare_dtc_failed, tool.message)

                    saveDtsButton.isEnabled =
                        true

                    validateDtsButton.isEnabled =
                        true
                }

                return@Thread
            }


            val validatedDtb =
                File(
                    dtsFile.parentFile,
                    "validated_dtb_${dtbIndex}.dtb"
                )


            if (validatedDtb.exists()) {
                validatedDtb.delete()
            }


            val command =
                "'${tool.path}' " +
                    "-I dts " +
                    "-O dtb " +
                    "-o '${validatedDtb.absolutePath}' " +
                    "'${dtsFile.absolutePath}'"


            val result =
                ShellExecutor.root(
                    command
                )


            if (
                result.success &&
                validatedDtb.exists() &&
                validatedDtb.length() > 0L
            ) {

                val appUid =
                    Process.myUid()

                ShellExecutor.root(
                    "chown $appUid:$appUid '${validatedDtb.absolutePath}' && " +
                        "chmod 600 '${validatedDtb.absolutePath}'"
                )
            }


            runOnUiThread {

                saveDtsButton.isEnabled =
                    true

                validateDtsButton.isEnabled =
                    true


                if (
                    result.success &&
                    validatedDtb.exists() &&
                    validatedDtb.length() > 0L
                ) {

                    validatedContent =
                        dtsEditor.text
                            .toString()

                    editorStatus.text =
                        getString(R.string.dts_saved_valid)


                    if (sessionMode) {

                        PartitionSelection.get()
                            ?.let { selected ->

                                PartitionStateStore.save(
                                    this,
                                    selected,
                                    PartitionSessionState.VALIDATED
                                )
                            }
                    }

                } else {

                    validatedContent =
                        null


                    if (validatedDtb.exists()) {
                        validatedDtb.delete()
                    }


                    val error =
                        result.output
                            .lineSequence()
                            .firstOrNull {
                                it.isNotBlank()
                            }
                            ?.take(
                                220
                            )
                            ?: "Erro de compilação"


                    val location =
                        DtcErrorParser.findLocation(
                            result.output
                        )

                    if (
                        location != null &&
                        dtsEditor.goToLine(
                            location.line,
                            location.column
                        )
                    ) {

                        editorStatus.text =
                            getString(
                                R.string.dts_invalid_line,
                                location.line,
                                location.column,
                                error
                            )

                    } else {

                        editorStatus.text =
                            getString(
                                R.string.dts_invalid,
                                error
                            )
                    }
                }
            }

        }.start()
    }


    private fun invalidateBootFlowIfNeeded() {

        val previous =
            validatedContent
                ?: return


        if (
            dtsEditor.text.toString() ==
            previous
        ) {
            return
        }


        validatedContent =
            null

        editorStatus.text =
            getString(R.string.dts_unvalidated_changes)


        if (sessionMode) {

            PartitionSelection.get()
                ?.let { selected ->

                    PartitionStateStore.save(
                        this,
                        selected,
                        PartitionSessionState.DIRTY
                    )
                }
        }
    }

    private fun toggleNumericBase() {

        val current =
            dtsEditor.text.toString()

        val cursor =
            dtsEditor.selectionStart
                .coerceAtLeast(0)

        val converted =
            if (decimalMode) {
                convertCellsDecimalToHex(current)
            } else {
                convertCellsHexToDecimal(current)
            }

        loadingDts = true

        dtsEditor.replaceTextWithoutHistory(
            converted,
            minOf(
                cursor,
                converted.length
            )
        )

        loadingDts = false

        decimalMode =
            !decimalMode

        numericBaseButton.text =
            if (decimalMode)
                "HEX"
            else
                "DEC"

        editorStatus.text =
            if (decimalMode)
                getString(R.string.mode_decimal)
            else
                getString(R.string.mode_hex)
    }


    private fun ensureHexModeBeforePersistence() {

        if (!decimalMode) {
            return
        }

        val current =
            dtsEditor.text.toString()

        val converted =
            convertCellsDecimalToHex(
                current
            )

        val cursor =
            dtsEditor.selectionStart
                .coerceAtLeast(0)

        loadingDts =
            true

        dtsEditor.replaceTextWithoutHistory(
            converted,
            minOf(
                cursor,
                converted.length
            )
        )

        loadingDts =
            false

        decimalMode =
            false

        numericBaseButton.text =
            "DEC"
    }


    private fun convertCellsHexToDecimal(
        source: String
    ): String {

        val cellRegex =
            Regex("""<([^<>]*)>""")

        val hexRegex =
            Regex("""0[xX]([0-9a-fA-F]+)""")

        return cellRegex.replace(source) { cell ->

            val content =
                cell.groupValues[1]

            val converted =
                hexRegex.replace(content) { number ->

                    try {

                        BigInteger(
                            number.groupValues[1],
                            16
                        ).toString(10)

                    } catch (_: Exception) {

                        number.value
                    }
                }

            "<$converted>"
        }
    }


    private fun convertCellsDecimalToHex(
        source: String
    ): String {

        val cellRegex =
            Regex("""<([^<>]*)>""")

        val decimalRegex =
            Regex(
                """(?<![A-Za-z0-9_])([0-9]+)(?![A-Za-z0-9_])"""
            )

        return cellRegex.replace(source) { cell ->

            val content =
                cell.groupValues[1]

            val converted =
                decimalRegex.replace(content) { number ->

                    try {

                        val value =
                            BigInteger(
                                number.groupValues[1],
                                10
                            )

                        "0x" +
                            value.toString(16)

                    } catch (_: Exception) {

                        number.value
                    }
                }

            "<$converted>"
        }
    }

}
