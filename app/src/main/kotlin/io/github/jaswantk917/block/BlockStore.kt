package io.github.jaswantk917.block

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** An SMS a rule stopped: kept out of the inbox and its notifications, but never thrown away unasked. */
data class BlockedSms(val address: String, val body: String, val at: Long, val subId: Int, val ruleId: Long)

class BlockStore(context: Context) {
    private val prefs = context.getSharedPreferences("block", Context.MODE_PRIVATE)
    private val rulesState = MutableStateFlow(loadRules())
    val rules: StateFlow<List<BlockRule>> = rulesState
    private val smsState = MutableStateFlow(loadSms())
    val blockedSms: StateFlow<List<BlockedSms>> = smsState

    fun add(kind: RuleKind, pattern: String, applies: Applies) {
        val fixed = if (kind.smsOnly) Applies.Sms else applies
        rulesState.update { it + BlockRule(System.currentTimeMillis(), kind, pattern.trim(), fixed) }
        saveRules()
    }

    fun remove(id: Long) {
        rulesState.update { list -> list.filterNot { it.id == id } }
        saveRules()
    }

    fun keep(sms: BlockedSms) {
        smsState.update { (it + sms).sortedBy { s -> s.at }.takeLast(MAX_SMS) }
        saveSms()
    }

    fun forget(sms: BlockedSms) {
        smsState.update { it - sms }
        saveSms()
    }

    fun forgetAllSms() {
        smsState.value = emptyList()
        saveSms()
    }

    private fun saveRules() = prefs.edit().putStringSet(RULES, rulesState.value.map { listOf(it.id, it.kind.name, it.applies.name, it.pattern).joinToString(SEP) }.toSet()).apply()

    private fun saveSms() = prefs.edit().putStringSet(SMS, smsState.value.map { listOf(it.at, it.subId, it.ruleId, it.address, it.body).joinToString(SEP) }.toSet()).apply()

    private fun loadRules() = prefs.getStringSet(RULES, emptySet()).orEmpty().mapNotNull { line ->
        val p = line.split(SEP, limit = 4)
        if (p.size != 4) return@mapNotNull null
        BlockRule(
            p[0].toLongOrNull() ?: return@mapNotNull null,
            RuleKind.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null,
            p[3],
            Applies.entries.firstOrNull { it.name == p[2] } ?: return@mapNotNull null,
        )
    }.sortedBy { it.id }

    private fun loadSms() = prefs.getStringSet(SMS, emptySet()).orEmpty().mapNotNull { line ->
        val p = line.split(SEP, limit = 5)
        if (p.size != 5) return@mapNotNull null
        BlockedSms(p[3], p[4], p[0].toLongOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: -1, p[2].toLongOrNull() ?: 0)
    }.sortedBy { it.at }

    private companion object {
        const val RULES = "rules"
        const val SMS = "sms"
        const val SEP = "\u0001"
        const val MAX_SMS = 300
    }
}
