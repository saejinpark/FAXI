@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package com.geppetto.faxi.data

import com.geppetto.faxi.fare.FareLine
import com.geppetto.faxi.fare.Hvac
import com.geppetto.faxi.message.RideMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.UseSerializers

/**
 * 기사 등급 둘. 실제 면허증은 받지 않고 "운전해요" 자기 선언으로 정식 기사가 된다.
 * 예전 장부의 "명예 기사"(Honorary)는 정식 기사로 읽는다(운전 안 하는 어른도 손님으로는 똑같이 탄다).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class Grade(val label: String) {
    @JsonNames("Honorary") Full("정식 기사"),
    Learner("예비 기사"),
}

/** 기어 상태. 탑승·영수증·후기·정산은 Parked에서만 연다. */
@Serializable
/** 차 기어를 앱이 쓰는 세 가지로 줄인 것. R·N·D·1~9단은 모두 Driving(움직일 수 있는 상태). */
enum class Gear { Parked, Driving, Unknown }

@Serializable
data class Member(
    val id: String,
    val name: String,
    /** 자격증에 크게 찍히는 별명. 비어 있으면 "엄마 기사"처럼 이름으로 쓴다. */
    val nickname: String = "",
    val grade: Grade,
    val registered: LocalDate,
    /** 기사로 운행한 누적 거리 */
    val drivenKm: Int = 0,
    /** 예비 기사만: 면허를 딸 수 있게 되는 날 */
    val licenseEligible: LocalDate? = null,
    /** 사진 대신 쓰는 캐릭터(Avatars의 key). null이면 이름 첫 글자를 보여 준다. */
    val avatar: String? = null,
    /** 자격증 사진 파일 이름(차 내부 저장소). 폰 → 차 사진 전송 통로가 정해지면 쓴다. */
    val photoFile: String? = null,
    /**
     * 탑승 인증 비밀 열쇠(Base32). 가족 등록 때 차가 만들고 초대 QR로 그 사람 폰에 한 번 건넨다.
     * 차 밖으로는 그 QR 말고 나가지 않는다.
     */
    val secret: String = "",
    /** 가족 색 칸(0~3). 등록한 순서대로 정해서 자격증 띠·아바타 테두리에 쓴다. */
    val color: Int = 0,
    /** 가족에서 뺀 사람. 지난 영수증·기록의 이름은 남기고, 명단·탑승·폰 연결에서는 사라진다. */
    val removed: Boolean = false,
) {
    val canDrive: Boolean get() = grade == Grade.Full
    /** 자격증 제목: 별명, 없으면 "엄마 기사" */
    val cardTitle: String get() = nickname.ifBlank { "$name 기사" }
}

@Serializable
data class Receipt(
    val id: String,
    val driverId: String,
    val passengerId: String,
    val from: String,
    val to: String,
    val km: Int,
    val endedAt: LocalDateTime,
    val lines: List<FareLine>,
    /** 이번 운행 전부터 이 손님이 이 기사에게 밀린 돈 */
    val carriedOver: Int = 0,
    /** 밀린 돈이 며칠째였는지(이자는 없다. 영수증에 일수만 보인다) */
    val lateDays: Int = 0,
    /** 상계: 기사가 이 손님에게 진 빚에서 퉁친 금액 */
    val offset: Int = 0,
    /** 예비 기사가 손님이면 청구하지 않고 평생 장부에 쌓는다 */
    val toLifetime: Boolean = false,
    /** 승차감: 운행 시간을 고르게 나눈 칸마다 가장 큰 가감속(0~100, 속도 값에서). 하차 뒤 정지 그래프로만 보여 준다. */
    val shake: List<Int> = emptyList(),
    /** shake 안에서 급정거가 있었던 위치(인덱스) */
    val hardBrakes: List<Int> = emptyList(),
) {
    val fareTotal: Int get() = lines.sumOf { it.amount }
    /** 이번 하차로 손님이 기사에게 갚을 돈(밀린 돈 포함). 평생 장부 운행은 0 */
    val billed: Int get() = if (toLifetime) 0 else fareTotal + carriedOver - offset
}

/** debtor가 creditor에게 갚을 돈. 실제 송금은 가족끼리 하고 앱은 기록만 한다. 두 사람 사이에 하나만 있다. */
@Serializable
data class Debt(
    val debtorId: String,
    val creditorId: String,
    val amount: Int,
    /** 이 빚이 지금 금액으로 시작된 날. 연체 일수는 이 날짜로 계산한다. */
    val since: LocalDate,
) {
    fun daysOn(today: LocalDate): Int = ChronoUnit.DAYS.between(since, today).toInt().coerceAtLeast(0)
}

/** 장부 기록 한 줄(갚음, 탕감, 상계). 두 사람 사이 기록이면 a·b가 그 둘이다. */
@Serializable
data class LedgerEvent(
    val date: LocalDate,
    val text: String,
    val a: String? = null,
    val b: String? = null,
) {
    fun isBetween(x: String, y: String): Boolean = (a == x && b == y) || (a == y && b == x)
}

/** 예비 기사가 손님으로 탄 기록. 당장 청구하지 않고 쌓아 둔다. */
@Serializable
data class LifetimeLedger(
    val learnerId: String,
    val sinceYear: Int,
    val rides: Int,
    val km: Int,
    val amount: Int,
    /** 면허를 따서 공개했는지. 공개 뒤에도 장부는 남는다(이제 운전해서 갚아 나간다). */
    val revealed: Boolean = false,
)

/**
 * 요금이 붙는 차량 옵션의 지금 상태. 운행 중 화면에 켜짐/꺼짐으로 보여 준다(바뀔 때만 바뀌는 정지 표시).
 * 차에서는 VehicleFeed가 Vehicle SDK의 HVAC·Seat·Wiper·Window 값으로 채운다. 차량 값이 없으면(폰·폴드) 시연용 기본값.
 */
data class CabinState(
    val hvac: Hvac,
    val seatHeat: Boolean,
    val wiper: Boolean,
    val windowOpen: Boolean,
) {
    companion object {
        /** 시연 운행(기획서 예시)과 같은 상태: 냉방·엉따 켜짐 */
        val Demo = CabinState(Hvac.Cool, seatHeat = true, wiper = false, windowOpen = false)
    }
}

/** 운행 중 손님이 보낸 한마디와 받은 시각 */
data class PassengerMessage(val message: RideMessage, val at: LocalDateTime)

@Serializable
data class Review(val stars: Int, val tags: Set<String>)

/** 사진 대신 고르는 캐릭터. key는 저장용, label은 TalkBack이 읽는다. */
data class AvatarChoice(val key: String, val emoji: String, val label: String)

val Avatars = listOf(
    AvatarChoice("bear", "🐻", "곰"),
    AvatarChoice("fox", "🦊", "여우"),
    AvatarChoice("rabbit", "🐰", "토끼"),
    AvatarChoice("turtle", "🐢", "거북이"),
    AvatarChoice("tiger", "🐯", "호랑이"),
    AvatarChoice("panda", "🐼", "판다"),
    AvatarChoice("koala", "🐨", "코알라"),
    AvatarChoice("frog", "🐸", "개구리"),
    AvatarChoice("penguin", "🐧", "펭귄"),
    AvatarChoice("dog", "🐶", "강아지"),
    AvatarChoice("cat", "🐱", "고양이"),
    AvatarChoice("chick", "🐥", "병아리"),
)

fun avatarOf(key: String?): AvatarChoice? = Avatars.firstOrNull { it.key == key }

