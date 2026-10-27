package com.geppetto.faxi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiTheme

/** 화면 미리보기 두 벌: 차량 넓은 화면(1920dp)과 갤럭시 폴드 펼침(900dp) */
@Preview(name = "차량 1920dp", widthDp = 1920, heightDp = 720)
@Preview(name = "폴드 900dp", widthDp = 900, heightDp = 1100)
annotation class FaxiPreviews

/** 미리보기용 바탕: 야간 테마 + bg 색 */
@Composable
fun PreviewFrame(night: Boolean = true, content: @Composable () -> Unit) {
    FaxiTheme(night = night) {
        Box(Modifier.fillMaxSize().background(Faxi.colors.bg)) { content() }
    }
}
