package dev.j3pf.rp.ui.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScannerView(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnCancel = rememberUpdatedState(onCancel)
    val scanner =
        remember {
            IosQrScanner(
                onResult = { currentOnResult.value(it) },
                onUnavailable = { currentOnCancel.value() },
            )
        }
    DisposableEffect(scanner) {
        onDispose { scanner.dispose() }
    }
    UIKitView(
        factory = { scanner.createView() },
        modifier = Modifier.fillMaxSize(),
        update = { scanner.updateView(it) },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosQrScanner(
    private val onResult: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val session = AVCaptureSession()
    private val output = AVCaptureMetadataOutput()
    private val delegate =
        QrMetadataDelegate { raw ->
            stopSessionAsync()
            onResult(raw)
        }
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var configured = false

    fun createView(): UIView {
        val view = UIView()
        if (!configured) {
            configured = configureSession()
            if (!configured) {
                onUnavailable()
                return view
            }
        }

        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.addSublayer(layer)
        previewLayer = layer
        layer.frame = view.bounds
        startSessionAsync()
        return view
    }

    fun updateView(view: UIView) {
        previewLayer?.frame = view.bounds
    }

    fun dispose() {
        output.setMetadataObjectsDelegate(null, null)
        stopSessionAsync()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
    }

    private fun startSessionAsync() {
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        dispatch_async(queue) {
            if (!session.isRunning()) {
                session.startRunning()
            }
        }
    }

    private fun stopSessionAsync() {
        val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        dispatch_async(queue) {
            if (session.isRunning()) {
                session.stopRunning()
            }
        }
    }

    private fun configureSession(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh
        val canConfigure = session.canAddInput(input) && session.canAddOutput(output)
        if (canConfigure) {
            session.addInput(input)
            session.addOutput(output)
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        }
        session.commitConfiguration()
        return canConfigure
    }
}

@OptIn(ExperimentalForeignApi::class)
private class QrMetadataDelegate(
    private val onResult: (String) -> Unit,
) : NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    private var lastRawValue: String? = null

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val rawValue =
            didOutputMetadataObjects
                .asSequence()
                .mapNotNull { it as? AVMetadataMachineReadableCodeObject }
                .firstOrNull { it.type == AVMetadataObjectTypeQRCode }
                ?.stringValue

        if (rawValue == null) {
            lastRawValue = null
        } else if (rawValue != lastRawValue) {
            lastRawValue = rawValue
            onResult(rawValue)
        }
    }
}
