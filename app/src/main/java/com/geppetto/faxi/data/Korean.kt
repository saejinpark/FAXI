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

/** 독촉 한 줄(화면·음성 공용): "손님, 미결제 3,120원이 3일째입니다." */
fun dunningLine(amount: Int, days: Int): String =
    if (days > 0) "손님, 미결제 ${won(amount)}원이 ${days}일째입니다." else "손님, 미결제 ${won(amount)}원이 있습니다."
