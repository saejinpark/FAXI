package com.geppetto.faxi.auth

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 탑승 인증 코드(TOTP, RFC 6238). 은행 OTP와 같은 방식이라 인터넷이 필요 없다.
 * - 가족 등록 때 차가 만든 비밀 열쇠를 폰이 QR로 받아 둔다.
 * - 폰은 비밀 열쇠 + 지금 시각(30초 단위)으로 4자리를 만들고, 차는 같은 계산으로 맞는지 본다.
 * - 차와 폰 시계가 조금 달라도 되도록 앞뒤 한 칸(±30초)까지 받아 준다.
 */
object Totp {
    const val STEP_SECONDS = 30L
    const val DIGITS = 4
    const val SECRET_BYTES = 20

    /** epochSeconds 시각의 코드. digits는 테스트(RFC 8자리 예시)용으로만 바꾼다. */
    fun code(secret: ByteArray, epochSeconds: Long, digits: Int = DIGITS): String {
        val counter = Math.floorDiv(epochSeconds, STEP_SECONDS)
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    /** 앞뒤 window칸(30초씩)까지 맞으면 통과 */
    fun verify(secret: ByteArray, code: String, epochSeconds: Long, window: Int = 1): Boolean {
        if (code.length != DIGITS || !code.all { it.isDigit() }) return false
        return (-window..window).any { step ->
            MessageDigest.isEqual(code(secret, epochSeconds + step * STEP_SECONDS).toByteArray(), code.toByteArray())
        }
    }

    /** 지금 코드가 바뀌기까지 남은 초(1~30) */
    fun secondsLeft(epochSeconds: Long): Long = STEP_SECONDS - Math.floorMod(epochSeconds, STEP_SECONDS)

    /** 가족 등록 때 차가 한 사람당 하나씩 만든다 */
    fun newSecret(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SECRET_BYTES).also(random::nextBytes)
}

/** 비밀 열쇠를 QR·파일에 글자로 담기 위한 Base32(RFC 4648, 패딩 없음, 대문자) */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
    }

    /** 잘못된 글자가 있으면 null */
    fun decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in text.trimEnd('=').uppercase()) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        // 정상 인코딩이면 남는 비트는 5개 미만이고 모두 0이다(글자가 하나 더 붙거나 빠진 경우를 막는다)
        if (bits >= 5 || (buffer and ((1 shl bits) - 1)) != 0) return null
        return out.toByteArray()
    }
}
