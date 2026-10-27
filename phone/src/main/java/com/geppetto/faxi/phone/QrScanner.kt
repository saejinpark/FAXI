package com.geppetto.faxi.phone

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.geppetto.faxi.qr.QrCodec
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 차 화면 QR 찍기. 카메라 미리보기를 띄우고, 장면마다 밝기 값을 :core의 QrCodec으로 읽는다.
 * faxi:// 로 시작하는 글자를 처음 찾으면 onResult를 한 번 부르고 끝낸다(인터넷·구글 서비스 없이).
 */
@Composable
fun QrScanner(title: String, onResult: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (granted) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) { CameraPreview(onResult) }
            Text("차 화면의 QR이 네모 안에 다 들어오게 비춰 주세요.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("QR을 찍으려면 카메라 권한이 필요해요.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("카메라 허용") }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("닫기") }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResult by rememberUpdatedState(onResult)
    val done = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { image ->
                image.use {
                    if (done.get()) return@use
                    val plane = it.planes[0]
                    val buffer = plane.buffer
                    // 마지막 줄은 rowStride보다 짧을 수 있어 전체 크기로 잡고 있는 만큼 복사한다
                    val y = ByteArray(plane.rowStride * it.height)
                    buffer.get(y, 0, minOf(buffer.remaining(), y.size))
                    val text = QrCodec.decodeLuminance(y, it.width, it.height, plane.rowStride)
                    if (text != null && text.startsWith("faxi://") && done.compareAndSet(false, true)) {
                        ContextCompat.getMainExecutor(context).execute { latestOnResult(text) }
                    }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) future.get().unbindAll()
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
