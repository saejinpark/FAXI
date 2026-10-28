package com.geppetto.faxi.data

/** 18379 -> "18,379". 1원 단위 그대로, 반올림하지 않는다. */
fun won(value: Int): String = "%,d".format(value)

private fun String.hasBatchim(): Boolean {
    val ch = lastOrNull() ?: return false
    if (ch !in '가'..'힣') return false
    return (ch - '가') % 28 != 0
}

/** 이름 + 주격 조사: "엄마가", "지민이" */
fun String.ga(): String = this + if (hasBatchim()) "이" else "가"

/** "엄마가 아빠에게" — 빚의 방향을 화살표 대신 말로 */
fun owesPhrase(debtor: String, creditor: String): String = "${debtor.ga()} ${creditor}에게"

/**
 * 밀린 돈 알림 한 줄(화면·음성 공용). 말투는 설정에서 고른다.
 * 단호하게: "손님, 미결제 3,120원이 3일째입니다." / 부드럽게·화면에만: "엄마, 지난번 요금 3,120원이 남아 있어요."
 */
fun dunningLine(amount: Int, days: Int, passenger: String = "손님", tone: Dunning = Dunning.Firm): String = when (tone) {
    Dunning.Firm -> if (days > 0) "손님, 미결제 ${won(amount)}원이 ${days}일째입니다." else "손님, 미결제 ${won(amount)}원이 있습니다."
    Dunning.Gentle, Dunning.ScreenOnly -> "$passenger, 지난번 요금 ${won(amount)}원이 남아 있어요."
}

private val StarWords = listOf("한", "두", "세", "네", "다섯")

/** 후기 낭독 한 줄: "지난 손님 후기입니다. 엄마 손님, 별 세 개. 선곡 최악." */
fun reviewLine(passenger: String, stars: Int, tags: Collection<String>): String {
    val count = StarWords.getOrElse(stars.coerceIn(1, 5) - 1) { "$stars" }
    val tagText = if (tags.isEmpty()) "" else " ${tags.joinToString(", ")}."
    return "지난 손님 후기입니다. $passenger 손님, 별 $count 개.$tagText"
}
