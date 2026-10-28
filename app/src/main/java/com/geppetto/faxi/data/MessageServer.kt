package com.geppetto.faxi.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.geppetto.faxi.message.MessageEnvelope
import com.geppetto.faxi.message.MessageReply
import com.geppetto.faxi.message.MessageWire
import com.geppetto.faxi.message.ReviewEnvelope
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 손님 폰의 한마디와 기사 후기를 받는 창구. 같은 Wi-Fi(차 핫스팟 등)에서 폰이 TCP로 한 줄을 보내면 한 줄로 답한다.
 * - 서버·인터넷 없이 차 안에서 끝난다. 받은 줄은 FaxiStore.receiveMessage가 서명·손님·간격을 확인한다.
 * - 폰이 차를 찾도록 NSD(mDNS)에 "_faxi._tcp" 서비스로 알린다.
 * - 장부(FaxiStore)는 화면 상태라 메인 스레드에서만 바꾼다. 소켓 스레드는 줄을 넘기고 답을 기다린다.
 * 에뮬레이터 두 대로 시험할 때: `adb -s <차> forward tcp:47800 tcp:47800` 뒤 폰(디버그)이 10.0.2.2로 보낸다.
 */
class MessageServer(private val context: Context, private val store: FaxiStore) {
    private var server: ServerSocket? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (server != null) return
        val socket = runCatching { ServerSocket(MessageWire.PORT) }.getOrElse {
            Log.w(TAG, "포트 ${MessageWire.PORT}를 열지 못했어요", it)
            return
        }
        server = socket
        thread(name = "faxi-message-server", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(name = "faxi-message-client", isDaemon = true) { handle(client) }
            }
        }
        advertise()
    }

    fun stop() {
        nsdListener?.let { runCatching { nsd().unregisterService(it) } }
        nsdListener = null
        server?.close()
        server = null
    }

    private fun handle(client: Socket) = client.use {
        it.soTimeout = 3_000
        val line = runCatching { it.getInputStream().bufferedReader().readLine() }.getOrNull()
        // 한 줄은 한마디(운행 중) 아니면 기사 후기(하차 뒤). 앞머리로 가른다.
        val message = line?.let(MessageEnvelope::parse)
        val review = line?.let(ReviewEnvelope::parse)
        val reply = when {
            message != null -> onMain { store.receiveMessage(message) }
            review != null -> onMain { store.receiveReview(review) }
            else -> MessageReply.Rejected
        }
        runCatching { it.getOutputStream().write((reply.wire + "\n").toByteArray()) }
    }

    private fun onMain(block: () -> MessageReply): MessageReply {
        val result = CompletableFuture<MessageReply>()
        main.post { result.complete(runCatching(block).getOrDefault(MessageReply.Rejected)) }
        return runCatching { result.get(2, TimeUnit.SECONDS) }.getOrDefault(MessageReply.Rejected)
    }

    private fun nsd() = context.getSystemService(NsdManager::class.java)

    private fun advertise() {
        val info = NsdServiceInfo().apply {
            serviceName = "FAXI-${store.familyId}"
            serviceType = MessageWire.SERVICE_TYPE
            port = MessageWire.PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD 등록: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "NSD 등록 실패 $error")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        runCatching { nsd().registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { nsdListener = listener }
            .onFailure { Log.w(TAG, "NSD를 쓸 수 없어요", it) }
    }

    private companion object {
        const val TAG = "FaxiMessage"
    }
}
