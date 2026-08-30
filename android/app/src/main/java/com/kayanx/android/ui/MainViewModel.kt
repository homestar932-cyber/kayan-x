package com.kayanx.android.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kayanx.android.agent.loop.AgentEvent
import com.kayanx.android.agent.loop.AgentOrchestrator
import com.kayanx.android.agent.state.PendingConfirmation
import com.kayanx.android.fs.FileBridge
import com.kayanx.android.fs.model.LogicalRoot
import com.kayanx.android.fs.saf.PersistedTreeStore
import com.kayanx.android.native.LlamaEngine
import com.kayanx.android.native.ModelLoader
import com.kayanx.android.settings.EngineSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val hasDownloads: Boolean = false,
    val modelStatus: String = "غير محمّل",
    val modelLoaded: Boolean = false,
    val isLoadingModel: Boolean = false,
    val pendingConfirmation: PendingConfirmation? = null,
    val benchmarkText: String = "",
    val log: List<String> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val treeStore: PersistedTreeStore,
    private val fileBridge: FileBridge,
    private val orchestrator: AgentOrchestrator,
    private val engine: LlamaEngine,
    private val modelLoader: ModelLoader,
    private val engineSettings: EngineSettings
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val agentEvents = orchestrator.events

    init {
        viewModelScope.launch {
            val has = treeStore.hasTree(LogicalRoot.DOWNLOADS)
            _uiState.update { it.copy(hasDownloads = has) }
            observeEvents()
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            orchestrator.events.collect { event ->
                when (event) {
                    is AgentEvent.Started -> append("▶ الهدف: ${event.goal}")
                    is AgentEvent.Planning -> append("… تخطيط")
                    is AgentEvent.Executing -> append("⚙ تنفيذ: ${event.tool}")
                    is AgentEvent.Verified -> append("✓ تحقق: ${event.result}")
                    is AgentEvent.NeedsConfirmation -> {
                        append("⚠ تأكيد مطلوب: ${event.pending.details}")
                        _uiState.update { it.copy(pendingConfirmation = event.pending) }
                    }
                    is AgentEvent.NeedsUserInput -> append("؟ يحتاج إدخال: ${event.question}")
                    is AgentEvent.Finished -> append("✔ النتيجة: ${event.answer}")
                    else -> {}
                }
            }
        }
    }

    fun onTreeSelected(root: LogicalRoot, uri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            fileBridge.grantTree(root, uri)
            _uiState.update { it.copy(hasDownloads = true) }
            append("✓ تم منح صلاحية $root")
        }
    }

    fun onGgufSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, modelStatus = "جارٍ التحميل…") }
            append("تحميل النموذج من SAF…")
            val config = engineSettings.toEngineConfig()
            val result = modelLoader.profileAndLoad(uri, config)
            if (result.success) {
                val bench = engine.getBenchmark()
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        modelLoaded = true,
                        modelStatus = "محمّل ✓",
                        benchmarkText = "Load: %.0fms | tok/s: %.1f | TTFT: %.0fms".format(
                            bench.modelLoadMs, bench.tokensPerSec, bench.firstTokenLatencyMs
                        )
                    )
                }
                append("✓ ${result.message}")
                append("Benchmark: ${_uiState.value.benchmarkText}")
            } else {
                _uiState.update {
                    it.copy(isLoadingModel = false, modelLoaded = false, modelStatus = "فشل التحميل")
                }
                append("✗ ${result.message}")
            }
        }
    }

    fun startAgent(goal: String) {
        viewModelScope.launch {
            if (!_uiState.value.hasDownloads) {
                append("✗ يجب منح صلاحية Downloads أولاً")
                return@launch
            }
            append("▶ بدء الوكيل…")
            orchestrator.start(goal)
        }
    }

    fun confirm(yes: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingConfirmation = null) }
            orchestrator.confirmPending(yes)
        }
    }

    fun runBenchmarkSmoke() {
        viewModelScope.launch {
            if (!engine.isLoaded()) {
                append("النموذج غير محمّل")
                return@launch
            }
            append("تشغيل benchmark…")
            val t0 = System.currentTimeMillis()
            val out = engine.complete("قل مرحبا في جملة واحدة.", maxTokens = 32, temperature = 0.1f)
            val dt = System.currentTimeMillis() - t0
            val bench = engine.getBenchmark()
            append("Output: $out")
            append("Wall: ${dt}ms | Load: %.0fms | tok/s: %.1f | TTFT: %.0fms".format(
                bench.modelLoadMs, bench.tokensPerSec, bench.firstTokenLatencyMs
            ))
            _uiState.update {
                it.copy(benchmarkText = "tok/s=%.1f TTFT=%.0fms".format(bench.tokensPerSec, bench.firstTokenLatencyMs))
            }
        }
    }

    private fun append(line: String) {
        _uiState.update { it.copy(log = (it.log + line).takeLast(200)) }
    }
}
