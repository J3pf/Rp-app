package dev.j3pf.rp.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.repo.PairResult
import dev.j3pf.rp.data.repo.SessionRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.ui.theme.RpShapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class PairUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class PairViewModel
    @Inject
    constructor(
        private val session: SessionRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PairUiState())
        val state: StateFlow<PairUiState> = _state

        fun pair(
            url: String,
            code: String,
        ) {
            if (_state.value.busy) return
            _state.update { PairUiState(busy = true) }
            viewModelScope.launch {
                _state.update {
                    when (val r = session.pair(url, code)) {
                        PairResult.Success -> PairUiState(done = true)
                        is PairResult.Failure -> PairUiState(error = r.message)
                    }
                }
            }
        }

        fun pairFromUri(text: String): Boolean {
            val (u, c) = SessionRepository.parsePairingUri(text) ?: return false
            pair(u, c)
            return true
        }

        fun clearError() = _state.update { it.copy(error = null) }

        fun finishOnboarding() {
            viewModelScope.launch { settings.setOnboardingDone(true) }
        }
    }

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RP", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Ve y controla todos tus agentes Pi desde el móvil.\nRecibe preguntas y autorizaciones y respóndelas desde la propia notificación.",
            Modifier.padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onStart,
            shape = RpShapes.pill,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Emparejar con mi relay") }
    }
}

/** Introducir URL + código (o llegar aquí con un deep link `rp://pair?u=…&c=…` ya relleno). */
@Composable
fun PairScreen(
    initialUrl: String?,
    initialCode: String?,
    onScan: () -> Unit,
    onPaired: () -> Unit,
    vm: PairViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(initialCode.orEmpty()) }
    LaunchedEffect(initialUrl, initialCode) {
        if (!initialUrl.isNullOrBlank()) url = initialUrl
        if (!initialCode.isNullOrBlank()) code = initialCode
    }
    LaunchedEffect(st.done) { if (st.done) onPaired() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Emparejar dispositivo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "En el servidor ejecuta `rp-relay pair device` y escanea el QR, o escribe la URL y el código.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onScan,
            shape = RpShapes.pill,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !st.busy,
        ) { Text("Escanear código QR") }
        OutlinedTextField(
            url,
            {
                url = it
                vm.clearError()
            },
            Modifier.fillMaxWidth(),
            label = { Text("URL del relay (https://…)") },
            singleLine = true,
            enabled = !st.busy,
        )
        OutlinedTextField(
            code,
            {
                code = it.uppercase()
                vm.clearError()
            },
            Modifier.fillMaxWidth(),
            label = { Text("Código de emparejamiento") },
            singleLine = true,
            enabled = !st.busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = { vm.pair(url, code) },
            shape = RpShapes.pill,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !st.busy && url.isNotBlank() && code.isNotBlank(),
        ) {
            if (st.busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).then(Modifier), strokeWidth = 2.dp)
            Text("Emparejar")
        }
    }
}

/** Escáner QR con CameraX + ML Kit (modelo empaquetado, sin Play Services de descarga). */
@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanScreen(
    onResult: (url: String, code: String) -> Unit,
    onManual: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var invalid by remember { mutableStateOf(false) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Se necesita la cámara para escanear el QR", textAlign = TextAlign.Center)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 16.dp)) { Text("Conceder permiso") }
            TextButton(onClick = onManual) { Text("Escribir URL y código") }
        }
        return
    }

    val handled = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner =
        remember {
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val view = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null || handled.get()) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        scanner
                            .process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val raw = codes.firstNotNullOfOrNull { it.rawValue }
                                if (raw != null) {
                                    val parsed = SessionRepository.parsePairingUri(raw)
                                    if (parsed != null && handled.compareAndSet(false, true)) {
                                        view.post { onResult(parsed.first, parsed.second) }
                                    } else if (parsed == null) {
                                        invalid = true
                                    }
                                }
                            }.addOnCompleteListener { proxy.close() }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                view
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (invalid) Text("Ese QR no es de RP (rp://pair…)", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onManual) { Text("Escribir URL y código") }
        }
    }
}

/** Permiso de notificaciones (Android 13+) y exclusión de optimización de batería. */
@SuppressLint("BatteryLife") // Persistent relay WebSocket connection requires foreground battery optimization exemption.
@Composable
fun PermissionsScreen(
    onDone: () -> Unit,
    vm: PairViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }
    val pm = context.getSystemService(PowerManager::class.java)
    var batteryOk by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    val batteryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
        }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Notificaciones", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sin el permiso de notificaciones no podrás recibir preguntas ni autorizaciones con la app cerrada.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = {
            if (Build.VERSION.SDK_INT >=
                33
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }, enabled = !notifGranted, modifier = Modifier.fillMaxWidth()) {
            Text(if (notifGranted) "Permiso concedido ✓" else "Permitir notificaciones")
        }
        Text("Batería", style = MaterialTheme.typography.titleLarge)
        Text(
            "El canal directo mantiene una conexión con tu relay. Para máxima fiabilidad en algunos fabricantes, " +
                "excluye RP de la optimización de batería (recomendado; con FCM es solo una sugerencia).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                batteryLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                )
            },
            enabled = !batteryOk,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (batteryOk) "Excluida de la optimización ✓" else "Excluir de la optimización de batería") }
        Button(onClick = {
            vm.finishOnboarding()
            onDone()
        }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
        TextButton(onClick = {
            vm.finishOnboarding()
            onDone()
        }, modifier = Modifier.fillMaxWidth()) { Text("Ahora no") }
    }
}
