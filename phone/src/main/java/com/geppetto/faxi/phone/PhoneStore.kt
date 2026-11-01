package com.geppetto.faxi.phone

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geppetto.faxi.auth.DemoFamily
import com.geppetto.faxi.auth.FaxiInvite
import com.geppetto.faxi.message.SyncPayload
import com.geppetto.faxi.qr.Balance
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

    var me by mutableStateOf(prefs.getString(KEY_INVITE, null)?.let(FaxiInvite::parse)?.let(::withDemoColor))
        private set

    /** 최신이 앞 */
    val receipts = mutableStateListOf<ReceiptTicket>().apply {
        val saved = prefs.getString(KEY_RECEIPTS, null)
        if (saved != null) addAll(Json.decodeFromString(LinkList, saved).mapNotNull(ReceiptTicket::parse))
    }

    /** 기사 후기를 보냈거나 건너뛴 영수증 id. 후기 카드는 이 안에 없는 가장 최근 영수증에만 뜬다. */
    var reviewed by mutableStateOf(prefs.getStringSet(KEY_REVIEWED, emptySet())!!.toSet())
        private set

    /** 후기를 물어볼 영수증: 가장 최근 것이 아직 후기 전이면(건너뛰려면 후기 카드의 "안 남길래요") */
    val pendingReview: ReceiptTicket? get() = receipts.firstOrNull()?.takeIf { it.id !in reviewed }

    // ---- 프로필(자격증 별명, 사진)과 동기화 ----

    /** 자격증 별명. 바꾸면 다음 동기화 때 차로 간다 */
    var nickname by mutableStateOf(prefs.getString(KEY_NICK, "") ?: "")
        private set
    /** 프로필 사진(240×320 JPEG, Base64). 바꾸면 다음 동기화 때 차로 간다 */
    var photo by mutableStateOf(prefs.getString(KEY_PHOTO, null))
        private set
    /** 아직 차에 반영되지 않은 프로필이 있는지 */
    var profileDirty by mutableStateOf(prefs.getBoolean(KEY_DIRTY, false))
        private set
    /** 마지막으로 차와 맞춘 정산 현황(영수증 QR보다 새것). null이면 아직 동기화한 적 없음 */
    var syncedBalances by mutableStateOf<List<Balance>?>(
        prefs.getString(KEY_BALANCES, null)?.let { runCatching { Json.decodeFromString(BalanceList, it) }.getOrNull() },
    )
        private set
    /** 마지막으로 차와 맞춘 시각(epoch 초) */
    var syncedAt by mutableStateOf(prefs.getLong(KEY_SYNCED_AT, 0L).takeIf { it > 0 })
        private set

    /** 동기화 상태 한 줄(저장하지 않는다): 차와 맞추는 중, 못 찾음 등 */
    var syncNote by mutableStateOf<String?>(null)

    fun updateProfile(nickname: String, photo: String?) {
        this.nickname = nickname.trim()
        this.photo = photo
        profileDirty = true
        prefs.edit().putString(KEY_NICK, this.nickname).putString(KEY_PHOTO, photo).putBoolean(KEY_DIRTY, true).apply()
    }

    /** 차의 답을 반영한다: 새 영수증 저장, 정산 현황 갱신, 프로필이 반영됐으면 보낼 것 없음 */
    fun applySync(p: SyncPayload, atEpoch: Long): Int {
        var added = 0
        p.receipts.mapNotNull(ReceiptTicket::parse).reversed().forEach { if (save(it) == SaveResult.Saved) added++ }
        syncedBalances = p.balances
        syncedAt = atEpoch
        if (p.profileApplied) profileDirty = false
        prefs.edit()
            .putString(KEY_BALANCES, Json.encodeToString(BalanceList, p.balances))
            .putLong(KEY_SYNCED_AT, atEpoch)
            .putBoolean(KEY_DIRTY, profileDirty)
            .apply()
        return added
    }

    fun markReviewed(receiptId: String) {
        reviewed = reviewed + receiptId
        prefs.edit().putStringSet(KEY_REVIEWED, reviewed).apply()
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
        prefs.edit().clear().apply()
        nickname = ""; photo = null; profileDirty = false; syncedBalances = null; syncedAt = null
        receipts.clear()
        reviewed = emptySet()
        me = null
    }

    enum class SaveResult { Saved, AlreadySaved, NotRegistered, OtherFamily, NotMine }

    /** 영수증 저장. 내 가족·내 영수증만 받는다 */
    fun save(ticket: ReceiptTicket): SaveResult {
        val me = me ?: return SaveResult.NotRegistered
        if (ticket.familyId != me.familyId) return SaveResult.OtherFamily
        if (ticket.passengerId != me.memberId) return SaveResult.NotMine
        if (receipts.any { it.id == ticket.id }) return SaveResult.AlreadySaved
        // 최신이 앞: 하차 시각 순서로 끼운다(동기화로 예전 영수증이 나중에 들어와도)
        val at = receipts.indexOfFirst { it.endedAt < ticket.endedAt }.let { if (it < 0) receipts.size else it }
        receipts.add(at, ticket)
        saveReceipts()
        return SaveResult.Saved
    }

    private fun saveReceipts() {
        prefs.edit().putString(KEY_RECEIPTS, Json.encodeToString(LinkList, receipts.map { it.toLink() })).apply()
    }

    private companion object {
        const val KEY_INVITE = "invite"
        const val KEY_RECEIPTS = "receipts"
        const val KEY_REVIEWED = "reviewed"
        const val KEY_NICK = "nickname"
        const val KEY_PHOTO = "photo"
        const val KEY_DIRTY = "profile-dirty"
        const val KEY_BALANCES = "synced-balances"
        const val KEY_SYNCED_AT = "synced-at"
        val BalanceList = ListSerializer(Balance.serializer())
        val LinkList = ListSerializer(String.serializer())
    }
}

/** 예전 시연 가족 링크(c=호출명)에는 가족 색이 없다: 시연 가족이면 차와 같은 색을 채운다 */
private fun withDemoColor(i: FaxiInvite): FaxiInvite =
    if (i.familyId != DemoFamily.FAMILY_ID) i
    else DemoFamily.people.firstOrNull { it.memberId == i.memberId }?.let { i.copy(color = it.color) } ?: i
