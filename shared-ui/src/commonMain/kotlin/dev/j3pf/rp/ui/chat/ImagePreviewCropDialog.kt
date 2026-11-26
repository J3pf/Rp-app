package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen image preview and optional free-form crop dialog.
 *
 * When [isNewAttachment] is `true`, the primary preview action attaches the full image as-is
 * (`onConfirm(null)`) and crop mode attaches the cropped region (`onConfirm(cropRect)`).
 * When [isNewAttachment] is `false` (inspecting an already staged attachment), preview lets the
 * user inspect the photo or enter crop mode to replace it with a cropped version.
 */
@Composable
fun ImagePreviewCropDialog(
    image: ImageBitmap?,
    loading: Boolean,
    error: String?,
    isNewAttachment: Boolean,
    canCrop: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (NormalizedCropRect?) -> Unit,
) {
    var cropMode by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(NormalizedCropRect.FULL) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 28.dp.toPx() }
    val cornerArmPx = with(density) { 22.dp.toPx() }
    val cornerStrokePx = with(density) { 3.5.dp.toPx() }
    val borderStrokePx = with(density) { 1.5.dp.toPx() }
    val gridStrokePx = with(density) { 1.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            color = Color.Black,
            contentColor = Color.White,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (cropMode) {
                                cropMode = false
                                cropRect = NormalizedCropRect.FULL
                            } else {
                                onDismiss()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = if (cropMode) "Recortar imagen" else "Vista previa",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    if (cropMode) {
                        TextButton(
                            onClick = { cropRect = NormalizedCropRect.FULL },
                            enabled = !cropRect.isFullImage(),
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (!cropRect.isFullImage()) Color.White else Color.Gray,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Restablecer",
                                color = if (!cropRect.isFullImage()) Color.White else Color.Gray,
                            )
                        }
                    } else if (canCrop && image != null) {
                        TextButton(
                            onClick = {
                                cropRect = NormalizedCropRect.FULL
                                cropMode = true
                            },
                        ) {
                            Icon(
                                Icons.Filled.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Recortar", color = Color.White)
                        }
                    }
                }

                // Image stage
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .onSizeChanged { stageSize = it },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> {
                            CircularProgressIndicator(color = Color.White)
                        }

                        image == null -> {
                            Text(
                                text = error ?: "No se pudo cargar la imagen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }

                        else -> {
                            val imageRect =
                                remember(stageSize, image.width, image.height) {
                                    computeFitRect(
                                        containerWidth = stageSize.width.toFloat(),
                                        containerHeight = stageSize.height.toFloat(),
                                        imageWidth = image.width,
                                        imageHeight = image.height,
                                    )
                                }

                            Image(
                                bitmap = image,
                                contentDescription = "Vista previa de imagen",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )

                            if (cropMode && imageRect.width > 0f && imageRect.height > 0f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .pointerInput(imageRect, handleRadiusPx) {
                                                var activeHandle: CropHandle? = null
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        activeHandle =
                                                            hitTestCropHandle(
                                                                touchX = offset.x,
                                                                touchY = offset.y,
                                                                imageRect = imageRect,
                                                                cropRect = cropRect,
                                                                handleRadiusPx = handleRadiusPx,
                                                            )
                                                    },
                                                    onDragEnd = { activeHandle = null },
                                                    onDragCancel = { activeHandle = null },
                                                    onDrag = { change, dragAmount ->
                                                        val handle = activeHandle ?: return@detectDragGestures
                                                        change.consume()
                                                        cropRect =
                                                            applyCropDrag(
                                                                rect = cropRect,
                                                                handle = handle,
                                                                dxNorm = dragAmount.x / imageRect.width,
                                                                dyNorm = dragAmount.y / imageRect.height,
                                                            )
                                                    },
                                                )
                                            },
                                ) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        val cLeft = imageRect.left + cropRect.left * imageRect.width
                                        val cTop = imageRect.top + cropRect.top * imageRect.height
                                        val cRight = imageRect.left + cropRect.right * imageRect.width
                                        val cBottom = imageRect.top + cropRect.bottom * imageRect.height
                                        val cWidth = (cRight - cLeft).coerceAtLeast(1f)
                                        val cHeight = (cBottom - cTop).coerceAtLeast(1f)

                                        // Scrim outside crop region (within fitted image bounds)
                                        val scrim = Color.Black.copy(alpha = 0.62f)
                                        if (cTop > imageRect.top) {
                                            drawRect(
                                                color = scrim,
                                                topLeft = Offset(imageRect.left, imageRect.top),
                                                size = Size(imageRect.width, cTop - imageRect.top),
                                            )
                                        }
                                        if (cBottom < imageRect.bottom) {
                                            drawRect(
                                                color = scrim,
                                                topLeft = Offset(imageRect.left, cBottom),
                                                size = Size(imageRect.width, imageRect.bottom - cBottom),
                                            )
                                        }
                                        if (cLeft > imageRect.left) {
                                            drawRect(
                                                color = scrim,
                                                topLeft = Offset(imageRect.left, cTop),
                                                size = Size(cLeft - imageRect.left, cHeight),
                                            )
                                        }
                                        if (cRight < imageRect.right) {
                                            drawRect(
                                                color = scrim,
                                                topLeft = Offset(cRight, cTop),
                                                size = Size(imageRect.right - cRight, cHeight),
                                            )
                                        }

                                        // Rule-of-thirds grid
                                        val gridColor = Color.White.copy(alpha = 0.35f)
                                        for (i in 1..2) {
                                            val gx = cLeft + cWidth * i / 3f
                                            drawLine(gridColor, Offset(gx, cTop), Offset(gx, cBottom), gridStrokePx)
                                            val gy = cTop + cHeight * i / 3f
                                            drawLine(gridColor, Offset(cLeft, gy), Offset(cRight, gy), gridStrokePx)
                                        }

                                        // Outer border
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(cLeft, cTop),
                                            size = Size(cWidth, cHeight),
                                            style = Stroke(width = borderStrokePx),
                                        )

                                        // Bold L-shaped corner handles
                                        val arm = minOf(cornerArmPx, cWidth / 3f, cHeight / 3f)
                                        // Top-left
                                        drawLine(Color.White, Offset(cLeft, cTop), Offset(cLeft + arm, cTop), cornerStrokePx)
                                        drawLine(Color.White, Offset(cLeft, cTop), Offset(cLeft, cTop + arm), cornerStrokePx)
                                        // Top-right
                                        drawLine(Color.White, Offset(cRight, cTop), Offset(cRight - arm, cTop), cornerStrokePx)
                                        drawLine(Color.White, Offset(cRight, cTop), Offset(cRight, cTop + arm), cornerStrokePx)
                                        // Bottom-left
                                        drawLine(Color.White, Offset(cLeft, cBottom), Offset(cLeft + arm, cBottom), cornerStrokePx)
                                        drawLine(Color.White, Offset(cLeft, cBottom), Offset(cLeft, cBottom - arm), cornerStrokePx)
                                        // Bottom-right
                                        drawLine(Color.White, Offset(cRight, cBottom), Offset(cRight - arm, cBottom), cornerStrokePx)
                                        drawLine(Color.White, Offset(cRight, cBottom), Offset(cRight, cBottom - arm), cornerStrokePx)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom action bar
                Surface(
                    color = Color(0xFF141414),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (cropMode && image != null) {
                            val px = cropRect.toPixelRect(image.width, image.height)
                            Text(
                                text = "Arrastra las esquinas o bordes para recortar · ${px.width} × ${px.height}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (cropMode) {
                                OutlinedButton(
                                    onClick = {
                                        cropMode = false
                                        cropRect = NormalizedCropRect.FULL
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Cancelar", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        val selected = cropRect.takeUnless { it.isFullImage() }
                                        onConfirm(selected)
                                    },
                                    enabled = image != null && (isNewAttachment || !cropRect.isFullImage()),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isNewAttachment) "Recortar y adjuntar" else "Aplicar recorte")
                                }
                            } else if (isNewAttachment) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Cancelar", color = Color.White)
                                }
                                Button(
                                    onClick = { onConfirm(null) },
                                    enabled = !loading && image != null,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Adjuntar")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Cerrar", color = Color.White)
                                }
                                if (canCrop && image != null) {
                                    Button(
                                        onClick = {
                                            cropRect = NormalizedCropRect.FULL
                                            cropMode = true
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Recortar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
