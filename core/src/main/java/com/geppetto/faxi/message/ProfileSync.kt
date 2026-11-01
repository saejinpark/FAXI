package com.geppetto.faxi.message

import com.geppetto.faxi.qr.Balance
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 동기화 한 통(폰 → 차, 한마디와 같은 통로·같은 열쇠).
 * 폰은 바뀐 프로필(자격증 별명, 사진)을 싣고, 차는 답으로 그 사람의 정산 현황과 영수증을 돌려준다([SyncPayload]).
 * `FAXIS1|가족|사람|보낸시각(초)|별명|사진|서명`. 별명·사진은 Base64(URL), 바뀌지 않았으면 "-".
 * 사진은 폰이 3:4로 잘라 240×320 JPEG로 줄여 보낸다(수십 KB, 한 줄로 충분).
 */
class SyncEnvelope(
    val familyId: String,
    val memberId: String,
    val sentAt: Long,
    /** 바뀐 자격증 별명. null이면 그대로 */
    val nickname: String?,
    /** 바뀐 사진(JPEG). null이면 그대로 */
    val photo: ByteArray?,
    val signature: String,
) {
    fun toLine(): String = "${body(familyId, memberId, sentAt, nickname, photo)}|$signature"

    fun verify(secret: ByteArray, nowEpochSeconds: Long, windowSeconds: Long = MessageEnvelope.WINDOW_SECONDS): Boolean {
        if (kotlin.math.abs(nowEpochSeconds - sentAt) > windowSeconds) return false
        val expected = sign(secret, body(familyId, memberId, sentAt, nickname, photo))
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }

    companion object {
        const val VERSION = "FAXIS1"
        /** 사진 한 장 상한(240×320 JPEG면 넉넉하다). 넘으면 받지 않는다 */
        const val MAX_PHOTO_BYTES = 200_000
        const val MAX_NICKNAME = 14

        fun create(
            familyId: String, memberId: String, nickname: String?, photo: ByteArray?, secret: ByteArray, sentAt: Long,
        ) = SyncEnvelope(familyId, memberId, sentAt, nickname, photo, sign(secret, body(familyId, memberId, sentAt, nickname, photo)))

        fun parse(line: String): SyncEnvelope? {
            val p = line.trim().split('|')
            if (p.size != 7 || p[0] != VERSION) return null
            if (p[1].isEmpty() || p[2].isEmpty() || p[6].isEmpty()) return null
            val sentAt = p[3].toLongOrNull() ?: return null
            val nickname = if (p[4] == "-") null else runCatching { String(b64d(p[4]), Charsets.UTF_8) }.getOrNull() ?: return null
            if (nickname != null && nickname.length > MAX_NICKNAME) return null
            val photo = if (p[5] == "-") null else runCatching { b64d(p[5]) }.getOrNull() ?: return null
            if (photo != null && photo.size > MAX_PHOTO_BYTES) return null
            return SyncEnvelope(p[1], p[2], sentAt, nickname, photo, p[6])
        }

        private fun body(familyId: String, memberId: String, sentAt: Long, nickname: String?, photo: ByteArray?) =
            "$VERSION|$familyId|$memberId|$sentAt|${nickname?.let { b64(it.toByteArray(Charsets.UTF_8)) } ?: "-"}|${photo?.let(::b64) ?: "-"}"
    }
}

/**
 * 차의 답: 그 사람의 지금 정산 현황과 최근 영수증(영수증 QR과 같은 링크). 폰은 이걸로 영수증 탭과 정산 탭을 맞춘다.
 * riding이면 폰은 하차 직후 영수증을 받으려고 조금 자주 다시 묻는다.
 */
@Serializable
data class SyncPayload(
    /** 이 사람이 지금 운행 중인 차에 타 있다(기사든 손님이든) */
    val riding: Boolean,
    /** 보낸 프로필을 차가 반영했다. false면(운행 중 등) 폰이 다음에 다시 보낸다 */
    val profileApplied: Boolean,
    val balances: List<Balance>,
    /** 이 사람이 손님이었던 최근 영수증 링크(faxi://receipt?d=…), 최신이 앞 */
    val receipts: List<String>,
)

/** 답 한 줄: `SYNC|<Deflate한 JSON의 Base64>`. 틀리면 null */
object SyncReply {
    private const val PREFIX = "SYNC|"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(p: SyncPayload): String = PREFIX + b64(deflate(json.encodeToString(SyncPayload.serializer(), p).toByteArray(Charsets.UTF_8)))

    fun decode(line: String): SyncPayload? {
        if (!line.startsWith(PREFIX)) return null
        return runCatching {
            json.decodeFromString(SyncPayload.serializer(), String(inflate(b64d(line.removePrefix(PREFIX).trim())), Charsets.UTF_8))
        }.getOrNull()
    }
}

private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
private fun b64d(s: String): ByteArray = Base64.getUrlDecoder().decode(s)

private fun deflate(bytes: ByteArray): ByteArray {
    val d = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bytes); finish() }
    val out = ByteArrayOutputStream()
    val buf = ByteArray(1024)
    while (!d.finished()) out.write(buf, 0, d.deflate(buf))
    d.end()
    return out.toByteArray()
}

private fun inflate(bytes: ByteArray): ByteArray {
    val i = Inflater(true).apply { setInput(bytes) }
    val out = ByteArrayOutputStream()
    val buf = ByteArray(1024)
    while (!i.finished()) {
        val n = i.inflate(buf)
        if (n == 0 && (i.needsInput() || i.needsDictionary())) break
        out.write(buf, 0, n)
    }
    i.end()
    return out.toByteArray()
}
