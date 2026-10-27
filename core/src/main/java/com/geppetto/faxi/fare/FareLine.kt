package com.geppetto.faxi.fare

import kotlinx.serialization.Serializable

/** 영수증 한 줄. amount는 1원 단위, 환급은 음수. 차 앱 장부와 폰 앱 영수증이 같이 쓴다. */
@Serializable
data class FareLine(val label: String, val detail: String?, val amount: Int)
