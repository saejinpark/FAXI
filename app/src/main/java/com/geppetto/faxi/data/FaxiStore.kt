package com.geppetto.faxi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.Totp
import com.geppetto.faxi.fare.FakeTrip
import com.geppetto.faxi.fare.FareLine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 화면이 보는 상태 창구이자 차 안 장부의 주인.
 * 운행 요금은 아직 기획서 영수증 예시를 그대로 쓴다. 차량 데이터로 계산하는 요금 엔진과
 * TOTP 탑승 인증은 다음 단계에서 붙인다.
 *
 * SwiftUI의 @Observable 클래스처럼, mutableStateOf로 만든 값이 바뀌면 그 값을 읽은 화면이 다시 그려진다.
 * 값이 바뀔 때마다 onChanged를 불러서 FaxiApp이 파일에 저장한다.
 *
 * @param clock 오늘·지금. 테스트에서 날짜를 고정하려고 바꿔 끼운다.
 */
class FaxiStore(
    snapshot: FaxiSnapshot,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) {
    val members = mutableStateListOf<Member>().apply { addAll(snapshot.members.map(::withSecret)) }

    var driverId by mutableStateOf(snapshot.driverId)
        private set
    var passengerId by mutableStateOf(snapshot.ridePassengerId)
        private set
    /** 미터기를 켠 시각. 운행 중 화면에 "출발 09:12"로 보여 준다(움직이는 경과 시간은 쓰지 않는다). */
    var rideStartedAt by mutableStateOf(snapshot.rideStartedAt)
        private set
    /** 기어. 지금은 운행 중 손님이 있으면 Driving으로 본다. 차량에서는 Vehicle SDK 기어 값으로 바꾼다. */
    val gear: Gear get() = if (passengerId != null) Gear.Driving else Gear.Parked

    val debts = mutableStateListOf<Debt>().apply { addAll(snapshot.debts) }
    val events = mutableStateListOf<LedgerEvent>().apply { addAll(snapshot.events) }
    /** 운행 기록, 최신이 앞 */
    val receipts = mutableStateListOf<Receipt>().apply { addAll(snapshot.receipts) }
    val lifetime = mutableStateListOf<LifetimeLedger>().apply { addAll(snapshot.lifetime) }
    val requests = mutableStateListOf<SettlementRequest>().apply { addAll(snapshot.requests) }
    val reviews = mutableStateMapOf<String, Review>().apply { putAll(snapshot.reviews) }

    /** 차량 옵션 상태. 저장하지 않는다(차가 늘 지금 값을 알려 준다). */
    var cabin by mutableStateOf(CabinState(ac = true, seatHeat = true, wiper = false, windowOpen = false))
        private set

    /** 방금 끝난 운행. null이 아니면 도착 화면(영수증 공개)을 띄운다. */
    var arrival by mutableStateOf(snapshot.arrivalReceiptId?.let { id -> snapshot.receipts.firstOrNull { it.id == id } })
        private set

    private var nextId = snapshot.nextId

    /** 값이 바뀔 때마다 불린다. FaxiApp이 저장에 쓴다. */
    var onChanged: (() -> Unit)? = null

    private fun changed() = onChanged?.invoke()

    val today: LocalDate get() = clock().toLocalDate()

    fun snapshot(): FaxiSnapshot = FaxiSnapshot(
        members = members.toList(),
        driverId = driverId,
        debts = debts.toList(),
        events = events.toList(),
        receipts = receipts.toList(),
        lifetime = lifetime.toList(),
        requests = requests.toList(),
        reviews = reviews.toMap(),
        ridePassengerId = passengerId,
        rideStartedAt = rideStartedAt,
        arrivalReceiptId = arrival?.id,
        nextId = nextId,
    )

    fun member(id: String): Member = members.first { it.id == id }
    fun name(id: String): String = member(id).name

    fun debt(debtorId: String, creditorId: String): Debt? =
        debts.firstOrNull { it.debtorId == debtorId && it.creditorId == creditorId }

    /** 빚이 생긴 지 며칠째인지(오늘 생겼으면 0) */
    fun daysOf(debt: Debt): Int = debt.daysOn(today)

    /** 두 사람 사이 운행 기록, 최신이 앞 */
    fun receiptsBetween(a: String, b: String): List<Receipt> =
        receipts.filter { (it.driverId == a && it.passengerId == b) || (it.driverId == b && it.passengerId == a) }

    fun receipt(id: String): Receipt? = receipts.firstOrNull { it.id == id }

    /** 이 사람이 기사였던 운행 중 후기가 남은 것, 최신이 앞 */
    fun reviewsOf(driverId: String): List<Pair<Receipt, Review>> =
        receipts.filter { it.driverId == driverId }.mapNotNull { r -> reviews[r.id]?.let { r to it } }

    /** 기사로 받은 평균 별점. 후기가 없으면 null */
    fun driverRating(driverId: String): Double? =
        reviewsOf(driverId).map { it.second.stars }.takeIf { it.isNotEmpty() }?.average()

    /** 이 사람이 손님이나 기사로 탄 운행, 최신이 앞 */
    fun ridesOf(memberId: String): List<Receipt> =
        receipts.filter { it.driverId == memberId || it.passengerId == memberId }

    fun lifetimeOf(memberId: String): LifetimeLedger? = lifetime.firstOrNull { it.learnerId == memberId }

    /** 이 손님이 이 기사 차를 몇 번 탔는지(지금 운행 전까지) */
    fun ridesTogether(driverId: String, passengerId: String): Int =
        receipts.count { it.driverId == driverId && it.passengerId == passengerId }

    /** 사진 대신 쓸 캐릭터 바꾸기 */
    fun setAvatar(memberId: String, avatar: String) {
        val i = members.indexOfFirst { it.id == memberId }
        members[i] = members[i].copy(avatar = avatar)
        changed()
    }

    fun selectDriver(id: String) {
        require(member(id).canDrive)
        driverId = id
        changed()
    }

    /**
     * 탑승 인증: 손님 폰에 뜬 4자리가 그 손님 비밀 열쇠로 지금(±30초) 만든 코드와 같은지.
     * 인터넷 없이 차 안에서 끝난다. 다른 가족의 코드나 1분 넘은 코드는 통과하지 못한다.
     */
    fun verifyBoardingCode(passengerId: String, code: String): Boolean {
        val secret = Base32.decode(member(passengerId).secret) ?: return false
        val now = clock().atZone(ZoneId.systemDefault()).toEpochSecond()
        return Totp.verify(secret, code, now)
    }

    fun startRide(passengerId: String) {
        require(passengerId != driverId)
        this.passengerId = passengerId
        rideStartedAt = clock().withSecond(0).withNano(0)
        changed()
    }

    /**
     * 하차. 이번 운행의 차량 값을 요금 엔진(FareMeter)에 돌려 영수증을 만들고 장부에 반영한다.
     * 지금은 차량 값 대신 기획서 예시 운행(FakeTrip.planExample)을 쓴다. 밤 10시~새벽 4시에 타면 심야 할증이 붙는다.
     * - 손님이 기사에게 밀린 돈은 이월하고 연체 이자(하루 0.1%, 1원 미만 버림, 원금 이하)를 붙인다.
     * - 상계: 기사가 이 손님에게 진 빚이 있으면 먼저 퉁친다.
     * - 예비 기사 손님은 청구하지 않고 평생 장부에 쌓는다.
     */
    fun endRide(): Receipt {
        val passenger = checkNotNull(passengerId)
        val meter = FakeTrip.run(FakeTrip.planExample(rideStartedAt ?: clock()))
        val brakes = meter.hardBrakes
        val base = Receipt(
            id = "r${nextId++}", driverId = driverId, passengerId = passenger,
            from = "우리집", to = "이마트 성수점", km = (meter.distanceM / 1000).roundToInt(),
            endedAt = clock().withSecond(0).withNano(0), lines = meter.lines(),
            reviewQuestion = if (brakes > 0) "오늘 급정거 ${brakes}번, 괜찮으셨나요?" else "오늘 운행 어떠셨나요?",
            // 승차감 그래프는 아직 가짜 모양. 급정거 점만 엔진이 센 횟수에 맞춘다.
            shake = sampleShake(nextId, brakeAt = 82),
            hardBrakes = if (brakes > 0) listOf(82) else emptyList(),
        )
        val receipt = if (member(passenger).grade == Grade.Learner) {
            addToLifetime(passenger, base.fareTotal, base.km)
            base.copy(toLifetime = true)
        } else {
            settleRide(base)
        }
        receipts.add(0, receipt)
        arrival = receipt
        passengerId = null
        rideStartedAt = null
        changed()
        return receipt
    }

    private fun settleRide(base: Receipt): Receipt {
        val p = base.passengerId
        val d = base.driverId
        val old = debt(p, d)
        val carried = old?.amount ?: 0
        val days = old?.let(::daysOf) ?: 0
        val interest = minOf(carried * days / 1000, carried)
        val owedBack = debt(d, p)
        val gross = base.fareTotal + carried + interest
        val offset = minOf(owedBack?.amount ?: 0, gross)
        val receipt = base.copy(carriedOver = carried, lateDays = days, lateInterest = interest, offset = offset)

        if (old != null) debts.remove(old)
        if (owedBack != null && offset > 0) {
            val left = owedBack.amount - offset
            if (left <= 0) debts.remove(owedBack) else debts[debts.indexOf(owedBack)] = owedBack.copy(amount = left)
            events.add(0, LedgerEvent(today, "상계 ${won(offset)}원 · ${owesPhrase(name(d), name(p))} 진 빚과 퉁침", d, p))
        }
        // 이자는 이번 영수증에서 받았으니, 남은 빚은 오늘부터 다시 센다
        if (receipt.billed > 0) debts.add(0, Debt(p, d, receipt.billed, since = today, disputed = old?.disputed ?: false))
        return receipt
    }

    private fun addToLifetime(learnerId: String, amount: Int, km: Int) {
        val i = lifetime.indexOfFirst { it.learnerId == learnerId }
        if (i >= 0) {
            val l = lifetime[i]
            lifetime[i] = l.copy(rides = l.rides + 1, km = l.km + km, amount = l.amount + amount)
        } else {
            lifetime += LifetimeLedger(learnerId, today.year, rides = 1, km = km, amount = amount)
        }
    }

    /** 도착 화면 닫기 */
    fun dismissArrival() {
        arrival = null
        changed()
    }

    fun submitReview(receiptId: String, review: Review) {
        reviews[receiptId] = review
        changed()
    }

    fun requestSettlement(debt: Debt, amount: Int) {
        requests += SettlementRequest("s${nextId++}", debt.debtorId, debt.creditorId, amount)
        changed()
    }

    /** 기사의 [받았어요] / [못 받았는데요] */
    fun confirmSettlement(request: SettlementRequest, received: Boolean) {
        requests.remove(request)
        val p = request.payerId
        val q = request.payeeId
        val index = debts.indexOfFirst { it.debtorId == p && it.creditorId == q }
        if (received) {
            var note = ""
            if (index >= 0) {
                val left = debts[index].amount - request.amount
                if (left <= 0) {
                    debts.removeAt(index)
                    note = " · 다 갚음"
                } else {
                    // 일부만 갚아도 연체 일수는 처음 날짜부터 그대로 센다
                    debts[index] = debts[index].copy(amount = left, disputed = false)
                    note = " · ${won(left)}원 남음"
                }
            }
            events.add(0, LedgerEvent(today, "${owesPhrase(name(p), name(q))} ${won(request.amount)}원 정산 확인$note", p, q))
        } else {
            if (index >= 0) debts[index] = debts[index].copy(disputed = true)
            events.add(0, LedgerEvent(today, "${name(q)}: \"${won(request.amount)}원 못 받았는데요\" · 분쟁 중", p, q))
        }
        changed()
    }

    /** 생일·기념일 빚 탕감. 장부에 생색 기록이 남는다. */
    fun forgive(debt: Debt) {
        debts.remove(debt)
        requests.removeAll { it.payerId == debt.debtorId && it.payeeId == debt.creditorId }
        events.add(
            0,
            LedgerEvent(
                today,
                "${name(debt.creditorId).ga()} ${name(debt.debtorId)} 빚 ${won(debt.amount)}원 탕감",
                debt.debtorId, debt.creditorId,
            ),
        )
        changed()
    }

    companion object {
        /**
         * 비밀 열쇠가 없는 가족(열쇠 기능 전에 저장된 장부)에게 열쇠를 채운다.
         * 시연 가족은 폰 앱 "시연 가족으로 등록"과 맞도록 고정 열쇠, 그 밖에는 새로 만든다.
         */
        private fun withSecret(m: Member): Member = when {
            m.secret.isNotEmpty() -> m
            DemoFamily.people.any { it.memberId == m.id } -> m.copy(secret = DemoFamily.secretOf(m.id))
            else -> m.copy(secret = Base32.encode(Totp.newSecret()))
        }

        /**
         * 가짜 승차감 데이터: 잔잔한 흔들림 + 과속방지턱 몇 번 + 급정거 한 번.
         * 차량에서는 운행 중 가속도·브레이크 값을 고르게 모아 만든다.
         */
        fun sampleShake(seed: Int, brakeAt: Int, size: Int = 120): List<Int> {
            val rnd = java.util.Random(seed.toLong())
            val bumps = setOf(24, 25, 57, 58, 59, 101)
            return List(size) { i ->
                val base = 8 + rnd.nextInt(10)
                when {
                    i == brakeAt -> 96
                    i == brakeAt - 1 || i == brakeAt + 1 -> 60
                    i in bumps -> 38 + rnd.nextInt(15)
                    else -> base
                }
            }
        }

        fun sample(clock: () -> LocalDateTime = LocalDateTime::now): FaxiStore = FaxiStore(sampleSnapshot(clock().toLocalDate()), clock)

        /**
         * 처음 설치했을 때(저장 파일이 없을 때) 쓰는 시연 데이터.
         * 엄마는 8,120원 중 5,000원만 보내서 3,120원이 사흘째 밀려 있다.
         * 그래서 데모 운행(아빠가 엄마를 태움)이 기획서 예시 18,379원이 된다.
         */
        fun sampleSnapshot(today: LocalDate): FaxiSnapshot {
            val d3 = today.minusDays(3)
            val dad = Member(
                "dad", "아빠", "PAXI", "급정거 김기사", Grade.Full,
                LocalDate.of(2026, 10, 1), drivenKm = 1_284, avatar = "bear",
            )
            val mom = Member(
                "mom", "엄마", "MAXI", "칼치기 금지 박기사", Grade.Full,
                LocalDate.of(2026, 10, 1), drivenKm = 962, avatar = "fox",
            )
            val kid = Member(
                "kid", "지우", "KIXI", "뒷좌석 과자왕", Grade.Learner,
                LocalDate.of(2026, 10, 1), drivenKm = 0, avatar = "rabbit",
                licenseEligible = LocalDate.of(2029, 3, 14),
            )
            val grandma = Member(
                "grandma", "할머니", "NAXI", "40년 무사고", Grade.Honorary,
                LocalDate.of(2026, 10, 3), drivenKm = 412_000, avatar = "turtle",
            )
            val receipts = listOf(
                Receipt(
                    "r3", driverId = "dad", passengerId = "kid", from = "우리집", to = "수학학원", km = 4,
                    endedAt = d3.atTime(17, 50),
                    lines = listOf(FareLine("기본요금", null, 4_800), FareLine("거리요금", "4.2km", 3_100)),
                    toLifetime = true,
                ),
                Receipt(
                    "r2", driverId = "mom", passengerId = "grandma", from = "우리집", to = "정형외과", km = 5,
                    endedAt = d3.atTime(10, 15),
                    lines = listOf(
                        FareLine("기본요금", null, 4_800), FareLine("거리요금", "5.1km", 3_800),
                        FareLine("대기요금", "신호 4회", 400), FareLine("난방 옵션", "10분", 300),
                    ),
                ),
                Receipt(
                    "r1", driverId = "dad", passengerId = "mom", from = "우리집", to = "성수역", km = 4,
                    endedAt = d3.atTime(8, 10),
                    lines = listOf(
                        FareLine("기본요금", null, 4_800), FareLine("거리요금", "3.9km", 2_900),
                        FareLine("대기요금", "신호 3회", 300), FareLine("엉따 옵션", "6분", 120),
                    ),
                ),
            )
            return FaxiSnapshot(
                members = listOf(dad, mom, kid, grandma),
                driverId = "dad",
                debts = listOf(
                    Debt("mom", "dad", 3_120, since = d3),
                    Debt("grandma", "mom", 9_300, since = d3, disputed = true),
                ),
                events = listOf(
                    LedgerEvent(today.minusDays(2), "엄마: \"9,300원 못 받았는데요\" · 분쟁 중", "grandma", "mom"),
                    LedgerEvent(d3, "엄마가 아빠에게 5,000원 정산 확인 · 3,120원 남음", "mom", "dad"),
                    LedgerEvent(today.minusDays(7), "엄마가 아빠 빚 18,379원 탕감 · 생일 기념", "dad", "mom"),
                ),
                receipts = receipts,
                lifetime = listOf(LifetimeLedger("kid", sinceYear = 2017, rides = 2_140, km = 18_204, amount = 14_502_310)),
                reviews = mapOf(
                    "r1" to Review(3, setOf("선곡 최악")),
                    "r2" to Review(5, setOf("길 잘 앎", "다음에 또 탈게요")),
                    "r3" to Review(2, setOf("말이 너무 많음")),
                ),
            )
        }
    }
}
