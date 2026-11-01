package com.geppetto.faxi

import com.geppetto.faxi.auth.Base32
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.toJson
import com.geppetto.faxi.message.SyncEnvelope
import com.geppetto.faxi.qr.ReceiptTicket
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 동기화: 폰 프로필(별명·사진)을 받고, 그 사람의 정산·영수증을 돌려준다 */
class ProfileSyncStoreTest {
    private val now = LocalDateTime.of(2026, 10, 27, 9, 0)
    private val store = FaxiStore.sample { now }
    private val epoch = now.atZone(ZoneId.systemDefault()).toEpochSecond()
    private fun env(id: String, nick: String? = null, photo: ByteArray? = null) =
        SyncEnvelope.create(store.familyId, id, nick, photo, Base32.decode(store.member(id).secret)!!, epoch)

    @Test
    fun 프로필을_반영하고_내_정산과_영수증을_돌려준다() {
        val photo = ByteArray(1_000) { 7 }
        val p = store.receiveSync(env("mom", "주차 여왕", photo))!!
        assertTrue(p.profileApplied)
        assertFalse(p.riding)
        assertEquals("주차 여왕", store.member("mom").nickname)
        assertNotNull(store.member("mom").photo)
        // 엄마는 아빠에게 3,120원, 할머니에게서 9,300원
        assertEquals(setOf("아빠" to false, "할머니" to true), p.balances.map { it.other to it.owedToMe }.toSet())
        // 엄마가 손님이었던 영수증만, 폰이 읽을 수 있는 링크로
        assertTrue(p.receipts.isNotEmpty())
        assertTrue(p.receipts.all { ReceiptTicket.parse(it)?.passengerId == "mom" })
        // 사진은 장부와 같이 저장된다
        assertNotNull(faxiSnapshotFromJson(store.snapshot().toJson()).members.first { it.id == "mom" }.photo)
    }

    @Test
    fun 운행_중인_사람의_프로필은_나중에() {
        store.startRide("mom")
        val p = store.receiveSync(env("mom", "바뀐 별명"))!!
        assertTrue(p.riding)
        assertFalse(p.profileApplied)
        assertEquals("주차 장인 박기사", store.member("mom").nickname)
    }

    @Test
    fun 남의_열쇠로_보낸_통은_거절한다() {
        val forged = SyncEnvelope.create(store.familyId, "mom", "가짜", null, Base32.decode(store.member("dad").secret)!!, epoch)
        assertNull(store.receiveSync(forged))
    }
}
