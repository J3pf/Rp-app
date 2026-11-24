package dev.j3pf.rp.ui.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
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
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

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
private class QrPreviewContainerView : UIView(frame = CGRectZero.readValue()) {
    var previewLayer: AVCaptureVideoPreviewLayer? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosQrScanner(
    private val onResult: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val session = AVCaptureSession()
    private val output = AVCaptureMetadataOutput()
    private val sessionQueue = dispatch_queue_create("dev.j3pf.rp.qr", null)
    private val delegate =
        QrMetadataDelegate { raw ->
            if (disposed) return@QrMetadataDelegate
            stopSessionAsync()
            onResult(raw)
        }
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var configured = false

    @Volatile
    private var disposed = false

    fun createView(): UIView {
        val view = QrPreviewContainerView()
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                attachPreviewAndStart(view)
            }

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (disposed) return@dispatch_async
                        if (granted) {
                            attachPreviewAndStart(view)
                        } else {
                            onUnavailable()
                        }
                    }
                }
            }

            else -> {
                dispatchUnavailableAsync()
            }
        }
        return view
    }

    fun updateView(view: UIView) {
        previewLayer?.frame = view.bounds
    }

    fun dispose() {
        disposed = true
        output.setMetadataObjectsDelegate(null, null)
        stopSessionAsync()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
    }

    private fun attachPreviewAndStart(view: QrPreviewContainerView) {
        if (disposed) return
        val layer =
            previewLayer ?: AVCaptureVideoPreviewLayer(session = session).also {
                it.videoGravity = AVLayerVideoGravityResizeAspectFill
                view.layer.addSublayer(it)
                view.previewLayer = it
                previewLayer = it
            }
        layer.frame = view.bounds

        dispatch_async(sessionQueue) {
            if (disposed) return@dispatch_async
            if (!configured) {
                configured = configureSession()
            }
            if (!configured) {
                dispatch_async(dispatch_get_main_queue()) {
                    previewLayer?.removeFromSuperlayer()
                    view.previewLayer = null
                    previewLayer = null
                    if (!disposed) {
                        onUnavailable()
                    }
                }
                return@dispatch_async
            }
            if (!session.isRunning()) {
                session.startRunning()
            }
        }
    }

    private fun dispatchUnavailableAsync() {
        dispatch_async(dispatch_get_main_queue()) {
            if (!disposed) {
                onUnavailable()
            }
        }
    }

    private fun stopSessionAsync() {
        dispatch_async(sessionQueue) {
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
