package com.geppetto.faxi.phone

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.message.DriverTag
import com.geppetto.faxi.message.MessageEnvelope
import com.geppetto.faxi.message.MessageReply
import com.geppetto.faxi.message.MessageWire
import com.geppetto.faxi.message.ReviewEnvelope
import com.geppetto.faxi.message.RideMessage
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 손님 한마디와 기사 후기를 차로 보낸다. 같은 Wi-Fi(차 핫스팟 등)에서 NSD로 "FAXI-우리 가족" 차를 찾아 TCP 한 줄을 보내고 답 한 줄을 받는다.
 * 서명은 탑승 코드와 같은 내 비밀 열쇠로 한다(core MessageEnvelope).
 * 디버그 빌드는 차를 못 찾으면 10.0.2.2(에뮬레이터에서 본 내 컴퓨터)로 보낸다: 차 에뮬레이터에 `adb forward tcp:47800 tcp:47800`.
 */
class MessageSender(private val context: Context) {
    /** 한 번 찾은 차 주소는 앱이 사는 동안 기억한다 */
    private var car: InetSocketAddress? = null

    /** 한마디를 보내고 차의 답을 받는다. 차를 못 찾거나 연결이 끊기면 null */
    suspend fun send(me: FaxiInvite, message: RideMessage): MessageReply? {
        val secret = Base32.decode(me.secret) ?: return MessageReply.Rejected
        return deliver(me.familyId, MessageEnvelope.create(me.familyId, me.memberId, message, secret, now()).toLine())
    }

    /** 운행이 끝난 뒤 기사 후기를 보낸다. 같은 통로, 같은 열쇠 */
    suspend fun sendReview(me: FaxiInvite, receiptId: String, stars: Int, tags: List<DriverTag>): MessageReply? {
        val secret = Base32.decode(me.secret) ?: return MessageReply.Rejected
        return deliver(me.familyId, ReviewEnvelope.create(me.familyId, me.memberId, receiptId, stars, tags, secret, now()).toLine())
    }

    private fun now() = System.currentTimeMillis() / 1000

    private suspend fun deliver(familyId: String, line: String): MessageReply? = withContext(Dispatchers.IO) {
        val targets = listOfNotNull(car) + listOfNotNull(discover(familyId)) + listOfNotNull(emulatorHost())
        for (target in targets.distinct()) {
            val reply = runCatching { exchange(target, line) }.getOrNull() ?: continue
            car = target
            return@withContext reply
        }
        car = null
        null
    }

    private fun exchange(target: InetSocketAddress, line: String): MessageReply? = Socket().use { s ->
        s.connect(target, 2_000)
        s.soTimeout = 3_000
        s.getOutputStream().write((line + "\n").toByteArray())
        s.getOutputStream().flush()
        s.getInputStream().bufferedReader().readLine()?.let(MessageReply::of)
    }

    private fun emulatorHost(): InetSocketAddress? {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) InetSocketAddress(InetAddress.getByName("10.0.2.2"), MessageWire.PORT) else null
    }

    /** 같은 Wi-Fi에서 "FAXI-가족" 서비스를 3초 동안 찾는다 */
    @Suppress("DEPRECATION") // resolveService는 API 34부터 새 콜백이 있지만 minSdk 28이라 옛 방식으로 통일
    private suspend fun discover(familyId: String): InetSocketAddress? {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return null
        return withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (info.serviceName != "FAXI-$familyId") return
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                if (cont.isActive) cont.resume(InetSocketAddress(resolved.host, resolved.port))
                            }
                            override fun onResolveFailed(info: NsdServiceInfo, error: Int) = Unit
                        })
                    }
                    override fun onDiscoveryStarted(type: String) = Unit
                    override fun onDiscoveryStopped(type: String) = Unit
                    override fun onServiceLost(info: NsdServiceInfo) = Unit
                    override fun onStartDiscoveryFailed(type: String, error: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
                }
                runCatching { nsd.discoverServices(MessageWire.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
            }
        }
    }
}
