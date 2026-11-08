package io.github.jaswantk917.block

/** What a rule looks at. Words and sender IDs only make sense for SMS. */
enum class RuleKind(val label: String, val smsOnly: Boolean) {
    StartsWith("Number starts with", false),
    SenderId("Sender ID", true),
    Words("Message contains", true),
    Regex("Regex", false),
}

enum class Applies(val label: String) { Sms("SMS"), Calls("Calls"), Both("SMS and calls") }

data class BlockRule(val id: Long, val kind: RuleKind, val pattern: String, val applies: Applies) {
    val forSms get() = applies != Applies.Calls
    val forCalls get() = applies != Applies.Sms && !kind.smsOnly
}

/** Pure matching, shared by the SMS receiver and the call screener. People in your contacts are never caught by a rule. */
object BlockRules {
    fun forSms(rules: List<BlockRule>, address: String, body: String, isContact: Boolean): BlockRule? =
        if (isContact) null else rules.firstOrNull { it.forSms && matches(it, address, body) }

    fun forCall(rules: List<BlockRule>, number: String, isContact: Boolean): BlockRule? =
        if (isContact) null else rules.firstOrNull { it.forCalls && matches(it, number, null) }

    /** null when the pattern can be used, else why not */
    fun problem(kind: RuleKind, pattern: String): String? {
        val p = pattern.trim()
        if (p.isEmpty()) return "Type something to match"
        return when (kind) {
            RuleKind.StartsWith -> if (p.any { !it.isDigit() && it != '+' && it != ' ' && it != '-' }) "Only digits and +" else null
            RuleKind.Regex -> runCatching { Regex(p) }.exceptionOrNull()?.let { "Not a valid regex" }
            RuleKind.SenderId, RuleKind.Words -> null
        }
    }

    fun matches(rule: BlockRule, address: String, body: String?): Boolean {
        val p = rule.pattern.trim()
        if (p.isEmpty()) return false
        return when (rule.kind) {
            RuleKind.StartsWith -> startsWith(address, p)
            RuleKind.SenderId -> glob(p).matches(address.trim())
            RuleKind.Words -> body != null && words(p).any { body.contains(it, ignoreCase = true) }
            RuleKind.Regex -> runCatching {
                val regex = Regex(p, RegexOption.IGNORE_CASE)
                regex.containsMatchIn(address) || (body != null && regex.containsMatchIn(body))
            }.getOrDefault(false)
        }
    }

    /**
     * "+92" is checked against the number as it came; anything else against the Indian national
     * number too, so "140" catches +91 140xxxxxxx telemarketers.
     */
    private fun startsWith(address: String, pattern: String): Boolean {
        val raw = address.filter { it.isDigit() || it == '+' }
        val want = pattern.filter { it.isDigit() || it == '+' }
        if (want.isEmpty() || raw.isEmpty()) return false
        if (want.startsWith('+')) return raw.startsWith(want)
        val national = when {
            raw.startsWith("+91") -> raw.removePrefix("+91")
            raw.startsWith("0") -> raw.removePrefix("0")
            else -> raw.removePrefix("+")
        }
        return national.startsWith(want) || raw.removePrefix("+").startsWith(want)
    }

    /** "*-P" style: * is anything, the rest literal, case doesn't matter */
    private fun glob(pattern: String) = Regex(
        pattern.split('*').joinToString(".*") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    private fun words(pattern: String) = pattern.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
