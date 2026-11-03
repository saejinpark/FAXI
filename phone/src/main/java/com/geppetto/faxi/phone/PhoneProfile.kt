package com.geppetto.faxi.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.message.SyncEnvelope
import com.geppetto.faxi.message.SyncPayload
import com.geppetto.faxi.ui.components.AvatarCircle
import com.geppetto.faxi.ui.components.FaxiCard
import com.geppetto.faxi.ui.components.FaxiPrimaryButton
import com.geppetto.faxi.ui.components.FaxiSecondaryButton
import com.geppetto.faxi.ui.components.SectionTitle
import com.geppetto.faxi.ui.components.rememberPhoto
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * 프로필과 동기화(폰 ↔ 차, 같은 Wi-Fi).
 * - 폰 → 차: 바뀐 자격증 별명과 사진. 차는 그 사람이 운행 중이 아닐 때만 반영하고, 반영 못 하면 다음에 다시 보낸다.
 * - 차 → 폰: 내 정산 현황과 내가 손님이었던 최근 영수증. 영수증 QR을 찍지 않아도 영수증이 들어온다.
 * - 언제: 앱이 앞에 있는 동안 1분마다(앱을 열 때 바로 한 번), 운행 중이면 15초마다(하차 직후 영수증을 바로 받으려고),
 *   그리고 설정의 "지금 맞추기". 차가 없는 곳에서는 조용히 넘어간다.
 */

private val SyncTime = DateTimeFormatter.ofPattern("M.d HH:mm")

/**
 * 한 번 맞춘다. 차를 못 찾으면 null.
 * manual(사람이 누름)일 때만 "맞추는 중", "못 찾았어요"를 보여 준다. 저절로 맞추다 못 찾으면 조용히 넘어간다(차 밖에서는 늘 못 찾으니까).
 */
internal suspend fun syncOnce(me: FaxiInvite, store: PhoneStore, sender: MessageSender, manual: Boolean = true): SyncPayload? {
    val dirty = store.profileDirty
    val photo = if (dirty) store.photo?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() } else null
    if (manual) store.syncNote = "차와 맞추는 중이에요."
    val p = sender.sync(me, if (dirty) store.nickname else null, photo)
    if (p == null) {
        if (manual) store.syncNote = "차를 찾지 못했어요. 차와 같은 Wi-Fi에서 다시 맞춰요."
        return null
    }
    val added = store.applySync(p, System.currentTimeMillis() / 1000)
    store.syncNote = when {
        added > 0 -> "새 영수증 ${added}장을 받았어요."
        dirty && !p.profileApplied -> "운행이 끝나면 프로필을 차에 보낼게요."
        else -> null
    }
    return p
}

/** 앱이 앞에 있는 동안 저절로 맞춘다. 운행 중이면 자주(하차 직후 영수증), 아니면 1분마다. */
@Composable
internal fun AutoSync(me: FaxiInvite, store: PhoneStore, sender: MessageSender) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(me) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val p = syncOnce(me, store, sender, manual = false)
                delay(if (p?.riding == true) 15_000 else 60_000)
            }
        }
    }
}

/** 설정 맨 위: 내 사진·이름·자격증 별명 + 차와 맞춘 시각, "지금 맞추기" */
@Composable
internal fun ProfileCard(me: FaxiInvite, store: PhoneStore, sender: MessageSender) {
    val c = Faxi.colors
    val t = Faxi.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nickname by rememberSaveable(store.nickname) { mutableStateOf(store.nickname) }

    fun setPhoto(bitmap: Bitmap?) {
        val jpeg = bitmap?.let(::toProfileJpeg) ?: return
        store.updateProfile(nickname, Base64.getEncoder().encodeToString(jpeg))
        scope.launch { syncOnce(me, store, sender) }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { setPhoto(it) }
    // 이 앱은 영수증 QR 때문에 카메라 권한을 선언해서, 사진 찍기 앱을 부르기 전에도 권한이 있어야 한다
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) takePicture.launch(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { setPhoto(decodeSmall(context, it)) } }

    FaxiCard {
        SectionTitle("내 프로필")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space4)) {
            AvatarCircle(store.avatar, me.name.take(1), FaxiDimens.controlHeight + FaxiDimens.space6, t.title, c.family(me.color), photo = rememberPhoto(store.photo))
            Column(Modifier.weight(1f)) {
                Text(me.name, style = t.headline, color = c.ink)
                Text("사진과 별명은 차 화면의 기사 자격증에 나와요.", style = t.caption, color = c.inkMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3)) {
            FaxiSecondaryButton(
                "사진 찍기",
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePicture.launch(null)
                    else askCamera.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f),
            )
            FaxiSecondaryButton(
                "앨범에서",
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it.take(SyncEnvelope.MAX_NICKNAME) },
            modifier = Modifier.fillMaxWidth().heightIn(min = FaxiDimens.targetMin),
            textStyle = t.body,
            singleLine = true,
            label = { Text("자격증 별명", style = t.caption) },
            placeholder = { Text("새벽 픽업 ${me.name}", style = t.body, color = c.inkMuted) },
            shape = RoundedCornerShape(FaxiDimens.radiusSm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                focusedBorderColor = c.accent, unfocusedBorderColor = c.lineStrong, cursorColor = c.accentInk,
                focusedLabelColor = c.accentInk, unfocusedLabelColor = c.inkMuted,
            ),
        )
        FaxiPrimaryButton(
            "저장하고 차에 보내기",
            enabled = nickname.trim() != store.nickname || store.profileDirty,
            onClick = {
                store.updateProfile(nickname, store.photo)
                scope.launch { syncOnce(me, store, sender) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // 상태 줄은 늘 한 줄을 둔다(생겼다 사라지며 버튼을 밀지 않게)
        val synced = store.syncedAt?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(SyncTime) }
        Text(
            store.syncNote ?: when {
                store.profileDirty -> "다음에 차와 이어지면 보내요."
                synced != null -> "$synced 차와 맞췄어요."
                else -> "차와 같은 Wi-Fi에 있으면 저절로 맞춰요."
            },
            style = t.caption, color = c.inkMuted,
        )
        FaxiSecondaryButton("지금 맞추기", onClick = { scope.launch { syncOnce(me, store, sender) } }, modifier = Modifier.fillMaxWidth())
    }
}

/** 프로필 사진: 가운데를 3:4로 잘라 240×320 JPEG(차 자격증 사진 칸과 같은 비율, 수십 KB) */
private fun toProfileJpeg(src: Bitmap): ByteArray {
    val targetRatio = 3f / 4f
    val (w, h) = if (src.width.toFloat() / src.height > targetRatio) {
        (src.height * targetRatio).toInt() to src.height
    } else {
        src.width to (src.width / targetRatio).toInt()
    }
    val cropped = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
    val scaled = Bitmap.createScaledBitmap(cropped, 240, 320, true)
    return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}

/** 앨범 사진을 작게(긴 변 약 1000px) 읽는다. 큰 원본을 통째로 올리지 않게 */
private fun decodeSmall(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1000) sample *= 2
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()
