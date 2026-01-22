package com.slimepop.asmr

import android.content.Context
import java.time.LocalDate

enum class QuestType { POPS_25, POPS_60, HOLD_MS_10000 }

data class Quest(
    val idx: Int,
    val type: QuestType,
    val title: String,
    val target: Long,
    val rewardCoins: Int
)

data class QuestState(
    val dateIso: String,
    val progress: LongArray, 
    val claimed: BooleanArray 
)

object QuestManager {

    fun todaysQuests(): List<Quest> {
        return listOf(
            Quest(0, QuestType.POPS_25, "Pop 25 times", 25, 80),
            Quest(1, QuestType.POPS_60, "Pop 60 times", 60, 160),
            Quest(2, QuestType.HOLD_MS_10000, "Hold total 10 seconds", 10_000, 140)
        )
    }

    fun loadOrInit(ctx: Context): QuestState {
        val today = LocalDate.now().toString()
        val savedDate = Prefs.getQuestDate(ctx)
        if (savedDate != today) {
            val st = QuestState(today, longArrayOf(0,0,0), booleanArrayOf(false,false,false))
            save(ctx, st)
            return st
        }
        val progress = parseCsvLongs(Prefs.getQuestProgressCsv(ctx), defaultSize = 3)
        val claimed = parseCsvBools(Prefs.getQuestClaimedCsv(ctx), defaultSize = 3)
        return QuestState(today, progress, claimed)
    }

    fun save(ctx: Context, st: QuestState) {
        Prefs.setQuestDate(ctx, st.dateIso)
        Prefs.setQuestProgressCsv(ctx, "q0=${st.progress[0]},q1=${st.progress[1]},q2=${st.progress[2]}")
        Prefs.setQuestClaimedCsv(ctx, "q0=${if (st.claimed[0]) 1 else 0},q1=${if (st.claimed[1]) 1 else 0},q2=${if (st.claimed[2]) 1 else 0}")
    }

    fun applyPop(ctx: Context, st: QuestState, count: Int = 1): QuestState {
        val out = st.copy(progress = st.progress.clone(), claimed = st.claimed.clone())
        out.progress[0] += count.toLong()
        out.progress[1] += count.toLong()
        save(ctx, out)
        return out
    }

    fun applyHoldMs(ctx: Context, st: QuestState, holdMs: Long): QuestState {
        val out = st.copy(progress = st.progress.clone(), claimed = st.claimed.clone())
        out.progress[2] += holdMs
        save(ctx, out)
        return out
    }

    fun canClaim(q: Quest, st: QuestState): Boolean {
        val idx = q.idx
        if (st.claimed[idx]) return false
        return st.progress[idx] >= q.target
    }

    fun claim(ctx: Context, q: Quest, st: QuestState): QuestState {
        val out = st.copy(progress = st.progress.clone(), claimed = st.claimed.clone())
        out.claimed[q.idx] = true
        save(ctx, out)
        return out
    }

    private fun parseCsvLongs(csv: String, defaultSize: Int): LongArray {
        val arr = LongArray(defaultSize) { 0L }
        csv.split(",").forEach { token ->
            val parts = token.split("=")
            if (parts.size == 2) {
                val k = parts[0].trim()
                val v = parts[1].trim().toLongOrNull() ?: return@forEach
                val idx = k.removePrefix("q").toIntOrNull()
                if (idx != null && idx in 0 until defaultSize) arr[idx] = v
            }
        }
        return arr
    }

    private fun parseCsvBools(csv: String, defaultSize: Int): BooleanArray {
        val arr = BooleanArray(defaultSize) { false }
        csv.split(",").forEach { token ->
            val parts = token.split("=")
            if (parts.size == 2) {
                val k = parts[0].trim()
                val v = parts[1].trim().toIntOrNull() ?: return@forEach
                val idx = k.removePrefix("q").toIntOrNull()
                if (idx != null && idx in 0 until defaultSize) arr[idx] = (v == 1)
            }
        }
        return arr
    }
}