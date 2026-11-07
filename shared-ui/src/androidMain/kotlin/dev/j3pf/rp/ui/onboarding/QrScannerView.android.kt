package dev.j3pf.rp.ui.onboarding

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalGetImage::class)
@Composable
actual fun QrScannerView(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner =
        remember {
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val disposed = remember { AtomicBoolean(false) }
    val lastRawValue = remember { AtomicReference<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            cameraProvider.value?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { viewContext ->
            val view = PreviewView(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                if (disposed.get()) return@addListener

                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || disposed.get()) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    scanner
                        .process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            val raw = codes.firstNotNullOfOrNull { it.rawValue }
                            if (raw == null) {
                                lastRawValue.set(null)
                            } else if (lastRawValue.getAndSet(raw) != raw) {
                                view.post {
                                    if (!disposed.get()) currentOnResult(raw)
                                }
                            }
                        }.addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onSuccess {
                    cameraProvider.value = provider
                }
            }, ContextCompat.getMainExecutor(viewContext))
            view
        },
        modifier = Modifier.fillMaxSize(),
    )
}
