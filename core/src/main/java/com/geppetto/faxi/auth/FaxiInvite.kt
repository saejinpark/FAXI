package com.geppetto.faxi.auth

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 가족 초대: 차 화면 QR에 담기는 내용. 폰이 찍으면 "나는 이 가족의 이 사람"과 비밀 열쇠를 저장한다.
 * 서버가 없으니 이 한 번이 차 → 폰으로 비밀을 건네는 유일한 순간이다(차 안에서, 주차 상태에서).
 *
 * 형식: faxi://join?f=<가족ID>&m=<멤버ID>&n=<이름>&c=<호출명>&s=<비밀 열쇠 Base32>
 */
data class FaxiInvite(
    val familyId: String,
    val memberId: String,
    val name: String,
    val callsign: String,
    /** Base32 */
    val secret: String,
) {
    fun toLink(): String =
        "faxi://join?f=${enc(familyId)}&m=${enc(memberId)}&n=${enc(name)}&c=${enc(callsign)}&s=${enc(secret)}"

    companion object {
        private const val PREFIX = "faxi://join?"

        /** QR에서 읽은 글자. FAXI 초대가 아니거나 빠진 값이 있으면 null */
        fun parse(link: String): FaxiInvite? {
            if (!link.startsWith(PREFIX)) return null
            val q = link.removePrefix(PREFIX).split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to dec(it.substring(i + 1))
            }.toMap()
            val secret = q["s"] ?: return null
            if (Base32.decode(secret)?.size != Totp.SECRET_BYTES) return null
            return FaxiInvite(
                familyId = q["f"] ?: return null,
                memberId = q["m"] ?: return null,
                name = q["n"] ?: return null,
                callsign = q["c"] ?: return null,
                secret = secret,
            )
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
    }
}

/**
 * 시연 가족(아빠·엄마·지우·할머니)의 고정 비밀 열쇠.
 * 차 앱 시연 데이터와 폰 앱 "시연 가족으로 등록"이 같은 열쇠를 쓰게 해서, QR 없이도 탑승 인증을 시연할 수 있다.
 * 실제 가족은 차가 Totp.newSecret()으로 새로 만든다. 이 열쇠는 누구나 알 수 있으니 시연에만 쓴다.
 */
object DemoFamily {
    const val FAMILY_ID = "demo-family"

    data class Person(val memberId: String, val name: String, val callsign: String)

    val people = listOf(
        Person("dad", "아빠", "PAXI"),
        Person("mom", "엄마", "MAXI"),
        Person("kid", "지우", "KIXI"),
        Person("grandma", "할머니", "NAXI"),
    )

    /** "faxi-demo:<멤버ID>"의 SHA-1(20바이트)을 Base32로 */
    fun secretOf(memberId: String): String =
        Base32.encode(MessageDigest.getInstance("SHA-1").digest("faxi-demo:$memberId".toByteArray()))

    fun inviteOf(p: Person): FaxiInvite = FaxiInvite(FAMILY_ID, p.memberId, p.name, p.callsign, secretOf(p.memberId))
}
