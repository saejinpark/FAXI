package com.geppetto.faxi

import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.message.MessageEnvelope
import com.geppetto.faxi.message.MessageReply
import com.geppetto.faxi.message.RideMessage
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 손님 한마디를 차가 받을지(서명, 지금 손님인지, 간격). 시각은 clock으로 움직인다. */
class RideMessageStoreTest {
    private var now = LocalDateTime.of(2026, 10, 27, 9, 0)
    private val store = FaxiStore.sample { now }
    private fun epoch() = now.atZone(ZoneId.systemDefault()).toEpochSecond()

    private fun from(memberId: String, msg: RideMessage, family: String = store.familyId): MessageEnvelope {
        val secret = Base32.decode(store.member(memberId).secret)!!
        return MessageEnvelope.create(family, memberId, msg, secret, epoch())
    }

    @Test
    fun 지금_손님이_보낸_한마디는_기사_화면에_뜬다() {
        store.selectDriver("dad")
        store.startRide("mom")
        assertEquals(MessageReply.Shown, store.receiveMessage(from("mom", RideMessage.Slow)))
        assertEquals(RideMessage.Slow, store.passengerMessage!!.message)
    }

    @Test
    fun 운행_중이_아니거나_다른_사람이면_받지_않는다() {
        store.selectDriver("dad")
        assertEquals(MessageReply.NotRiding, store.receiveMessage(from("mom", RideMessage.Slow)))
        store.startRide("mom")
        assertEquals(MessageReply.NotRiding, store.receiveMessage(from("kid", RideMessage.Hot)))
        assertNull(store.passengerMessage)
    }

    @Test
    fun 다른_사람_열쇠나_다른_가족이면_거절한다() {
        store.selectDriver("dad")
        store.startRide("mom")
        val kidSigned = from("kid", RideMessage.Slow)
        assertEquals(MessageReply.Rejected, store.receiveMessage(kidSigned.copy(memberId = "mom")))
        assertEquals(MessageReply.Rejected, store.receiveMessage(from("mom", RideMessage.Slow, family = "other")))
    }

    @Test
    fun 같은_통은_한_번만_연타는_10초에_한_번() {
        store.selectDriver("dad")
        store.startRide("mom")
        val first = from("mom", RideMessage.Cold)
        assertEquals(MessageReply.Shown, store.receiveMessage(first))
        assertEquals(MessageReply.Rejected, store.receiveMessage(first))
        now = now.plusSeconds(5)
        assertEquals(MessageReply.TooSoon, store.receiveMessage(from("mom", RideMessage.Hot)))
        now = now.plusSeconds(5)
        assertEquals(MessageReply.Shown, store.receiveMessage(from("mom", RideMessage.Hot)))
        assertEquals(RideMessage.Hot, store.passengerMessage!!.message)
    }

    @Test
    fun 하차하면_한마디가_지워진다() {
        store.selectDriver("dad")
        store.startRide("mom")
        store.receiveMessage(from("mom", RideMessage.Thanks))
        store.endRide()
        assertNull(store.passengerMessage)
    }
}
