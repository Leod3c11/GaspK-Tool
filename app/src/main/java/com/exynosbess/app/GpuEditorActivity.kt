package com.exynosbess.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Activity
import com.exynosbess.app.boot.BootFlashManager
import com.exynosbess.app.boot.BootRebuildManager
import com.exynosbess.app.boot.BootRoundTripVerifier
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.gpu.GpuProfile
import com.exynosbess.app.gpu.MaliGpuParser
import com.exynosbess.app.tools.ToolManager
import java.io.File

class GpuEditorActivity : Activity() {

    private lateinit var subtitle: TextView
    private lateinit var status: TextView

    private lateinit var maxClock: EditText
    private lateinit var maxClockLimit: EditText
    private lateinit var minClock: EditText
    private lateinit var startClock: EditText
    private lateinit var bootClock: EditText

    private lateinit var defaultVoltage: EditText
    private lateinit var voltageMargin: EditText

    private lateinit var dvfsContainer: LinearLayout
    private lateinit var throttleContainer: LinearLayout

    private lateinit var prepareButton: Button
    private lateinit var flashButton: Button
    private lateinit var openDtsButton: Button

    private val dvfsInputs =
        mutableListOf<EditText>()

    private val throttleInputs =
        mutableListOf<EditText>()

    private var sourceDtbIndex = 0

    private var baseProfile: GpuProfile? =
        null

    private var supportedDtsFiles =
        emptyList<File>()


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_gpu_editor
        )

        bindViews()
        loadGpu()

        prepareButton.setOnClickListener {
            prepareBoot()
        }

        flashButton.setOnClickListener {
            confirmFlash()
        }

        openDtsButton.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    DtsEditorActivity::class.java
                ).putExtra(
                    "dtb_index",
                    sourceDtbIndex
                )
            )
        }
    }


    private fun bindViews() {

        subtitle =
            findViewById(
                R.id.gpuEditorSubtitle
            )

        status =
            findViewById(
                R.id.gpuEditorStatus
            )

        maxClock =
            findViewById(
                R.id.gpuMaxClock
            )

        maxClockLimit =
            findViewById(
                R.id.gpuMaxClockLimit
            )

        minClock =
            findViewById(
                R.id.gpuMinClock
            )

        startClock =
            findViewById(
                R.id.gpuStartClock
            )

        bootClock =
            findViewById(
                R.id.gpuBootClock
            )

        defaultVoltage =
            findViewById(
                R.id.gpuDefaultVoltage
            )

        voltageMargin =
            findViewById(
                R.id.gpuVoltageMargin
            )

        dvfsContainer =
            findViewById(
                R.id.gpuDvfsContainer
            )

        throttleContainer =
            findViewById(
                R.id.gpuThrottleContainer
            )

        prepareButton =
            findViewById(
                R.id.gpuPrepareBootButton
            )

        flashButton =
            findViewById(
                R.id.gpuFlashButton
            )

        openDtsButton =
            findViewById(
                R.id.gpuOpenDtsButton
            )
    }


    private fun loadGpu() {

        val dtbDir =
            File(
                filesDir,
                "workspace/dtbs"
            )

        supportedDtsFiles =
            dtbDir.listFiles()
                ?.filter {
                    it.isFile &&
                        it.name.matches(
                            Regex("""dtb_\d+\.dts""")
                        )
                }
                ?.filter {
                    try {
                        MaliGpuParser.isSupported(
                            it.readText()
                        )
                    } catch (_: Exception) {
                        false
                    }
                }
                ?.sortedBy {
                    dtbIndex(it)
                }
                ?: emptyList()

        if (
            supportedDtsFiles.isEmpty()
        ) {
            subtitle.text =
                "GPU Mali compatível não encontrada"

            status.text =
                "Nenhum nó mali@... foi encontrado nos DTS."

            prepareButton.isEnabled =
                false

            openDtsButton.isEnabled =
                false

            return
        }

        val source =
            supportedDtsFiles.first()

        sourceDtbIndex =
            dtbIndex(source)

        val profile =
            try {
                MaliGpuParser.parse(
                    source.readText()
                )
            } catch (_: Exception) {
                null
            }

        if (profile == null) {

            subtitle.text =
                "Falha ao interpretar GPU Mali"

            prepareButton.isEnabled =
                false

            return
        }

        baseProfile =
            profile

        subtitle.text =
            "${profile.vendor} • ${profile.nodeName} • " +
                "${supportedDtsFiles.size} DTB(s)"

        populateProfile(profile)
    }


    private fun populateProfile(
        profile: GpuProfile
    ) {

        maxClock.setText(
            khzToMhz(
                profile.maxClockKhz
            ).toString()
        )

        maxClockLimit.setText(
            khzToMhz(
                profile.maxClockLimitKhz
            ).toString()
        )

        minClock.setText(
            khzToMhz(
                profile.minClockKhz
            ).toString()
        )

        startClock.setText(
            khzToMhz(
                profile.startClockKhz
            ).toString()
        )

        bootClock.setText(
            khzToMhz(
                profile.bootClockKhz
            ).toString()
        )

        defaultVoltage.setText(
            profile.defaultVoltageUv
                .toString()
        )

        voltageMargin.setText(
            profile.voltageMarginUv
                .toString()
        )

        dvfsContainer.removeAllViews()
        dvfsInputs.clear()

        profile.dvfsLevels
            .forEachIndexed {
                index,
                level ->

                val input =
                    createNumericInput(
                        "Nível ${index + 1} (MHz)",
                        khzToMhz(
                            level.frequencyKhz
                        )
                    )

                dvfsContainer.addView(
                    input
                )

                dvfsInputs += input
            }

        throttleContainer
            .removeAllViews()

        throttleInputs.clear()

        profile.throttlingKhz
            .forEachIndexed {
                index,
                frequency ->

                val input =
                    createNumericInput(
                        "Throttling ${index + 1} (MHz)",
                        khzToMhz(
                            frequency
                        )
                    )

                throttleContainer.addView(
                    input
                )

                throttleInputs += input
            }
    }


    private fun createNumericInput(
        hint: String,
        value: Long
    ): EditText {

        val input =
            EditText(this)

        input.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                topMargin =
                    dp(5)
            }

        input.setBackgroundResource(
            R.drawable.bg_search_field
        )

        input.hint =
            hint

        input.setText(
            value.toString()
        )

        input.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        input.setTextColor(
            getColor(
                R.color.exynos_text
            )
        )

        input.setHintTextColor(
            getColor(
                R.color.exynos_text_secondary
            )
        )

        input.setPadding(
            dp(12),
            0,
            dp(12),
            0
        )

        return input
    }


    private fun prepareBoot() {

        val requested =
            readRequestedValues()
                ?: return

        setBusy(true)

        status.text =
            "Salvando alterações..."

        flashButton.isEnabled =
            false

        Thread {

            val originals =
                linkedMapOf<File, String>()

            try {

                supportedDtsFiles
                    .forEach {
                        file ->

                        val original =
                            file.readText()

                        originals[file] =
                            original

                        val profile =
                            MaliGpuParser.parse(
                                original
                            ) ?: error(
                                "Não foi possível interpretar ${file.name}"
                            )

                        applyRequestedValues(
                            profile,
                            requested
                        )

                        val modified =
                            MaliGpuParser.apply(
                                original,
                                profile
                            )

                        if (
                            modified == original
                        ) {
                            error(
                                "Nenhuma alteração aplicada em ${file.name}"
                            )
                        }

                        file.writeText(
                            modified
                        )
                    }

                runOnUiThread {
                    status.text =
                        "Validando DTS..."
                }

                validateModifiedDts()

                runOnUiThread {
                    status.text =
                        "Reconstruindo boot..."
                }

                val rebuild =
                    BootRebuildManager.rebuild(
                        this
                    )

                if (!rebuild.success) {
                    error(
                        rebuild.message
                    )
                }

                runOnUiThread {
                    status.text =
                        "Verificando round-trip..."
                }

                val verify =
                    BootRoundTripVerifier.verify(
                        this
                    )

                if (!verify.success) {
                    error(
                        verify.message
                    )
                }

                runOnUiThread {

                    status.text =
                        "✓ DTS validado\n" +
                        "✓ Boot reconstruído\n" +
                        "✓ Round-trip verificado"

                    flashButton.isEnabled =
                        true

                    setBusy(false)
                }

            } catch (
                e: Exception
            ) {

                originals.forEach {
                    file,
                    content ->

                    try {
                        file.writeText(
                            content
                        )
                    } catch (_: Exception) {
                    }
                }

                runOnUiThread {

                    status.text =
                        "✗ ${e.message ?: "Falha ao preparar boot"}"

                    flashButton.isEnabled =
                        false

                    setBusy(false)
                }
            }

        }.start()
    }


    private data class RequestedValues(
        val maxClockKhz: Long,
        val maxClockLimitKhz: Long,
        val minClockKhz: Long,
        val startClockKhz: Long,
        val bootClockKhz: Long,
        val defaultVoltageUv: Long,
        val voltageMarginUv: Long,
        val dvfsKhz: List<Long>,
        val throttlingKhz: List<Long>
    )


    private fun readRequestedValues():
        RequestedValues? {

        fun mhz(
            input: EditText,
            name: String
        ): Long {

            val value =
                input.text
                    .toString()
                    .trim()
                    .toLongOrNull()
                    ?: error(
                        "$name inválido"
                    )

            if (value <= 0) {
                error(
                    "$name deve ser maior que zero"
                )
            }

            return value * 1000L
        }

        fun number(
            input: EditText,
            name: String
        ): Long {

            val value =
                input.text
                    .toString()
                    .trim()
                    .toLongOrNull()
                    ?: error(
                        "$name inválido"
                    )

            if (value < 0) {
                error(
                    "$name inválido"
                )
            }

            return value
        }

        return try {

            val dvfs =
                dvfsInputs.mapIndexed {
                    index,
                    input ->

                    mhz(
                        input,
                        "DVFS ${index + 1}"
                    )
                }

            val throttling =
                throttleInputs.mapIndexed {
                    index,
                    input ->

                    mhz(
                        input,
                        "Throttling ${index + 1}"
                    )
                }

            RequestedValues(
                maxClockKhz =
                    mhz(
                        maxClock,
                        "Clock máximo"
                    ),

                maxClockLimitKhz =
                    mhz(
                        maxClockLimit,
                        "Limite máximo"
                    ),

                minClockKhz =
                    mhz(
                        minClock,
                        "Clock mínimo"
                    ),

                startClockKhz =
                    mhz(
                        startClock,
                        "Clock inicial"
                    ),

                bootClockKhz =
                    mhz(
                        bootClock,
                        "Clock de boot"
                    ),

                defaultVoltageUv =
                    number(
                        defaultVoltage,
                        "Tensão padrão"
                    ),

                voltageMarginUv =
                    number(
                        voltageMargin,
                        "Margem de tensão"
                    ),

                dvfsKhz =
                    dvfs,

                throttlingKhz =
                    throttling
            )

        } catch (
            e: Exception
        ) {

            status.text =
                "✗ ${e.message}"

            null
        }
    }


    private fun applyRequestedValues(
        profile: GpuProfile,
        requested: RequestedValues
    ) {

        profile.maxClockKhz =
            requested.maxClockKhz

        profile.maxClockLimitKhz =
            requested.maxClockLimitKhz

        profile.minClockKhz =
            requested.minClockKhz

        profile.startClockKhz =
            requested.startClockKhz

        profile.bootClockKhz =
            requested.bootClockKhz

        profile.defaultVoltageUv =
            requested.defaultVoltageUv

        profile.voltageMarginUv =
            requested.voltageMarginUv


        if (
            profile.dvfsLevels.size !=
            requested.dvfsKhz.size
        ) {
            error(
                "A tabela DVFS difere entre os DTBs"
            )
        }

        profile.dvfsLevels
            .forEachIndexed {
                index,
                level ->

                level.frequencyKhz =
                    requested.dvfsKhz[index]
            }


        if (
            profile.throttlingKhz.size !=
            requested.throttlingKhz.size
        ) {
            error(
                "A tabela de throttling difere entre os DTBs"
            )
        }

        requested.throttlingKhz
            .forEachIndexed {
                index,
                value ->

                profile.throttlingKhz[index] =
                    value
            }
    }


    private fun validateModifiedDts() {

        val tool =
            ToolManager.installDtc(
                this
            )

        if (
            !tool.success ||
            tool.path == null
        ) {
            error(
                "dtc indisponível: ${tool.message}"
            )
        }

        val validationDir =
            File(
                filesDir,
                "gpu_validation"
            )

        ShellExecutor.root(
            "rm -rf '${validationDir.absolutePath}'"
        )

        validationDir.mkdirs()

        supportedDtsFiles
            .forEach {
                file ->

                val output =
                    File(
                        validationDir,
                        file.nameWithoutExtension +
                            ".dtb"
                    )

                val result =
                    ShellExecutor.root(
                        "'${tool.path}' " +
                            "-I dts -O dtb " +
                            "-o '${output.absolutePath}' " +
                            "'${file.absolutePath}'"
                    )

                if (
                    !result.success ||
                    !output.exists() ||
                    output.length() == 0L
                ) {

                    val detail =
                        result.output
                            .lineSequence()
                            .firstOrNull {
                                it.isNotBlank()
                            }
                            ?: "Erro desconhecido"

                    error(
                        "${file.name}: $detail"
                    )
                }
            }
    }


    private fun confirmFlash() {

        if (!flashButton.isEnabled)
            return

        AlertDialog.Builder(this)
            .setTitle(
                "Flash do novo boot"
            )
            .setMessage(
                "O boot já foi reconstruído e verificado.\n\n" +
                    "Será criado um backup pré-flash e a gravação será " +
                    "verificada por SHA-256.\n\n" +
                    "O aparelho não será reiniciado automaticamente."
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Flash"
            ) {
                _,
                _ ->

                executeFlash()
            }
            .show()
    }


    private fun executeFlash() {

        setBusy(true)

        flashButton.isEnabled =
            false

        status.text =
            "Gravando boot..."

        Thread {

            val result =
                BootFlashManager.flash(
                    this
                )

            runOnUiThread {

                status.text =
                    if (result.success) {
                        "✓ Boot gravado e verificado\n" +
                            "Reinicialização não executada"
                    } else {
                        "✗ ${result.message}"
                    }

                setBusy(false)

                flashButton.isEnabled =
                    result.success
            }

        }.start()
    }


    private fun setBusy(
        busy: Boolean
    ) {

        prepareButton.isEnabled =
            !busy

        openDtsButton.isEnabled =
            !busy
    }


    private fun dtbIndex(
        file: File
    ): Int {

        return Regex(
            """dtb_(\d+)\.dts"""
        )
            .find(file.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }


    private fun khzToMhz(
        value: Long
    ): Long {

        return value / 1000L
    }


    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
