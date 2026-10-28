package com.geppetto.faxi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.message.MessageEnvelope
import com.geppetto.faxi.message.MessageReply
import com.geppetto.faxi.message.MessageWire
import com.geppetto.faxi.message.ReviewEnvelope
import com.geppetto.faxi.qr.Balance
import com.geppetto.faxi.qr.ReceiptTicket
import com.geppetto.faxi.auth.Totp
import com.geppetto.faxi.fare.FakeTrip
import com.geppetto.faxi.fare.FareLine
import com.geppetto.faxi.fare.FareMeter
import com.geppetto.faxi.fare.Hvac
import com.geppetto.faxi.fare.VehicleSample
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 화면이 보는 상태 창구이자 차 안 장부의 주인.
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
    val familyId: String = snapshot.familyId
    val members = mutableStateListOf<Member>().apply { addAll(snapshot.members.map(::withSecret)) }

    var driverId by mutableStateOf(snapshot.driverId)
        private set
    var passengerId by mutableStateOf(snapshot.ridePassengerId)
        private set
    /** 미터기를 켠 시각. 운행 중 화면에 "출발 09:12"로 보여 준다(움직이는 경과 시간은 쓰지 않는다). */
    var rideStartedAt by mutableStateOf(snapshot.rideStartedAt)
        private set
    /** 미터기가 켜져 있는지(탑승 인증 뒤 ~ 하차 전) */
    val rideActive: Boolean get() = passengerId != null

    /** 지금 차량 값(VehicleFeed). 차가 아닌 기기·Pleos 서비스가 없는 이미지에서는 비어 있다. */
    var vehicle by mutableStateOf(VehicleReading())
        private set

    /**
     * 기어. 차량 값이 있으면 그대로, 없으면(폰·폴드 시연) 운행 중이면 Driving, 아니면 Parked로 본다.
     * Driving인데 운행 중이 아니면 화면을 잠근다(탑승·장부는 주차 중에만).
     */
    val gear: Gear get() = vehicle.gear?.takeIf { it != Gear.Unknown } ?: if (rideActive) Gear.Driving else Gear.Parked

    /**
     * 탑승·장부·기사 화면을 닫아야 하는지(운행 중이 아닐 때). 기어가 움직이는 상태이거나,
     * 플랫폼 주행 규제(CarUxRestrictions)가 주행 중 최적화를 요구하면 닫는다(Pleos 주행 규제 개발 가이드).
     */
    val lockedForDriving: Boolean get() = !rideActive && (gear == Gear.Driving || vehicle.uxRestricted)

    /** 실제 차량 값으로 도는 요금 엔진. 운행 중이고 거리·속도가 들어올 때만 있다. 저장하지 않는다(앱이 꺼지면 시연 운행으로 대신). */
    private var liveMeter: FareMeter? = null
    /** 이번 운행에서 차가 움직였는지. 움직인 뒤 P로 돌아오면 하차로 본다. */
    private var movedThisRide = false
    /**
     * 이번 운행 거리(m) 두 가지. 요금에는 큰 쪽을 쓴다.
     * - 주행거리계 차이: 정확하지만 단위가 굵다(차량 0.1km, 에뮬레이터 Virtual Console은 1km이고 주행 시뮬레이션에 안 따라 움직인다).
     * - 속도 적분: 속도 값이 올 때마다 (앞 속도 × 지난 시간)을 더한다. 요금 엔진처럼 앞 값의 상태로 센다.
     */
    private var rideStartOdometerKm: Double? = null
    private var integratedM = 0.0
    private var lastSpeedAt: LocalDateTime? = null
    private var lastSpeedKmh = 0.0

    /** 내비 값(NaviFeed). 차가 아니면 비어 있다. */
    var navi by mutableStateOf(NaviReading())
        private set
    /** 이번 운행 출발지(탑승 때 내비 주소) */
    private var rideFrom: String? = snapshot.rideFrom
    /** 이번 운행에 추가된 경유지 수(빼면 줄어든다). 하차 때 요금 엔진에 넣는다. */
    var rideWaypoints by mutableStateOf(snapshot.rideWaypoints)
        private set
    /** 이번 운행 중에 내비가 도착을 알린 장소 */
    private var rideArrivedAt: String? = null

    /** 운행이 시작되거나(손님 이름) 끝날 때(null). FaxiApp이 운행 서비스(RideService)를 켜고 끄는 데 쓴다. */
    var onRideChanged: ((passengerName: String?) -> Unit)? = null

    /** 하차해서 영수증이 나왔을 때(자동 하차든 시연 버튼이든). MainActivity가 음성 안내에 쓴다. */
    var onArrival: ((Receipt) -> Unit)? = null

    val debts = mutableStateListOf<Debt>().apply { addAll(snapshot.debts) }
    val events = mutableStateListOf<LedgerEvent>().apply { addAll(snapshot.events) }
    /** 운행 기록, 최신이 앞 */
    val receipts = mutableStateListOf<Receipt>().apply { addAll(snapshot.receipts) }
    val lifetime = mutableStateListOf<LifetimeLedger>().apply { addAll(snapshot.lifetime) }
    val requests = mutableStateListOf<SettlementRequest>().apply { addAll(snapshot.requests) }
    /** 손님이 폰에서 보낸 기사 후기(영수증 id별) */
    val reviews = mutableStateMapOf<String, Review>().apply { putAll(snapshot.reviews) }
    /** 기사가 하차 뒤 차 화면에서 남긴 손님 평가(영수증 id별) */
    val passengerReviews = mutableStateMapOf<String, Review>().apply { putAll(snapshot.passengerReviews) }
    private val reviewsReadAloud = snapshot.reviewsReadAloud.toMutableSet()

    /** 차량 옵션 상태. 저장하지 않는다(차가 늘 지금 값을 알려 준다). */
    var cabin by mutableStateOf(CabinState.Demo)
        private set

    /** 운행 중 손님이 폰으로 보낸 마지막 한마디. 운행이 바뀌면 지운다. 저장하지 않는다(그 운행 동안만 의미가 있다). */
    var passengerMessage by mutableStateOf<PassengerMessage?>(null)
        private set
    /** 사람별 마지막으로 받은 한마디의 보낸 시각(초). 같은 통을 다시 보내거나 연타하는 것을 막는다. */
    private val lastMessageAt = mutableMapOf<String, Long>()

    /** 방금 끝난 운행. null이 아니면 도착 화면(영수증 공개)을 띄운다. */
    var arrival by mutableStateOf(snapshot.arrivalReceiptId?.let { id -> snapshot.receipts.firstOrNull { it.id == id } })
        private set

    private var nextId = snapshot.nextId

    /** 가족이 고른 설정(음성, 후기 낭독, 연체 이자) */
    var settings by mutableStateOf(snapshot.settings)
        private set

    fun updateSettings(next: FaxiSettings) {
        settings = next
        changed()
    }

    /** 값이 바뀔 때마다 불린다. FaxiApp이 저장에 쓴다. */
    var onChanged: (() -> Unit)? = null

    private fun changed() = onChanged?.invoke()

    val today: LocalDate get() = clock().toLocalDate()

    fun snapshot(): FaxiSnapshot = FaxiSnapshot(
        familyId = familyId,
        members = members.toList(),
        driverId = driverId,
        debts = debts.toList(),
        events = events.toList(),
        receipts = receipts.toList(),
        lifetime = lifetime.toList(),
        requests = requests.toList(),
        reviews = reviews.toMap(),
        passengerReviews = passengerReviews.toMap(),
        reviewsReadAloud = reviewsReadAloud.toSet(),
        ridePassengerId = passengerId,
        rideStartedAt = rideStartedAt,
        rideFrom = rideFrom,
        rideWaypoints = rideWaypoints,
        arrivalReceiptId = arrival?.id,
        nextId = nextId,
        settings = settings,
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

    /** 손님으로 받은 평균 별점. 평가가 없으면 null */
    fun passengerRating(memberId: String): Double? =
        receipts.filter { it.passengerId == memberId }.mapNotNull { passengerReviews[it.id]?.stars }
            .takeIf { it.isNotEmpty() }?.average()

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

    /** 이 사람 폰에 건넬 초대(차 화면 QR). 비밀 열쇠가 들어 있으니 그 사람 앞에서만 띄운다. */
    fun inviteFor(memberId: String): FaxiInvite {
        val m = member(memberId)
        return FaxiInvite(familyId, m.id, m.name, m.callsign, m.secret)
    }

    /** 영수증 QR에 담을 내용: 영수증 + 지금 이 손님의 받을 돈·갚을 돈 */
    fun ticketFor(r: Receipt): ReceiptTicket {
        val p = r.passengerId
        val driver = member(r.driverId)
        val balances = debts.mapNotNull { d ->
            when (p) {
                d.debtorId -> Balance(name(d.creditorId), owedToMe = false, amount = d.amount, disputed = d.disputed)
                d.creditorId -> Balance(name(d.debtorId), owedToMe = true, amount = d.amount, disputed = d.disputed)
                else -> null
            }
        }
        return ReceiptTicket(
            familyId = familyId, id = r.id, passengerId = p, passenger = name(p),
            driver = "${driver.name} · ${driver.callsign}", route = "${r.from} → ${r.to}",
            endedAt = r.endedAt.toString(), lines = r.lines, fareTotal = r.fareTotal, billed = r.billed,
            carriedOver = r.carriedOver, lateInterest = r.lateInterest, offset = r.offset, toLifetime = r.toLifetime,
            balances = balances,
        )
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

    /**
     * 손님 폰에서 온 한마디(같은 Wi-Fi, MessageServer가 넘겨준다). 기사 화면에 띄울지 정한다.
     * - 우리 가족, 그 사람 열쇠로 서명, 1분 안에 보낸 것만
     * - 지금 이 차에 탄 손님만(운행 중이 아니거나 다른 사람이면 NotRiding)
     * - 한 사람이 10초에 한 번까지, 같은 통을 두 번 받지 않는다
     */
    fun receiveMessage(envelope: MessageEnvelope): MessageReply {
        if (envelope.familyId != familyId) return MessageReply.Rejected
        val m = members.firstOrNull { it.id == envelope.memberId } ?: return MessageReply.Rejected
        val secret = Base32.decode(m.secret) ?: return MessageReply.Rejected
        val now = clock().atZone(ZoneId.systemDefault()).toEpochSecond()
        if (!envelope.verify(secret, now)) return MessageReply.Rejected
        if (passengerId != m.id) return MessageReply.NotRiding
        val last = lastMessageAt[m.id]
        if (last != null && envelope.sentAt <= last) return MessageReply.Rejected
        if (last != null && envelope.sentAt - last < MessageWire.MIN_INTERVAL_SECONDS) return MessageReply.TooSoon
        lastMessageAt[m.id] = envelope.sentAt
        passengerMessage = PassengerMessage(envelope.message, clock().withNano(0))
        return MessageReply.Shown
    }

    fun startRide(passengerId: String) {
        require(passengerId != driverId)
        this.passengerId = passengerId
        passengerMessage = null
        rideStartedAt = clock().withSecond(0).withNano(0)
        resetRideDistance()
        liveMeter = sampleNow()?.let { FareMeter(it) }
        rideFrom = navi.currentAddress?.let(::shortPlace)
        rideWaypoints = 0
        rideArrivedAt = null
        changed()
        onRideChanged?.invoke(member(passengerId).name)
    }

    /**
     * 차량 값이 바뀔 때마다(VehicleFeed, 메인 스레드). 운행 중이면 요금 엔진에 한 칸 쌓는다.
     * 엔진은 구간을 앞 값의 상태로 세므로 값이 바뀔 때만 넣어도 정차 시간·거리가 맞다(하차 때 마지막 값을 한 번 더 넣는다).
     * 움직였다가 P에 들어가면 하차.
     */
    fun onVehicle(reading: VehicleReading) {
        vehicle = reading
        // 옵션 상태가 하나라도 들어오면 차량 값으로(안 온 칸은 꺼짐). 요금 엔진은 바뀐 순간의 값을 다음 구간에 쓴다.
        reading.cabin()?.let { cabin = it }
        if (!rideActive) return
        integrate()
        val sample = sampleNow() ?: return
        val meter = liveMeter
        if (meter == null) liveMeter = FareMeter(sample) else meter.update(sample)
        if (reading.gear == Gear.Driving || (reading.speedKmh ?: 0.0) >= 1.0) movedThisRide = true
        if (reading.gear == Gear.Parked && movedThisRide) endRide()
    }

    private fun resetRideDistance() {
        movedThisRide = false
        rideStartOdometerKm = vehicle.odometerKm
        integratedM = 0.0
        lastSpeedAt = vehicle.speedKmh?.let { clock() }
        lastSpeedKmh = vehicle.speedKmh ?: 0.0
    }

    /** 앞 속도로 지난 시간만큼 거리를 더하고, 지금 속도를 다음 구간의 기준으로 둔다 */
    private fun integrate() {
        val now = clock()
        lastSpeedAt?.let { at ->
            val sec = java.time.Duration.between(at, now).toMillis() / 1000.0
            if (sec > 0) integratedM += lastSpeedKmh / 3.6 * sec
        }
        if (vehicle.speedKmh != null) {
            lastSpeedAt = now
            lastSpeedKmh = vehicle.speedKmh ?: 0.0
        }
        if (rideStartOdometerKm == null) rideStartOdometerKm = vehicle.odometerKm
    }

    /** 이번 운행 거리(m): 주행거리계 차이와 속도 적분 중 큰 쪽 */
    private fun rideDistanceM(): Double {
        val odo = vehicle.odometerKm?.let { km -> rideStartOdometerKm?.let { (km - it) * 1000 } } ?: 0.0
        return maxOf(odo, integratedM)
    }

    /** 내비 값이 바뀔 때(NaviFeed, 메인 스레드). 운행 중에 도착을 알리면 그 장소를 도착지로 쓴다. */
    fun onNavi(reading: NaviReading) {
        if (rideActive && reading.arrivedAt != null && reading.arrivedAt != navi.arrivedAt) rideArrivedAt = reading.arrivedAt
        navi = reading
    }

    /** 내비에서 경유지를 넣거나 뺄 때. 운행 중일 때만 센다(경유지 1곳 500원, 빼면 돌려준다). */
    fun onWaypoint(change: WaypointChange) {
        if (!rideActive) return
        rideWaypoints = when (change) {
            WaypointChange.Added -> rideWaypoints + 1
            WaypointChange.Removed -> maxOf(0, rideWaypoints - 1)
            WaypointChange.AllRemoved -> 0
        }
        changed()
    }

    /**
     * 영수증의 출발지·도착지. 내비 값이 하나라도 왔으면 실제 장소(없으면 "출발지"/"도착지"),
     * 내비가 없는 기기(폰·폴드 시연)면 시연 장소 이름.
     */
    private fun rideRoute(): Pair<String, String> {
        if (!navi.connected && rideFrom == null) return "우리집" to "이마트 성수점"
        val to = rideArrivedAt ?: navi.destination ?: navi.currentAddress?.let(::shortPlace)
        return (rideFrom ?: "출발지") to (to ?: "도착지")
    }

    /** 지금 차량 값 + 옵션 상태로 요금 엔진 한 칸. 속도가 아직 안 왔으면 null. 거리는 운행 시작부터 센 값. */
    private fun sampleNow(): VehicleSample? {
        val speed = vehicle.speedKmh ?: return null
        return VehicleSample(
            at = clock(), odometerM = rideDistanceM(), speedKmh = speed,
            hvac = cabin.hvac,
            seatHeat = cabin.seatHeat, wiper = cabin.wiper, windowOpen = cabin.windowOpen,
        )
    }

    /**
     * 하차. 이번 운행의 요금 엔진(FareMeter)으로 영수증을 만들고 장부에 반영한다.
     * - 차량 값이 들어온 운행이면 그 엔진(진짜 주행거리·속도·정차). 마지막 값을 한 번 더 넣어 하차 순간까지 센다.
     * - 차량 값이 없으면(폰·폴드 시연, 운행 중 앱이 다시 켜짐) 기획서 예시 운행(FakeTrip.planExample).
     * 밤 10시~새벽 4시에 타면 심야 할증이 붙는다.
     * - 손님이 기사에게 밀린 돈은 이월하고 연체 이자(하루 0.1%, 1원 미만 버림, 원금 이하)를 붙인다.
     * - 상계: 기사가 이 손님에게 진 빚이 있으면 먼저 퉁친다.
     * - 예비 기사 손님은 청구하지 않고 평생 장부에 쌓는다.
     */
    fun endRide(): Receipt {
        val passenger = checkNotNull(passengerId)
        if (liveMeter != null) integrate()
        val live = liveMeter?.also { m -> sampleNow()?.let(m::update) }
        val meter = live ?: FakeTrip.run(FakeTrip.planExample(rideStartedAt ?: clock()))
        repeat(rideWaypoints) { meter.addWaypoint() }
        val (from, to) = rideRoute()
        val comfort = meter.comfort()
        val base = Receipt(
            id = "r${nextId++}", driverId = driverId, passengerId = passenger,
            from = from, to = to, km = (meter.distanceM / 1000).roundToInt(),
            endedAt = clock().withSecond(0).withNano(0), lines = meter.lines(),
            shake = comfort.shake,
            hardBrakes = comfort.brakes,
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
        passengerMessage = null
        rideStartedAt = null
        liveMeter = null
        resetRideDistance()
        rideFrom = null
        rideWaypoints = 0
        rideArrivedAt = null
        changed()
        onRideChanged?.invoke(null)
        onArrival?.invoke(receipt)
        return receipt
    }

    private fun settleRide(base: Receipt): Receipt {
        val p = base.passengerId
        val d = base.driverId
        val old = debt(p, d)
        val carried = old?.amount ?: 0
        val days = old?.let(::daysOf) ?: 0
        val interest = if (settings.lateInterest) minOf(carried * days / 1000, carried) else 0
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

    /**
     * 후기 낭독: 이 기사가 받은 후기 중 아직 소리 내어 읽지 않은 가장 최근 것을 꺼내고 읽음으로 표시한다.
     * 기사를 고를 때(탑승 화면, 주차 중) 차가 읽어 준다. 운전자는 주행 중에 후기를 보거나 듣지 않는다.
     * 설정이 "별 4개 이상만"이면 낮은 후기는 건너뛰고, "읽지 않기"면 null.
     */
    fun takeUnreadReview(driverId: String): Pair<Receipt, Review>? {
        val minStars = when (settings.reviewsAloud) {
            ReviewsAloud.Off -> return null
            ReviewsAloud.GoodOnly -> 4
            ReviewsAloud.All -> 1
        }
        val unread = reviewsOf(driverId).filter { (r, _) -> r.id !in reviewsReadAloud }
        val next = unread.firstOrNull { (_, review) -> review.stars >= minStars }
        // 건너뛴 낮은 후기는 나중에 설정을 바꿔도 몰아서 읽지 않게 읽은 것으로 둔다(기사 상세에서는 그대로 보인다)
        val skipped = unread.takeWhile { it != next }.map { it.first.id }
        if (next == null && skipped.isEmpty()) return null
        reviewsReadAloud += skipped
        next?.let { reviewsReadAloud += it.first.id }
        changed()
        return next
    }

    /**
     * 손님 폰에서 온 기사 후기(MessageServer가 넘겨준다). 운행 하나에 한 번만 받는다.
     * 우리 가족·그 사람 서명·1분 안, 그리고 그 사람이 손님이었던 운행이어야 한다.
     */
    fun receiveReview(envelope: ReviewEnvelope): MessageReply {
        if (envelope.familyId != familyId) return MessageReply.Rejected
        val m = members.firstOrNull { it.id == envelope.memberId } ?: return MessageReply.Rejected
        val secret = Base32.decode(m.secret) ?: return MessageReply.Rejected
        val now = clock().atZone(ZoneId.systemDefault()).toEpochSecond()
        if (!envelope.verify(secret, now)) return MessageReply.Rejected
        val receipt = receipt(envelope.receiptId)
        if (receipt == null || receipt.passengerId != m.id) return MessageReply.UnknownRide
        if (receipt.id in reviews) return MessageReply.AlreadyReviewed
        reviews[receipt.id] = Review(envelope.stars, envelope.tags.map { it.text }.toSet())
        changed()
        return MessageReply.Shown
    }

    /** 기사가 하차 뒤 손님을 평가한다(도착 화면). 운행 하나에 한 번. */
    fun ratePassenger(receiptId: String, review: Review) {
        if (receiptId in passengerReviews) return
        passengerReviews[receiptId] = review
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
                    "r2" to Review(5, setOf("길 잘 앎", "또 탈게요")),
                    "r3" to Review(2, setOf("말 많음")),
                ),
            )
        }
    }
}
