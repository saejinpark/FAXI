@file:UseSerializers(LocalDateTimeSerializer::class)

package com.geppetto.faxi.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 차에 저장하는 장부 전체. 차가 장부의 주인이라 이 파일 하나가 가족 데이터의 원본이다.
 * 필드를 추가할 때는 기본값을 줘야 예전 파일도 읽힌다(ignoreUnknownKeys + 기본값).
 */
@Serializable
data class FaxiSnapshot(
    val version: Int = 1,
    /** 가족 ID. 초대·영수증 QR에 담겨, 폰이 "어느 차의 가족인지" 안다. 예전 장부는 시연 가족으로 본다. */
    val familyId: String = com.geppetto.faxi.auth.DemoFamily.FAMILY_ID,
    val members: List<Member>,
    val driverId: String,
    val debts: List<Debt>,
    val events: List<LedgerEvent>,
    val receipts: List<Receipt>,
    val lifetime: List<LifetimeLedger>,
    val requests: List<SettlementRequest> = emptyList(),
    val reviews: Map<String, Review> = emptyMap(),
    /** 이미 소리 내어 읽은 후기(영수증 id). 한 후기는 한 번만 읽는다 */
    val reviewsReadAloud: Set<String> = emptySet(),
    val passengerReviews: Map<String, Review> = emptyMap(),
    /** 운행 중에 앱이 꺼졌다 켜져도 이어 가도록 */
    val ridePassengerId: String? = null,
    val rideStartedAt: LocalDateTime? = null,
    /** 운행 중 출발지(내비 주소). 앱이 다시 켜져도 영수증에 남게 */
    val rideFrom: String? = null,
    /** 운행 중 경유지 수 */
    val rideWaypoints: Int = 0,
    /** 도착 화면을 닫기 전에 앱이 꺼졌다 켜져도 다시 띄우도록 */
    val arrivalReceiptId: String? = null,
    val nextId: Int = 100,
)

val FaxiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun FaxiSnapshot.toJson(): String = FaxiJson.encodeToString(FaxiSnapshot.serializer(), this)
fun faxiSnapshotFromJson(text: String): FaxiSnapshot = FaxiJson.decodeFromString(FaxiSnapshot.serializer(), text)

/** "2026-10-27" */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** "2026-10-27T09:09:00" */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
