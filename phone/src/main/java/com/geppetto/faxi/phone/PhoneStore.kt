package com.geppetto.faxi.phone

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geppetto.faxi.auth.FaxiInvite

/**
 * 폰에 남기는 것: "나는 이 가족의 이 사람"과 비밀 열쇠(초대 링크 그대로).
 * 앱 전용 저장소(SharedPreferences)라 다른 앱은 못 읽는다. 백업도 끈다(manifest allowBackup=false).
 * 영수증 보관은 다음 단계(차 영수증 QR 읽기)에서 붙인다.
 */
class PhoneStore(context: Context) {
    private val prefs = context.getSharedPreferences("faxi-phone", Context.MODE_PRIVATE)

    var me by mutableStateOf(prefs.getString(KEY_INVITE, null)?.let(FaxiInvite::parse))
        private set

    /** 초대 링크로 등록. FAXI 초대가 아니면 false */
    fun register(link: String): Boolean {
        val invite = FaxiInvite.parse(link.trim()) ?: return false
        prefs.edit().putString(KEY_INVITE, invite.toLink()).apply()
        me = invite
        return true
    }

    fun unregister() {
        prefs.edit().remove(KEY_INVITE).apply()
        me = null
    }

    private companion object {
        const val KEY_INVITE = "invite"
    }
}
