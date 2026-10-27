package com.geppetto.faxi.fare

/**
 * 영수증 줄을 QR에 실을 때 쓰는 짧은 기호. "대기요금 / 신호 7회" → "W" / "7".
 * 한글 항목 이름이 영수증 QR의 대부분을 차지해서, 기호로 바꾸면 QR이 훨씬 성기게(찍기 쉽게) 된다.
 * 차·폰이 같은 표를 쓰므로 받는 쪽에서 원래 글자로 되돌린다. 표에 없는 줄은 그대로 실린다.
 */
object FareCodes {
    /** 기호 → (항목 이름, 설명 틀. %s 자리에 숫자 같은 값) */
    private val table = linkedMapOf(
        "B" to ("기본요금" to null),
        "D" to ("거리요금" to "%s"),
        "W" to ("대기요금" to "신호 %s회"),
        "N" to ("심야 할증" to "%s%%"),
        "R" to ("우천 할증" to "와이퍼 %s%%"),
        "C" to ("냉방 옵션" to "%s분"),
        "H" to ("난방 옵션" to "%s분"),
        "S" to ("엉따 옵션" to "%s분"),
        "V" to ("환기 서비스" to "%s분"),
        "P" to ("경유지 추가" to "%s곳"),
        "K" to ("기사 귀책 환급" to "급정거 %s회"),
    )

    fun shorten(line: FareLine): FareLine {
        for ((code, entry) in table) {
            val (label, template) = entry
            if (line.label != label) continue
            if (template == null) return if (line.detail == null) FareLine(code, null, line.amount) else line
            val value = line.detail?.let { extract(template, it) } ?: return line
            return FareLine(code, value, line.amount)
        }
        return line
    }

    fun expand(line: FareLine): FareLine {
        val (label, template) = table[line.label] ?: return line
        return FareLine(label, template?.let { t -> line.detail?.let { t.replace("%%", "%").replace("%s", it) } }, line.amount)
    }

    /** "신호 %s회"에서 "신호 7회"의 7을 꺼낸다. 틀과 안 맞으면 null */
    private fun extract(template: String, detail: String): String? {
        val t = template.replace("%%", "%")
        val i = t.indexOf("%s")
        val prefix = t.substring(0, i)
        val suffix = t.substring(i + 2)
        if (!detail.startsWith(prefix) || !detail.endsWith(suffix) || detail.length < prefix.length + suffix.length) return null
        return detail.substring(prefix.length, detail.length - suffix.length)
    }
}
