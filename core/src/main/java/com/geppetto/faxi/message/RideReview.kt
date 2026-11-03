package com.geppetto.faxi.message

import java.security.MessageDigest
import java.util.Base64

/**
 * 예전 폰이 보내던 기사 후기 태그. 지금 폰은 직접 쓴 글을 보내고 태그는 비워 둔다. 예전 후기를 읽으려고 남긴다.
 * 코드는 차·폰 약속이라 바꾸지 않는다.
 */
enum class DriverTag(val code: String, val text: String) {
    Route("ROUTE", "길 잘 앎"),
    Smooth("SMOOTH", "살살 운전"),
    Music("MUSIC", "선곡 최악"),
    Talk("TALK", "말 많음"),
    Quiet("QUIET", "잔소리 없음"),
    Again("AGAIN", "또 탈게요");

    companion object {
        fun of(code: String): DriverTag? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 후기 한 통(폰 → 차, 한마디와 같은 통로). 운행이 끝난 뒤 영수증을 찍은 폰에서 보낸다.
 * 지금 형식 `FAXIR2|가족|사람|영수증|별|태그,태그|글(Base64url)|보낸시각(초)|서명`. 글은 손님이 직접 쓴 후기 한두 줄.
 * 예전 형식 `FAXIR1|가족|사람|영수증|별|태그,태그|보낸시각(초)|서명`도 받는다(text = null). 서명은 한마디와 같은 열쇠·방식.
 */
data class ReviewEnvelope(
    val familyId: String,
    val memberId: String,
    val receiptId: String,
    val stars: Int,
    val tags: List<DriverTag>,
    val sentAt: Long,
    val signature: String,
    /** 직접 쓴 글. null이면 예전(FAXIR1) 형식 */
    val text: String? = null,
) {
    fun toLine(): String = "${body(familyId, memberId, receiptId, stars, tags, text, sentAt)}|$signature"

    fun verify(secret: ByteArray, nowEpochSeconds: Long, windowSeconds: Long = MessageEnvelope.WINDOW_SECONDS): Boolean {
        if (kotlin.math.abs(nowEpochSeconds - sentAt) > windowSeconds) return false
        val expected = sign(secret, body(familyId, memberId, receiptId, stars, tags, text, sentAt))
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }

    companion object {
        const val VERSION = "FAXIR2"
        private const val OLD_VERSION = "FAXIR1"
        /** 후기 글 최대 글자 수(차 화면에 두세 줄, 읽어 주기에 알맞은 길이) */
        const val MAX_TEXT = 80

        /** 줄바꿈은 띄어쓰기로, 앞뒤 공백을 지우고 MAX_TEXT까지 */
        fun cleanText(raw: String): String = raw.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT)

        fun create(
            familyId: String, memberId: String, receiptId: String, stars: Int, tags: List<DriverTag>,
            secret: ByteArray, sentAt: Long, text: String = "",
        ): ReviewEnvelope {
            val clean = cleanText(text)
            return ReviewEnvelope(
                familyId, memberId, receiptId, stars, tags, sentAt,
                sign(secret, body(familyId, memberId, receiptId, stars, tags, clean, sentAt)),
                clean,
            )
        }

        fun parse(line: String): ReviewEnvelope? {
            val p = line.trim().split('|')
            val v2 = p.size == 9 && p[0] == VERSION
            if (!v2 && !(p.size == 8 && p[0] == OLD_VERSION)) return null
            val stars = p[4].toIntOrNull()?.takeIf { it in 1..5 } ?: return null
            val tags = if (p[5].isEmpty()) emptyList() else p[5].split(',').map { DriverTag.of(it) ?: return null }
            val text = if (v2) decodeText(p[6]) ?: return null else null
            val rest = if (v2) 7 else 6
            val sentAt = p[rest].toLongOrNull() ?: return null
            val signature = p[rest + 1]
            if (p[1].isEmpty() || p[2].isEmpty() || p[3].isEmpty() || signature.isEmpty()) return null
            return ReviewEnvelope(p[1], p[2], p[3], stars, tags, sentAt, signature, text)
        }

        private fun encodeText(text: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

        private fun decodeText(s: String): String? = try {
            cleanText(String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            null
        }

        private fun body(familyId: String, memberId: String, receiptId: String, stars: Int, tags: List<DriverTag>, text: String?, sentAt: Long): String {
            val head = "$familyId|$memberId|$receiptId|$stars|${tags.joinToString(",") { it.code }}"
            return if (text == null) "$OLD_VERSION|$head|$sentAt" else "$VERSION|$head|${encodeText(text)}|$sentAt"
        }
    }
}
