package com.geppetto.faxi.fare

import org.junit.Assert.assertEquals
import org.junit.Test

/** 가족 할증·할인: 이번 요금 뒤에 정해진 순서로 붙는다 */
class FamilyExtraTest {
    private val base = listOf(FareLine("기본요금", null, 4_800), FareLine("거리요금", "1.3km", 1_000))

    @Test
    fun 없으면_그대로() = assertEquals(base, FamilyExtra.apply(base, emptySet()))

    @Test
    fun 지각은_이번_요금의_20퍼센트_1원_미만_버림() {
        val lines = FamilyExtra.apply(listOf(FareLine("기본요금", null, 4_805)), setOf(FamilyExtra.Late))
        assertEquals(FareLine("지각 할증", "20%", 961), lines.last())
    }

    @Test
    fun 넷_다_켜면_할인은_맨_끝에_여기까지의_절반() {
        val lines = FamilyExtra.apply(base, FamilyExtra.entries.toSet())
        // 5,800 + 지각 1,160 + 짐 500 + 대기 1,000 = 8,460 → 할인 −4,230
        assertEquals(listOf("지각 할증", "짐 할증", "대기 부탁", "생일·심부름 할인"), lines.drop(2).map { it.label })
        assertEquals(-4_230, lines.last().amount)
        assertEquals(4_230, lines.sumOf { it.amount })
    }

    @Test
    fun QR_기호로_줄였다_되살린다() {
        FamilyExtra.apply(base, FamilyExtra.entries.toSet()).forEach { line ->
            assertEquals(line, FareCodes.expand(FareCodes.shorten(line)))
        }
    }
}
