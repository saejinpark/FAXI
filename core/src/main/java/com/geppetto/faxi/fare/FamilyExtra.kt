package com.geppetto.faxi.fare

/**
 * 가족 할증·할인. 기사가 탑승 때(주차 중) 고르고, 손님이 자기 폰 코드를 누르는 것으로 동의한다.
 * 운행 중에는 바꿀 수 없다(운전자는 주행 중 아무것도 만지지 않는다). 하차 때 영수증 줄로 붙는다.
 * code는 차·폰·저장 파일이 같이 쓰는 약속이라 바꾸지 않는다. 새 항목은 끝에.
 */
enum class FamilyExtra(val code: String, val label: String, val tag: String, val hint: String) {
    Late("L", "지각 할증", "+20%", "손님이 늦게 나와서 급하게 가요"),
    Luggage("G", "짐 할증", "+500원", "장 본 짐, 캐리어, 학원 가방"),
    Wait("A", "대기 부탁", "+1,000원", "\"잠깐만 기다려 줘\"가 길어질 때"),
    Treat("T", "생일·심부름 할인", "−50%", "생일이거나 기사 심부름 가는 길"),
    ;

    companion object {
        fun of(code: String): FamilyExtra? = entries.firstOrNull { it.code == code }

        /**
         * 요금 엔진 줄 뒤에 붙인다. 순서: 지각(이번 요금의 20%) → 짐 → 대기 부탁 → 할인(여기까지 합의 절반).
         * 1원 단위, 남는 원은 버린다(할인은 깎아 주는 쪽이 버림이라 손님이 1원 더 낼 수 있다).
         */
        fun apply(lines: List<FareLine>, extras: Set<FamilyExtra>): List<FareLine> {
            if (extras.isEmpty()) return lines
            val base = lines.sumOf { it.amount }
            val out = lines.toMutableList()
            if (Late in extras) out += FareLine(Late.label, "20%", base * 20 / 100)
            if (Luggage in extras) out += FareLine(Luggage.label, null, 500)
            if (Wait in extras) out += FareLine(Wait.label, null, 1_000)
            if (Treat in extras) out += FareLine(Treat.label, "50%", -(out.sumOf { it.amount } / 2))
            return out
        }
    }
}
