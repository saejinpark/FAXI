package com.geppetto.faxi.qr

import com.geppetto.faxi.fare.FareCodes
import com.geppetto.faxi.fare.FareLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 영수증 QR에 담는 내용: 영수증 한 장 + 그 순간 손님의 정산 현황.
 * 차 → 폰으로만 가는 정보다(폰은 차 장부를 바꾸지 못한다). 서버가 없으니 폰의 정산 현황은 "찍은 시각 기준" 사본이다.
 * QR이 성겨야(칸이 적어야) 잘 찍히므로 이름표 없는 배열로 싣고(toWire), 영수증 줄은 기호로 바꾼다(FareCodes).
 * @Serializable은 폰이 영수증을 저장·복원할 때를 위해 남겨 둔다.
 *
 * 링크 형식: faxi://receipt?d=<배열 JSON을 Deflate로 줄여 Base64(URL용, 패딩 없음)>
 * 한글이 UTF-8·Base64를 거치며 두 번 불어나서, 줄이지 않으면 QR이 너무 촘촘해진다.
 */
@Serializable
data class ReceiptTicket(
    @SerialName("f") val familyId: String,
    @SerialName("i") val id: String,
    /** 이 영수증의 손님. 폰은 자기 영수증만 받는다. */
    @SerialName("pm") val passengerId: String,
    @SerialName("p") val passenger: String,
    /** "아빠 · PAXI" */
    @SerialName("d") val driver: String,
    /** "우리집 → 이마트 성수점" */
    @SerialName("r") val route: String,
    /** 하차 시각 "2026-10-27T11:07" */
    @SerialName("t") val endedAt: String,
    @SerialName("l") val lines: List<FareLine>,
    @SerialName("ft") val fareTotal: Int,
    /** 이번 하차로 갚을 돈(밀린 돈 포함). 평생 장부 운행은 0 */
    @SerialName("b") val billed: Int,
    @SerialName("c") val carriedOver: Int = 0,
    @SerialName("o") val offset: Int = 0,
    @SerialName("lt") val toLifetime: Boolean = false,
    /** 하차 순간 이 손님의 받을 돈·갚을 돈 */
    @SerialName("bal") val balances: List<Balance> = emptyList(),
) {
    fun toLink(): String = PREFIX + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(deflate(toWire().toString().toByteArray(Charsets.UTF_8)))

    /**
     * QR용 압축 형식: 이름표 없는 JSON 배열(자리로 뜻을 정한다) + 영수증 줄은 기호.
     * [가족, 영수증, 손님ID, 손님, 기사, 경로, 하차시각, 이번요금, 갚을돈, 이월, (빈 자리: 예전 연체 이자, 늘 0), 상계, 평생장부(0/1),
     *  [[기호, 설명, 금액], ...], [[상대, 받을돈(0/1), 금액, 분쟁(0/1)], ...]]
     * 자리를 바꾸면 예전 영수증 QR을 못 읽으니 새 값은 끝에만 붙인다.
     */
    private fun toWire(): JsonArray = buildJsonArray {
        add(familyId); add(id); add(passengerId); add(passenger); add(driver); add(route); add(endedAt)
        add(fareTotal); add(billed); add(carriedOver); add(0); add(offset); add(if (toLifetime) 1 else 0)
        addJsonArray {
            lines.map(FareCodes::shorten).forEach { l -> addJsonArray { add(l.label); add(l.detail); add(l.amount) } }
        }
        addJsonArray {
            balances.forEach { b -> addJsonArray { add(b.other); add(if (b.owedToMe) 1 else 0); add(b.amount); add(if (b.disputed) 1 else 0) } }
        }
    }

    companion object {
        private const val PREFIX = "faxi://receipt?d="

        private fun fromWire(a: JsonArray): ReceiptTicket {
            fun str(i: Int) = a[i].jsonPrimitive.content
            fun int(i: Int) = a[i].jsonPrimitive.int
            return ReceiptTicket(
                familyId = str(0), id = str(1), passengerId = str(2), passenger = str(3), driver = str(4),
                route = str(5), endedAt = str(6), fareTotal = int(7), billed = int(8), carriedOver = int(9),
                offset = int(11), toLifetime = int(12) == 1,
                lines = a[13].jsonArray.map { e ->
                    val l = e.jsonArray
                    FareCodes.expand(FareLine(l[0].jsonPrimitive.content, l[1].jsonPrimitive.contentOrNull, l[2].jsonPrimitive.int))
                },
                balances = a[14].jsonArray.map { e ->
                    val b = e.jsonArray
                    Balance(b[0].jsonPrimitive.content, b[1].jsonPrimitive.int == 1, b[2].jsonPrimitive.int, b[3].jsonPrimitive.int == 1)
                },
            )
        }

        /** FAXI 영수증 링크가 아니거나 깨졌으면 null */
        fun parse(link: String): ReceiptTicket? {
            if (!link.startsWith(PREFIX)) return null
            return try {
                val text = String(inflate(Base64.getUrlDecoder().decode(link.removePrefix(PREFIX))), Charsets.UTF_8)
                fromWire(Json.parseToJsonElement(text).jsonArray)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** 정산 현황 한 줄: other와의 사이에서 owedToMe면 내가 받을 돈, 아니면 내가 갚을 돈 */
@Serializable
data class Balance(
    @SerialName("n") val other: String,
    @SerialName("in") val owedToMe: Boolean,
    @SerialName("a") val amount: Int,
    @SerialName("dp") val disputed: Boolean = false,
)

private fun deflate(bytes: ByteArray): ByteArray {
    val d = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bytes); finish() }
    val out = ByteArrayOutputStream()
    val buf = ByteArray(512)
    while (!d.finished()) out.write(buf, 0, d.deflate(buf))
    d.end()
    return out.toByteArray()
}

private fun inflate(bytes: ByteArray): ByteArray {
    val i = Inflater(true).apply { setInput(bytes) }
    val out = ByteArrayOutputStream()
    val buf = ByteArray(512)
    while (!i.finished()) {
        val n = i.inflate(buf)
        if (n == 0 && (i.needsInput() || i.needsDictionary())) error("영수증 링크가 잘렸어요")
        out.write(buf, 0, n)
    }
    i.end()
    return out.toByteArray()
}
