package com.geppetto.faxi.message

import java.security.MessageDigest

/**
 * 손님이 폰에서 남기는 기사 후기의 태그. 차 화면에서 기사가 보는 앞에서 쓰지 않도록 후기는 폰에서만 쓴다.
 * 코드는 차·폰 약속이라 새 태그는 끝에만 붙인다.
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
 * `FAXIR1|가족|사람|영수증|별|태그,태그|보낸시각(초)|서명`. 서명은 한마디와 같은 열쇠·방식.
 */
data class ReviewEnvelope(
    val familyId: String,
    val memberId: String,
    val receiptId: String,
    val stars: Int,
    val tags: List<DriverTag>,
    val sentAt: Long,
    val signature: String,
) {
    fun toLine(): String = "${body(familyId, memberId, receiptId, stars, tags, sentAt)}|$signature"

    fun verify(secret: ByteArray, nowEpochSeconds: Long, windowSeconds: Long = MessageEnvelope.WINDOW_SECONDS): Boolean {
        if (kotlin.math.abs(nowEpochSeconds - sentAt) > windowSeconds) return false
        val expected = sign(secret, body(familyId, memberId, receiptId, stars, tags, sentAt))
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }

    companion object {
        const val VERSION = "FAXIR1"

        fun create(
            familyId: String, memberId: String, receiptId: String, stars: Int, tags: List<DriverTag>,
            secret: ByteArray, sentAt: Long,
        ): ReviewEnvelope = ReviewEnvelope(
            familyId, memberId, receiptId, stars, tags, sentAt,
            sign(secret, body(familyId, memberId, receiptId, stars, tags, sentAt)),
        )

        fun parse(line: String): ReviewEnvelope? {
            val p = line.trim().split('|')
            if (p.size != 8 || p[0] != VERSION) return null
            val stars = p[4].toIntOrNull()?.takeIf { it in 1..5 } ?: return null
            val tags = if (p[5].isEmpty()) emptyList() else p[5].split(',').map { DriverTag.of(it) ?: return null }
            val sentAt = p[6].toLongOrNull() ?: return null
            if (p[1].isEmpty() || p[2].isEmpty() || p[3].isEmpty() || p[7].isEmpty()) return null
            return ReviewEnvelope(p[1], p[2], p[3], stars, tags, sentAt, p[7])
        }

        private fun body(familyId: String, memberId: String, receiptId: String, stars: Int, tags: List<DriverTag>, sentAt: Long) =
            "$VERSION|$familyId|$memberId|$receiptId|$stars|${tags.joinToString(",") { it.code }}|$sentAt"
    }
}
