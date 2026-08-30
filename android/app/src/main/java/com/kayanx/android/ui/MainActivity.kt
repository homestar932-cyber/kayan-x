package com.kayanx.android.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kayanx.android.fs.model.LogicalRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KayanApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KayanApp(vm: MainViewModel = hiltViewModel()) {
    val ui by vm.uiState.collectAsState()
    val listState = rememberLazyListState()

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) vm.onTreeSelected(LogicalRoot.DOWNLOADS, uri)
    }

    val ggufLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.onGgufSelected(uri)
    }

    LaunchedEffect(ui.log.size) {
        if (ui.log.isNotEmpty()) listState.animateScrollToItem(ui.log.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kayan X Native") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusChip(if (ui.hasDownloads) "Downloads ✓" else "Downloads ✗", ui.hasDownloads)
                StatusChip(ui.modelStatus, ui.modelLoaded)
            }

            if (ui.benchmarkText.isNotBlank()) {
                Text(ui.benchmarkText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { treeLauncher.launch(null) }) { Text("منح Downloads") }
                Button(
                    onClick = { ggufLauncher.launch(arrayOf("*/*")) },
                    enabled = !ui.isLoadingModel
                ) { Text(if (ui.isLoadingModel) "تحميل…" else "اختر GGUF") }
                OutlinedButton(
                    onClick = { vm.runBenchmarkSmoke() },
                    enabled = ui.modelLoaded
                ) { Text("Benchmark") }
            }

            if (ui.isLoadingModel) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            var goal by remember {
                mutableStateOf("أنشئ مجلد KayanTest داخل Downloads واكتب فيه ملف hello.txt يحتوي مرحبا كيان")
            }
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text("الهدف") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = ui.hasDownloads
            )
            Button(
                onClick = { if (goal.isNotBlank()) vm.startAgent(goal) },
                enabled = ui.hasDownloads && goal.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("تشغيل الوكيل") }

            ui.pendingConfirmation?.let { pending ->
                AlertDialog(
                    onDismissRequest = { vm.confirm(false) },
                    title = { Text("تأكيد مطلوب") },
                    text = {
                        Column {
                            Text(pending.details)
                            Text("العملية: ${pending.operation}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { vm.confirm(true) }) { Text("تأكيد") } },
                    dismissButton = { TextButton(onClick = { vm.confirm(false) }) { Text("إلغاء") } }
                )
            }

            Text("السجل", style = MaterialTheme.typography.titleSmall)
            Surface(tonalElevation = 2.dp, modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(ui.log) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    )
}
