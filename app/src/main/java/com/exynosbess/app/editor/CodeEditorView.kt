package com.exynosbess.app.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.EditText
import com.exynosbess.app.R
import java.util.ArrayDeque

class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(
    context,
    attrs,
    defStyleAttr
) {

    private data class EditOperation(
        val start: Int,
        val removed: String,
        val added: String
    )

    private val undoStack =
        ArrayDeque<EditOperation>()

    private val redoStack =
        ArrayDeque<EditOperation>()

    private var internalChange =
        false

    private var changeStart =
        0

    private var removedText =
        ""

    private var addedText =
        ""

    private val gutterWidth =
        dp(52)

    private val lineNumberPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                context.getColor(
                    R.color.exynos_text_secondary
                )

            textSize =
                sp(9f)

            typeface =
                Typeface.MONOSPACE

            textAlign =
                Paint.Align.RIGHT
        }

    private val separatorPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                context.getColor(
                    R.color.exynos_border
                )

            strokeWidth =
                dp(1).toFloat()
        }

    var onCursorPositionChanged:
        (() -> Unit)? = null

    var onHistoryChanged:
        (() -> Unit)? = null

    init {

        typeface =
            Typeface.MONOSPACE

        setHorizontallyScrolling(
            true
        )

        setPadding(
            gutterWidth,
            paddingTop,
            paddingRight,
            paddingBottom
        )

        addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {

                    if (internalChange) {
                        return
                    }

                    changeStart =
                        start

                    removedText =
                        s
                            ?.subSequence(
                                start,
                                start + count
                            )
                            ?.toString()
                            ?: ""
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    if (internalChange) {
                        return
                    }

                    addedText =
                        s
                            ?.subSequence(
                                start,
                                start + count
                            )
                            ?.toString()
                            ?: ""
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {

                    if (internalChange) {
                        invalidate()
                        return
                    }

                    if (
                        removedText !=
                        addedText
                    ) {

                        undoStack.addLast(
                            EditOperation(
                                start =
                                    changeStart,
                                removed =
                                    removedText,
                                added =
                                    addedText
                            )
                        )

                        while (
                            undoStack.size >
                            MAX_HISTORY
                        ) {
                            undoStack.removeFirst()
                        }

                        redoStack.clear()

                        onHistoryChanged
                            ?.invoke()
                    }

                    invalidate()
                }
            }
        )
    }

    fun replaceTextWithoutHistory(
        value: String,
        selection: Int = 0
    ) {

        internalChange =
            true

        try {

            setText(
                value
            )

            setSelection(
                selection.coerceIn(
                    0,
                    text.length
                )
            )

        } finally {

            internalChange =
                false
        }

        invalidate()

        onCursorPositionChanged
            ?.invoke()
    }


    fun undoEdit() {

        val operation =
            undoStack.pollLast()
                ?: return

        internalChange =
            true

        try {

            val editable =
                text

            val end =
                (
                    operation.start +
                    operation.added.length
                )
                    .coerceAtMost(
                        editable.length
                    )

            editable.replace(
                operation.start,
                end,
                operation.removed
            )

            setSelection(
                (
                    operation.start +
                    operation.removed.length
                )
                    .coerceAtMost(
                        editable.length
                    )
            )

            redoStack.addLast(
                operation
            )

        } finally {

            internalChange =
                false
        }

        onHistoryChanged
            ?.invoke()

        onCursorPositionChanged
            ?.invoke()

        invalidate()
    }

    fun redoEdit() {

        val operation =
            redoStack.pollLast()
                ?: return

        internalChange =
            true

        try {

            val editable =
                text

            val end =
                (
                    operation.start +
                    operation.removed.length
                )
                    .coerceAtMost(
                        editable.length
                    )

            editable.replace(
                operation.start,
                end,
                operation.added
            )

            setSelection(
                (
                    operation.start +
                    operation.added.length
                )
                    .coerceAtMost(
                        editable.length
                    )
            )

            undoStack.addLast(
                operation
            )

        } finally {

            internalChange =
                false
        }

        onHistoryChanged
            ?.invoke()

        onCursorPositionChanged
            ?.invoke()

        invalidate()
    }

    fun canUndoEdit():
        Boolean =
        undoStack.isNotEmpty()

    fun canRedoEdit():
        Boolean =
        redoStack.isNotEmpty()

    fun clearEditHistory() {

        undoStack.clear()
        redoStack.clear()

        onHistoryChanged
            ?.invoke()
    }

    fun currentLineColumn():
        Pair<Int, Int> {

        val position =
            selectionStart
                .coerceIn(
                    0,
                    text.length
                )

        val source =
            text.toString()

        var line =
            1

        var lastBreak =
            -1

        var index =
            0

        while (
            index <
            position
        ) {

            if (
                source[index] ==
                '\n'
            ) {

                line++
                lastBreak =
                    index
            }

            index++
        }

        val column =
            position -
                lastBreak

        return Pair(
            line,
            column
        )
    }

    fun goToLine(
        line: Int
    ): Boolean {

        if (line < 1) {
            return false
        }

        val source =
            text.toString()

        if (line == 1) {

            requestFocus()
            setSelection(0)

            return true
        }

        var currentLine =
            1

        source.forEachIndexed {
            index,
            char ->

            if (
                char ==
                '\n'
            ) {

                currentLine++

                if (
                    currentLine ==
                    line
                ) {

                    requestFocus()

                    setSelection(
                        (
                            index + 1
                        )
                            .coerceAtMost(
                                text.length
                            )
                    )

                    return true
                }
            }
        }

        return false
    }

    fun increaseEditorTextSize() {

        val current =
            textSize /
                resources
                    .displayMetrics
                    .scaledDensity

        setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (
                current + 1f
            )
                .coerceAtMost(
                    22f
                )
        )

        invalidate()
    }

    fun decreaseEditorTextSize() {

        val current =
            textSize /
                resources
                    .displayMetrics
                    .scaledDensity

        setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (
                current - 1f
            )
                .coerceAtLeast(
                    8f
                )
        )

        invalidate()
    }

    override fun onSelectionChanged(
        selStart: Int,
        selEnd: Int
    ) {

        super.onSelectionChanged(
            selStart,
            selEnd
        )

        onCursorPositionChanged
            ?.invoke()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        val textLayout =
            layout

        if (textLayout != null) {

            val first =
                textLayout
                    .getLineForVertical(
                        scrollY
                    )
                    .coerceAtLeast(0)

            val last =
                textLayout
                    .getLineForVertical(
                        scrollY +
                            height
                    )
                    .coerceAtMost(
                        textLayout.lineCount - 1
                    )

            val x =
                (
                    gutterWidth -
                    dp(8)
                )
                    .toFloat()

            for (
                line in
                first..last
            ) {

                val y =
                    totalPaddingTop +
                        textLayout
                            .getLineBaseline(
                                line
                            ) -
                        scrollY

                canvas.drawText(
                    (
                        line + 1
                    ).toString(),
                    x,
                    y.toFloat(),
                    lineNumberPaint
                )
            }

            canvas.drawLine(
                (
                    gutterWidth -
                    dp(3)
                )
                    .toFloat(),
                0f,
                (
                    gutterWidth -
                    dp(3)
                )
                    .toFloat(),
                height.toFloat(),
                separatorPaint
            )
        }

        super.onDraw(
            canvas
        )
    }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources
                    .displayMetrics
                    .density
        ).toInt()

    private fun sp(
        value: Float
    ): Float =
        value *
            resources
                .displayMetrics
                .scaledDensity

    companion object {

        private const val MAX_HISTORY =
            100
    }
}
