package com.geppetto.faxi.message

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 손님 폰 → 운행 중인 차로 보내는 한마디. 자유 글은 받지 않고 정해진 문장만 보낸다:
 * 운전자는 주행 중 긴 글을 읽지 않고, 아이콘과 한 줄만 힐끗 본다.
 * 순서·코드는 차·폰이 같이 쓴다. 코드를 바꾸면 예전 폰 앱과 말이 안 통하니 새 문장은 끝에만 붙인다.
 */
enum class RideMessage(val code: String, val text: String) {
    Slow("SLOW", "천천히 가 주세요"),
    Stop("STOP", "잠깐 세워 주세요"),
    Cold("COLD", "조금 추워요"),
    Hot("HOT", "조금 더워요"),
    Music("MUSIC", "음악 바꿔 주세요"),
    Thanks("GOOD", "기사님 최고예요");

    companion object {
        fun of(code: String): RideMessage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 한마디 한 통. 한 줄 글자로 오간다(같은 Wi-Fi의 TCP).
 * `FAXI1|가족|사람|코드|보낸시각(초)|서명` — 서명은 그 사람의 비밀 열쇠(탑승 코드와 같은 열쇠)로 만든 HMAC-SHA256 앞 16바이트.
 * 열쇠는 차와 그 사람 폰에만 있으니, 같은 Wi-Fi의 다른 사람이 가족인 척 보낼 수 없다.
 */
data class MessageEnvelope(
    val familyId: String,
    val memberId: String,
    val message: RideMessage,
    val sentAt: Long,
    val signature: String,
) {
    fun toLine(): String = "${body(familyId, memberId, message, sentAt)}|$signature"

    /** 서명이 맞고 보낸 시각이 지금에서 앞뒤 [windowSeconds] 안인지(오래된 통을 다시 보내는 것을 막는다) */
    fun verify(secret: ByteArray, nowEpochSeconds: Long, windowSeconds: Long = WINDOW_SECONDS): Boolean {
        if (kotlin.math.abs(nowEpochSeconds - sentAt) > windowSeconds) return false
        val expected = sign(secret, body(familyId, memberId, message, sentAt))
        return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
    }

    companion object {
        const val VERSION = "FAXI1"
        const val WINDOW_SECONDS = 60L

        fun create(familyId: String, memberId: String, message: RideMessage, secret: ByteArray, sentAt: Long): MessageEnvelope =
            MessageEnvelope(familyId, memberId, message, sentAt, sign(secret, body(familyId, memberId, message, sentAt)))

        /** 모양이 틀리면 null. 서명 확인은 verify에서 한다. */
        fun parse(line: String): MessageEnvelope? {
            val p = line.trim().split('|')
            if (p.size != 6 || p[0] != VERSION) return null
            val message = RideMessage.of(p[3]) ?: return null
            val sentAt = p[4].toLongOrNull() ?: return null
            if (p[1].isEmpty() || p[2].isEmpty() || p[5].isEmpty()) return null
            return MessageEnvelope(p[1], p[2], message, sentAt, p[5])
        }

        private fun body(familyId: String, memberId: String, message: RideMessage, sentAt: Long) =
            "$VERSION|$familyId|$memberId|${message.code}|$sentAt"
    }
}

/** 그 사람 비밀 열쇠로 만든 HMAC-SHA256 앞 16바이트(Base64). 한마디와 후기가 같이 쓴다. */
internal fun sign(secret: ByteArray, body: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
    val full = mac.doFinal(body.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(full.copyOf(16))
}

/** 차가 돌려주는 답 한 줄. 폰은 이걸 보고 손님에게 알려 준다. */
enum class MessageReply(val wire: String) {
    /** 기사 화면에 떴다 */
    Shown("OK"),
    /** 서명·가족·시각이 틀림 */
    Rejected("BAD"),
    /** 이 사람은 지금 이 차의 손님이 아니다(운행 중이 아니거나 다른 손님) */
    NotRiding("NOTRIDING"),
    /** 방금 보냈다. 조금 뒤에 */
    TooSoon("WAIT"),
    /** 후기: 이 운행은 이미 후기를 받았다 */
    AlreadyReviewed("DONE"),
    /** 후기: 차에 없는 운행이거나 이 사람이 손님이 아닌 운행 */
    UnknownRide("NORIDE");

    companion object {
        fun of(wire: String): MessageReply? = entries.firstOrNull { it.wire == wire.trim() }
    }
}

/** 차와 폰이 만나는 곳: 같은 Wi-Fi에서 NSD(mDNS)로 찾는 서비스 이름과 포트 */
object MessageWire {
    const val SERVICE_TYPE = "_faxi._tcp."
    const val PORT = 47800
    /** 한 사람이 한마디를 보낸 뒤 다음 한마디까지 기다리는 시간(아이가 연타해도 기사 화면이 계속 바뀌지 않게) */
    const val MIN_INTERVAL_SECONDS = 10L
}
