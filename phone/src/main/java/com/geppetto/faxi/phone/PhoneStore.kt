package com.geppetto.faxi.phone

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.qr.ReceiptTicket
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 폰에 남기는 것: "나는 이 가족의 이 사람"(초대 링크 그대로)과 내 영수증들(영수증 링크 그대로).
 * 앱 전용 저장소(SharedPreferences)라 다른 앱은 못 읽는다. 백업도 끈다(manifest allowBackup=false).
 * 폰은 차 장부를 바꾸지 못한다. 영수증 속 정산 현황은 찍은 시각 기준 사본이다.
 */
class PhoneStore(context: Context) {
    private val prefs = context.getSharedPreferences("faxi-phone", Context.MODE_PRIVATE)

    var me by mutableStateOf(prefs.getString(KEY_INVITE, null)?.let(FaxiInvite::parse))
        private set

    /** 최신이 앞 */
    val receipts = mutableStateListOf<ReceiptTicket>().apply {
        val saved = prefs.getString(KEY_RECEIPTS, null)
        if (saved != null) addAll(Json.decodeFromString(LinkList, saved).mapNotNull(ReceiptTicket::parse))
    }

    /** 초대 링크로 등록. FAXI 초대가 아니면 false */
    fun register(link: String): Boolean {
        val invite = FaxiInvite.parse(link.trim()) ?: return false
        register(invite)
        return true
    }

    fun register(invite: FaxiInvite) {
        // 다른 가족으로 옮기면 예전 가족 영수증은 지운다
        if (me?.familyId != invite.familyId) {
            receipts.clear()
            saveReceipts()
        }
        prefs.edit().putString(KEY_INVITE, invite.toLink()).apply()
        me = invite
    }

    fun unregister() {
        prefs.edit().remove(KEY_INVITE).remove(KEY_RECEIPTS).apply()
        receipts.clear()
        me = null
    }

    enum class SaveResult { Saved, AlreadySaved, NotRegistered, OtherFamily, NotMine }

    /** 영수증 저장. 내 가족·내 영수증만 받는다 */
    fun save(ticket: ReceiptTicket): SaveResult {
        val me = me ?: return SaveResult.NotRegistered
        if (ticket.familyId != me.familyId) return SaveResult.OtherFamily
        if (ticket.passengerId != me.memberId) return SaveResult.NotMine
        if (receipts.any { it.id == ticket.id }) return SaveResult.AlreadySaved
        receipts.add(0, ticket)
        saveReceipts()
        return SaveResult.Saved
    }

    private fun saveReceipts() {
        prefs.edit().putString(KEY_RECEIPTS, Json.encodeToString(LinkList, receipts.map { it.toLink() })).apply()
    }

    private companion object {
        const val KEY_INVITE = "invite"
        const val KEY_RECEIPTS = "receipts"
        val LinkList = ListSerializer(String.serializer())
    }
}
