package com.exynosbess.app.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
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

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val highlightRunnable =
        Runnable {
            applySyntaxHighlight()
        }

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
                        scheduleSyntaxHighlight()

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
                    scheduleSyntaxHighlight()
                }
            }
        )
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

            val start =
                operation.start
                    .coerceIn(
                        0,
                        editable.length
                    )

            val end =
                (
                    start +
                    operation.added.length
                )
                    .coerceAtMost(
                        editable.length
                    )

            editable.replace(
                start,
                end,
                operation.removed
            )

            setSelection(
                (
                    start +
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

        scheduleSyntaxHighlight()
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

            val start =
                operation.start
                    .coerceIn(
                        0,
                        editable.length
                    )

            val end =
                (
                    start +
                    operation.removed.length
                )
                    .coerceAtMost(
                        editable.length
                    )

            editable.replace(
                start,
                end,
                operation.added
            )

            setSelection(
                (
                    start +
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

        scheduleSyntaxHighlight()
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


    fun replaceTextWithoutHistory(
        value: String,
        selection: Int = 0,
        clearHistory: Boolean = true
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

        if (clearHistory) {
            clearEditHistory()
        }

        scheduleSyntaxHighlight()

        onCursorPositionChanged
            ?.invoke()

        invalidate()
    }


    fun currentLineColumn():
        Pair<Int, Int> {

        val position =
            selectionStart
                .coerceIn(
                    0,
                    text.length
                )

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
                text[index] ==
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
        line: Int,
        column: Int = 1
    ): Boolean {

        if (line < 1) {
            return false
        }

        val source =
            text

        var currentLine =
            1

        var lineStart =
            0

        if (line > 1) {

            var index =
                0

            var found =
                false

            while (
                index <
                source.length
            ) {

                if (
                    source[index] ==
                    '\n'
                ) {

                    currentLine++

                    if (
                        currentLine ==
                        line
                    ) {

                        lineStart =
                            index + 1

                        found =
                            true

                        break
                    }
                }

                index++
            }

            if (!found) {
                return false
            }
        }

        var lineEnd =
            source.length

        var index =
            lineStart

        while (
            index <
            source.length
        ) {

            if (
                source[index] ==
                '\n'
            ) {

                lineEnd =
                    index

                break
            }

            index++
        }

        val offset =
            (
                lineStart +
                (
                    column - 1
                ).coerceAtLeast(0)
            )
                .coerceAtMost(
                    lineEnd
                )

        requestFocus()

        setSelection(
            offset
        )

        post {
            val layout =
                layout
                    ?: return@post

            val visualLine =
                layout.getLineForOffset(
                    offset
                )

            val y =
                layout.getLineTop(
                    visualLine
                )

            scrollTo(
                scrollX,
                y.coerceAtLeast(0)
            )
        }

        return true
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

        scheduleSyntaxHighlight()
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

        scheduleSyntaxHighlight()
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

        updateMatchingPair()

        onCursorPositionChanged
            ?.invoke()
    }


    override fun onScrollChanged(
        horiz: Int,
        vert: Int,
        oldHoriz: Int,
        oldVert: Int
    ) {

        super.onScrollChanged(
            horiz,
            vert,
            oldHoriz,
            oldVert
        )

        if (
            vert != oldVert
        ) {
            scheduleSyntaxHighlight()
        }
    }


    private fun scheduleSyntaxHighlight() {

        handler.removeCallbacks(
            highlightRunnable
        )

        handler.postDelayed(
            highlightRunnable,
            HIGHLIGHT_DELAY
        )
    }


    private fun applySyntaxHighlight() {

        val editable =
            text
                ?: return

        if (
            editable.isEmpty()
        ) {
            return
        }

        internalChange =
            true

        try {

            val range =
                highlightRange()

            val start =
                range.first

            val end =
                range.last + 1

            editable
                .getSpans(
                    start,
                    end,
                    ForegroundColorSpan::class.java
                )
                .forEach {
                    editable.removeSpan(it)
                }

            if (
                start >= end ||
                start !in
                0..editable.length ||
                end !in
                0..editable.length
            ) {
                return
            }

            val source =
                editable
                    .subSequence(
                        start,
                        end
                    )
                    .toString()

            applyMatches(
                editable,
                source,
                start,
                Regex(
                    """(?m)^\s*(/[A-Za-z0-9,_+\-]+/)"""
                ),
                COLOR_DIRECTIVE,
                1
            )

            applyMatches(
                editable,
                source,
                start,
                Regex(
                    """(?m)^\s*([A-Za-z_#][A-Za-z0-9,._+\-#@]*)\s*(?==)"""
                ),
                COLOR_PROPERTY,
                1
            )

            applyMatches(
                editable,
                source,
                start,
                Regex(
                    """&[A-Za-z_][A-Za-z0-9_]*"""
                ),
                COLOR_REFERENCE
            )

            applyMatches(
                editable,
                source,
                start,
                Regex(
                    """\b0[xX][0-9a-fA-F]+\b|\b[0-9]+\b"""
                ),
                COLOR_NUMBER
            )

            applyMatches(
                editable,
                source,
                start,
                Regex(
                    """"(?:\\.|[^"\\])*""""
                ),
                COLOR_STRING
            )

            applyMatches(
                editable,
                source,
                start,
                Regex(
                    """(?s)/\*.*?\*/|//[^\r\n]*"""
                ),
                COLOR_COMMENT
            )

        } finally {

            internalChange =
                false
        }

        updateMatchingPair()
    }


    private fun highlightRange():
        IntRange {

        val length =
            text.length

        if (
            length <=
            MAX_FULL_HIGHLIGHT
        ) {

            return 0 until length
        }

        val currentLayout =
            layout

        if (
            currentLayout == null ||
            currentLayout.lineCount <= 0
        ) {

            return 0 until minOf(
                length,
                MAX_FULL_HIGHLIGHT
            )
        }

        val firstLine =
            currentLayout
                .getLineForVertical(
                    scrollY
                )
                .coerceAtLeast(0)

        val lastLine =
            currentLayout
                .getLineForVertical(
                    scrollY +
                        height
                )
                .coerceAtMost(
                    currentLayout.lineCount - 1
                )

        val start =
            (
                currentLayout
                    .getLineStart(
                        firstLine
                    ) -
                    EXTRA_HIGHLIGHT_CHARS
            )
                .coerceAtLeast(0)

        val end =
            (
                currentLayout
                    .getLineEnd(
                        lastLine
                    ) +
                    EXTRA_HIGHLIGHT_CHARS
            )
                .coerceAtMost(
                    length
                )

        return start until end
    }


    private fun applyMatches(
        editable: Editable,
        source: String,
        baseOffset: Int,
        regex: Regex,
        color: Int,
        group: Int = 0
    ) {

        regex
            .findAll(
                source
            )
            .forEach { match ->

                val range =
                    match.groups[group]
                        ?.range
                        ?: return@forEach

                val start =
                    baseOffset +
                        range.first

                val end =
                    baseOffset +
                        range.last +
                        1

                if (
                    start >= 0 &&
                    end <= editable.length &&
                    start < end
                ) {

                    editable.setSpan(
                        ForegroundColorSpan(
                            color
                        ),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
    }


    private fun updateMatchingPair() {

        val editable =
            text
                ?: return

        editable
            .getSpans(
                0,
                editable.length,
                BackgroundColorSpan::class.java
            )
            .forEach {
                editable.removeSpan(it)
            }

        if (
            editable.isEmpty()
        ) {
            return
        }

        val cursor =
            selectionStart
                .coerceIn(
                    0,
                    editable.length
                )

        val candidate =
            when {

                cursor <
                    editable.length &&
                    isPairCharacter(
                        editable[cursor]
                    ) ->
                    cursor

                cursor > 0 &&
                    isPairCharacter(
                        editable[cursor - 1]
                    ) ->
                    cursor - 1

                else ->
                    -1
            }

        if (
            candidate < 0
        ) {
            return
        }

        val match =
            findMatchingCharacter(
                editable,
                candidate
            )

        if (
            match < 0
        ) {
            return
        }

        val color =
            Color.argb(
                90,
                72,
                170,
                255
            )

        editable.setSpan(
            BackgroundColorSpan(
                color
            ),
            candidate,
            candidate + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        editable.setSpan(
            BackgroundColorSpan(
                color
            ),
            match,
            match + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }


    private fun isPairCharacter(
        char: Char
    ): Boolean {

        return char in
            charArrayOf(
                '{',
                '}',
                '(',
                ')',
                '[',
                ']',
                '<',
                '>'
            )
    }


    private fun findMatchingCharacter(
        source: CharSequence,
        position: Int
    ): Int {

        val char =
            source[position]

        val opening =
            when (char) {

                '{' -> '{'
                '}' -> '{'

                '(' -> '('
                ')' -> '('

                '[' -> '['
                ']' -> '['

                '<' -> '<'
                '>' -> '<'

                else ->
                    return -1
            }

        val closing =
            when (opening) {

                '{' -> '}'
                '(' -> ')'
                '[' -> ']'
                '<' -> '>'

                else ->
                    return -1
            }

        val forward =
            char == opening

        var depth =
            0

        var index =
            position

        var scanned =
            0

        while (
            scanned <
            MAX_PAIR_SCAN &&
            if (forward) {
                index < source.length
            } else {
                index >= 0
            }
        ) {

            scanned++

            val current =
                source[index]

            if (current == opening) {
                depth +=
                    if (forward) {
                        1
                    } else {
                        -1
                    }
            }

            if (current == closing) {
                depth +=
                    if (forward) {
                        -1
                    } else {
                        1
                    }
            }

            if (
                depth == 0 &&
                index != position
            ) {
                return index
            }

            index +=
                if (forward) {
                    1
                } else {
                    -1
                }
        }

        return -1
    }


    override fun onDraw(
        canvas: Canvas
    ) {

        val textLayout =
            layout

        if (
            textLayout != null &&
            textLayout.lineCount > 0
        ) {

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

        private const val HIGHLIGHT_DELAY =
            300L

        private const val MAX_FULL_HIGHLIGHT =
            100_000

        private const val EXTRA_HIGHLIGHT_CHARS =
            3000

        private const val MAX_PAIR_SCAN =
            50_000

        private val COLOR_DIRECTIVE =
            Color.rgb(
                91,
                180,
                255
            )

        private val COLOR_PROPERTY =
            Color.rgb(
                115,
                210,
                255
            )

        private val COLOR_REFERENCE =
            Color.rgb(
                220,
                160,
                255
            )

        private val COLOR_NUMBER =
            Color.rgb(
                255,
                190,
                100
            )

        private val COLOR_STRING =
            Color.rgb(
                150,
                220,
                145
            )

        private val COLOR_COMMENT =
            Color.rgb(
                120,
                135,
                145
            )
    }
}
