package com.geppetto.faxi.data

import kotlinx.serialization.Serializable

/**
 * 가족이 고르는 차 앱 설정. 장부와 같이 저장한다(차가 주인). 기본값은 처음 설치했을 때의 동작.
 * 차가 말하는 것은 가족에 따라 공격적으로 들릴 수 있어서 끄거나 부드럽게 바꿀 수 있게 한다.
 */
@Serializable
data class FaxiSettings(
    /** 미터기를 켤 때 "손님, 어디로 모실까요?" */
    val greet: Boolean = true,
    /** 하차 때 요금을 소리 내어 읽기 */
    val fareAloud: Boolean = true,
    /** 손님을 고를 때 밀린 돈 알리기 */
    val dunning: Dunning = Dunning.Firm,
    /** 기사를 고를 때 지난 후기 읽기 */
    val reviewsAloud: ReviewsAloud = ReviewsAloud.All,
)

enum class Dunning(val label: String) {
    /** 택시 말투 그대로: "손님, 미결제 3,120원이 3일째입니다." */
    Firm("단호하게"),
    /** "엄마, 지난번 요금 3,120원이 남아 있어요." */
    Gentle("부드럽게"),
    /** 소리 없이 화면에만 부드러운 말로 */
    ScreenOnly("화면에만"),
}

enum class ReviewsAloud(val label: String) {
    All("모두"),
    /** 별 4개 이상만. 낮은 후기는 기사 상세에서만 본다 */
    GoodOnly("별 4개 이상만"),
    Off("읽지 않기"),
}
