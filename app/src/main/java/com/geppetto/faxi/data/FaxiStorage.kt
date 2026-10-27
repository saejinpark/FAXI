package com.geppetto.faxi.data

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * 장부를 차 내부 저장소의 JSON 파일 하나에 저장한다(앱 전용 폴더, 다른 앱은 못 읽는다).
 * - 쓰기는 AtomicFile로: 쓰는 도중 시동이 꺼져도 이전 파일이 남는다.
 * - 쓰기는 백그라운드 스레드 하나에서 순서대로 한다. 화면은 기다리지 않는다.
 * - 읽다가 깨진 파일이면 .broken으로 옮겨 두고 null을 돌려준다(덮어써서 잃지 않게).
 */
class FaxiStorage(dir: File) {
    private val file = AtomicFile(File(dir, "faxi-ledger.json"))
    private val writer = Executors.newSingleThreadExecutor()

    fun load(): FaxiSnapshot? {
        if (!file.baseFile.exists()) return null
        return try {
            faxiSnapshotFromJson(file.readFully().decodeToString())
        } catch (e: Exception) {
            Log.e(TAG, "장부 파일을 읽지 못했어요. 따로 보관하고 새로 시작합니다.", e)
            file.baseFile.renameTo(File(file.baseFile.path + ".broken"))
            null
        }
    }

    fun save(snapshot: FaxiSnapshot) {
        val bytes = snapshot.toJson().encodeToByteArray()
        writer.execute {
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                Log.e(TAG, "장부 저장 실패", e)
            }
        }
    }

    private companion object {
        const val TAG = "FaxiStorage"
    }
}
