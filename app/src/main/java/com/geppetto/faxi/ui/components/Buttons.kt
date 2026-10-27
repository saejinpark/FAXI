package com.geppetto.faxi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.geppetto.faxi.ui.theme.Faxi
import com.geppetto.faxi.ui.theme.FaxiDimens

/** 화면의 핵심 동작 하나. 한 화면에 하나만(accent 면은 화면당 하나). */
@Composable
fun FaxiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Faxi.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = FaxiDimens.controlHeight),
        shape = RoundedCornerShape(FaxiDimens.radiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.surfaceRaised,
            disabledContentColor = c.inkDisabled,
        ),
    ) {
        Text(text, style = Faxi.type.label)
    }
}

/** 보조 동작. leading에 아이콘을 넣을 수 있다. */
@Composable
fun FaxiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable RowScope.() -> Unit = {},
) {
    val c = Faxi.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = FaxiDimens.controlHeight),
        shape = RoundedCornerShape(FaxiDimens.radiusMd),
        border = BorderStroke(FaxiDimens.borderWidth, if (enabled) c.lineStrong else c.line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = c.surface,
            contentColor = c.ink,
            disabledContainerColor = c.surfaceRaised,
            disabledContentColor = c.inkDisabled,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Text(text, style = Faxi.type.label)
        }
    }
}

/** 원형 아이콘 버튼 88dp. label은 화면에 안 보이지만 TalkBack이 읽는다. */
@Composable
fun FaxiIconButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = Faxi.colors
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(FaxiDimens.controlHeight)
            .semantics { contentDescription = label },
        shape = CircleShape,
        border = BorderStroke(FaxiDimens.borderWidth, if (enabled) c.lineStrong else c.line),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = c.surface,
            contentColor = c.ink,
            disabledContainerColor = c.surfaceRaised,
            disabledContentColor = c.inkDisabled,
        ),
        content = content,
    )
}

/**
 * 고르는 타일(가족 선택, 후기 태그 등). 고르면 테두리가 accent로 굵어지고 체크가 붙는다.
 * 색만으로 상태를 전하지 않도록 체크 아이콘을 함께 쓴다. 최소 76dp.
 * 체크 자리는 고르기 전에도 비워 둔다. 골랐을 때 타일 폭이 늘면 옆 타일이 손가락 밑에서 밀리기 때문.
 * wide = true(폭을 꽉 채우는 목록)면 체크를 오른쪽 끝에 붙인다. FlowRow 안에서는 false로 둔다.
 */
@Composable
fun SelectTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wide: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Faxi.colors
    val shape = RoundedCornerShape(FaxiDimens.radiusMd)
    Row(
        modifier
            .sizeIn(minWidth = FaxiDimens.targetMin, minHeight = FaxiDimens.targetMin)
            .background(if (selected) c.surfaceRaised else c.surface, shape)
            .border(
                if (selected) FaxiDimens.borderWidth * 2 else FaxiDimens.borderWidth,
                if (selected) c.accent else c.line,
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = FaxiDimens.space4, vertical = FaxiDimens.space3),
        horizontalArrangement = Arrangement.spacedBy(FaxiDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        if (wide) Spacer(Modifier.weight(1f))
        Box(Modifier.size(FaxiDimens.iconMd)) {
            if (selected) CheckIcon(c.accentInk, FaxiDimens.iconMd)
        }
    }
}
