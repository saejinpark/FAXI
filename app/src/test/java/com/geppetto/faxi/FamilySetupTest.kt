package com.geppetto.faxi

import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.data.FaxiStore
import com.geppetto.faxi.data.Grade
import com.geppetto.faxi.data.faxiSnapshotFromJson
import com.geppetto.faxi.data.toJson
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 첫 실행 가장 등록, 가족 추가, 시연 가족 불러오기 */
class FamilySetupTest {
    private val clock = { LocalDateTime.of(2026, 10, 27, 9, 0) }
    private fun empty() = FaxiStore(FaxiStore.emptySnapshot(), clock)

    @Test
    fun 처음에는_가장_등록부터() {
        val s = empty()
        assertTrue(s.needsSetup)
        assertNotEquals(DemoFamily.FAMILY_ID, s.familyId)
    }

    @Test
    fun 가장을_등록하면_택시가_열린다() {
        val s = empty()
        var reopened = 0
        s.onFamilyChanged = { reopened++ }
        val head = s.startFamily(" 아빠 ")
        assertFalse(s.needsSetup)
        assertEquals(listOf(head), s.members.toList())
        assertEquals("아빠", head.name)
        assertEquals("아빠 기사", head.cardTitle)
        assertEquals(head.id, s.driverId)
        assertTrue(head.canDrive) // 가장은 운전하는 사람
        assertTrue(s.driverSignedIn) // 가장은 바로 오늘의 기사
        assertTrue(head.secret.isNotEmpty())
        assertEquals(1, reopened)
    }

    @Test
    fun 가족은_한_사람씩_몇_명이든_더한다() {
        val s = empty()
        s.startFamily("엄마")
        val kids = (1..12).map { s.addMember("$it 째", Grade.Learner) }
        assertEquals(13, s.members.size)
        assertEquals(13, s.members.map { it.id }.toSet().size)
        assertEquals(13, s.members.map { it.secret }.toSet().size)
        // 색 네 칸을 등록 순서대로 돌려 쓴다
        assertEquals(listOf(1, 2, 3, 4), kids.take(4).map { it.color })
        assertFalse(kids.first().canDrive)
        // 저장했다 읽어도 같다
        assertEquals(s.members.toList(), faxiSnapshotFromJson(s.snapshot().toJson()).members)
    }

    @Test
    fun 시연_가족으로_둘러보기() {
        val s = empty()
        s.loadDemo()
        assertEquals(DemoFamily.FAMILY_ID, s.familyId)
        assertEquals(listOf("아빠", "엄마", "지우", "할머니"), s.members.map { it.name })
        assertTrue(s.driverSignedIn)
        assertTrue(s.receipts.isNotEmpty())
    }

    @Test
    fun 초대_링크는_색을_싣고_예전_호출명은_버린다() {
        val inv = FaxiInvite("f1", "m1", "둘째", 2, DemoFamily.secretOf("kid"))
        assertEquals(inv, FaxiInvite.parse(inv.toLink()))
        val old = "faxi://join?f=demo-family&m=mom&n=%EC%97%84%EB%A7%88&c=MAXI&s=${DemoFamily.secretOf("mom")}"
        val parsed = FaxiInvite.parse(old)!!
        assertEquals("엄마", parsed.name)
        assertEquals(0, parsed.color)
    }

    @Test
    fun 예전_장부의_명예_기사는_정식_기사로_읽는다() {
        val json = FaxiStore.sample(clock).snapshot().toJson()
        val old = json.replace(Regex(""""grade":"Full""""), "\"grade\":\"Honorary\"")
        assertTrue(old.contains("Honorary")) // 정말 예전 값으로 바꿔 넣었는지
        assertTrue(faxiSnapshotFromJson(old).members.all { it.grade == Grade.Full || it.grade == Grade.Learner })
        assertEquals(Grade.Full, faxiSnapshotFromJson(old).members.first { it.id == "grandma" }.grade)
    }

    @Test
    fun 면허를_따면_정식_기사가_되고_평생_장부가_공개된다() {
        val s = FaxiStore.sample(clock)
        val before = s.lifetime.first { it.learnerId == "kid" }
        assertFalse(before.revealed)
        val ledger = s.graduate("kid")!!
        assertTrue(ledger.revealed)
        assertEquals(before.amount, ledger.amount) // 장부는 그대로 남는다
        assertEquals(Grade.Full, s.member("kid").grade)
        assertEquals("지우 면허 땀 · 평생 장부 공개", s.events.first().text)
        assertEquals(
            "지우, 면허 축하해요. 지금까지 2,140번, 18,204킬로미터를 태워 줬어요. 평생 장부는 14,502,310원이에요. 이제 지우가 기사예요.",
            com.geppetto.faxi.data.licenseLine("지우", ledger),
        )
        assertEquals(null, s.graduate("kid")) // 두 번은 없다
    }

    @Test
    fun 이름과_별명을_고친다() {
        val s = FaxiStore.sample(clock)
        s.updateMember("kid", " 막내 ", "")
        assertEquals("막내", s.member("kid").name)
        assertEquals("막내 기사", s.member("kid").cardTitle)
    }

    @Test
    fun 주고받을_돈이_남은_사람은_뺄_수_없고_정리하면_뺀다() {
        val s = FaxiStore.sample(clock)
        assertEquals("할머니와 주고받을 돈이 남아 있어요. 장부에서 먼저 정리해 주세요.", s.whyCannotRemove("grandma"))
        assertFalse(s.removeMember("grandma"))
        s.forgive(s.debt("grandma", "mom")!!)
        assertTrue(s.removeMember("grandma"))
        assertTrue(s.family.none { it.id == "grandma" })
        assertEquals("할머니", s.name("grandma")) // 지난 기록의 이름은 남는다
        assertFalse(s.verifyCode("grandma", "0000"))
    }

    @Test
    fun 오늘의_기사를_빼면_다시_고른다() {
        val s = FaxiStore.sample(clock)
        s.addMember("삼촌", Grade.Full).also { u -> s.selectDriver(u.id) }
        val uncle = s.driverId
        assertTrue(s.removeMember(uncle))
        assertFalse(s.driverSignedIn)
    }
}
