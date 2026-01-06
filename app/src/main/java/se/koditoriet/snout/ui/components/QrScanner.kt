package se.koditoriet.snout.ui.components

import android.Manifest.permission.CAMERA
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import se.koditoriet.snout.codec.QrCodeReader
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "QrScanner"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(onQrScanned: (String) -> Unit) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scan QR code") },
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .aspectRatio(9f / 16f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RequiresPermission(
                permission = CAMERA,
                permissionsRequiredMessage = "Camera permissions are required to scan QR codes",
            ) {
                Box(Modifier.fillMaxSize()) {
                    QrScannerView(
                        modifier = Modifier.fillMaxSize(),
                        onQrScanned = onQrScanned,
                    )
                    QrViewfinderOverlay(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QrScannerView(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit
) {
    Log.i(TAG, "Initializing camera for QR scanning")
    val handlerThread = HandlerThread("camera-bg").apply { start() }
    val handler = Handler(handlerThread.looper)
    val cameraManager = LocalContext.current.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.getBackCameraId()

    val availableOutputSizes = cameraManager
        .getCameraCharacteristics(cameraId)
        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        .getOutputSizes(ImageFormat.YUV_420_888)
        .filter { it.w.toFloat() / it.h in 1.75f..1.80f }

    Log.i(TAG, "Available 16:9 output sizes: ${availableOutputSizes.joinToString(", ")}")

    // Pick the largest available 16:9 resolution that is not _too_ large
    val outputSize = availableOutputSizes
        .filter { it.w < 1920 }
        .maxByOrNull { it.w } ?: availableOutputSizes.maxBy { it.w }

    Log.i(TAG, "Picked output size $outputSize")

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                var captureSession: CameraCaptureSession? = null
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        Log.d(TAG, "Setting preview buffer size to $outputSize")
                        surface.setDefaultBufferSize(outputSize.width, outputSize.height)
                        val previewSurface = Surface(surfaceTexture)
                        val callback = QrScannerCameraDeviceStateCallback(
                            outputSize = outputSize,
                            handler = handler,
                            previewSurface = previewSurface,
                            onQrScanned = onQrScanned,
                            onConfigured = { captureSession = it }
                        )

                        // This entire component is wrapped in a RequiresPermission component which ensures that we
                        // never get here unless we have camera access.
                        Log.d(TAG, "Opening camera with id $cameraId")
                        @Suppress("MissingPermission")
                        cameraManager.openCamera(cameraId, callback, handler)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        Log.d(TAG, "Camera preview surface texture destroyed, stopping camera")
                        captureSession?.apply {
                            stopRepeating()
                            abortCaptures()
                            close()
                            device.close()
                            Log.d(TAG, "Camera stopped")
                        }
                        return true
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

                }
            }
        },
    )
}

private fun CameraManager.getBackCameraId(): String = cameraIdList.first {
    val lensFacing = getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)
    lensFacing == CameraCharacteristics.LENS_FACING_BACK
}

@Composable
private fun QrViewfinderOverlay(modifier: Modifier) {
    Canvas(
        modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val size = min(size.width, size.height) * 0.65f
        val left = (this.size.width - size) / 2
        val top = (this.size.height - size) / 2

        drawRect(Color.Black.copy(alpha = 0.67f))

        drawRect(
            Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(size, size),
            blendMode = BlendMode.Clear
        )

        drawRect(
            Color.White,
            topLeft = Offset(left, top),
            size = Size(size, size),
            style = Stroke(width = 4f)
        )
    }
}

private class QrScannerCameraDeviceStateCallback(
    outputSize: android.util.Size,
    private val handler: Handler,
    private val previewSurface: Surface,
    private val onQrScanned: (String) -> Unit,
    private val onConfigured: (CameraCaptureSession) -> Unit,
) : CameraDevice.StateCallback() {
    private val cameraImageReader: ImageReader = ImageReader.newInstance(
        outputSize.width,
        outputSize.height,
        ImageFormat.YUV_420_888,
        2
    )

    private val qrCodeReader = QrCodeReader()

    @OptIn(ExperimentalAtomicApi::class)
    override fun onOpened(device: CameraDevice) {
        Log.d(TAG, "Camera device opened")
        val qrAlreadyScanned = AtomicBoolean(false)
        cameraImageReader.setOnImageAvailableListener(
            { reader ->
                reader.acquireLatestImage()?.let { image ->
                    val scanResult = qrCodeReader.tryScanImage(image)
                    image.close()

                    val qrWasScannedBefore = qrAlreadyScanned.exchange(scanResult != null)
                    if (!qrWasScannedBefore && scanResult != null) {
                        onQrScanned(scanResult)
                    }
                }
            },
            handler
        )

        Log.d(TAG, "Creating capture session")
        device.createCaptureSession(
            createCameraSessionConfig(
                device = device,
                handler = handler,
                previewSurface = this@QrScannerCameraDeviceStateCallback.previewSurface,
                outputSurface = cameraImageReader.surface,
                onConfigured = onConfigured,
            )
        )
    }

    override fun onClosed(camera: CameraDevice) { }
    override fun onDisconnected(device: CameraDevice) = device.close()
    override fun onError(device: CameraDevice, error: Int) = device.close()
}

private fun createCameraSessionConfig(
    device: CameraDevice,
    handler: Handler,
    previewSurface: Surface,
    outputSurface: Surface,
    onConfigured: (CameraCaptureSession) -> Unit,
): SessionConfiguration {
    val callback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "Creating capture request with two targets")
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(outputSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }.build()
            session.setRepeatingRequest(request, null, handler)
            onConfigured(session)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {}
    }

    val outputConfigs = listOf(
        OutputConfiguration(previewSurface),
        OutputConfiguration(outputSurface),
    )
    val executor = Executors.newSingleThreadExecutor()

    return SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        outputConfigs,
        executor,
        callback,
    )
}

private val android.util.Size.w: Int
    get() = max(width, height)

private val android.util.Size.h: Int
    get() = min(width, height)
