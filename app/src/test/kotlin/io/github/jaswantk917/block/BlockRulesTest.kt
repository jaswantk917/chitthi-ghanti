package io.github.jaswantk917.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockRulesTest {
    private fun rule(kind: RuleKind, pattern: String, applies: Applies = Applies.Both) = BlockRule(1, kind, pattern, applies)

    @Test fun `140 catches telemarketers however the number arrives`() {
        val r = listOf(rule(RuleKind.StartsWith, "140", Applies.Calls))
        assertNotNull(BlockRules.forCall(r, "+911401234567", false))
        assertNotNull(BlockRules.forCall(r, "01401234567", false))
        assertNotNull(BlockRules.forCall(r, "1401234567", false))
        assertNull(BlockRules.forCall(r, "+919876514000", false))
    }

    @Test fun `a plus pattern is a country code`() {
        val r = listOf(rule(RuleKind.StartsWith, "+92"))
        assertNotNull(BlockRules.forCall(r, "+92 300 1234567", false))
        assertNull(BlockRules.forCall(r, "+919212345678", false))
    }

    @Test fun `sender ID globs match promo routes and one company everywhere`() {
        val promo = listOf(rule(RuleKind.SenderId, "*-P"))
        assertNotNull(BlockRules.forSms(promo, "AX-MYNTRA-P", "Sale!", false))
        assertNull(BlockRules.forSms(promo, "AX-HDFCBK-S", "OTP 123456", false))
        val company = listOf(rule(RuleKind.SenderId, "*-AMAZON*"))
        assertNotNull(BlockRules.forSms(company, "jd-amazon-s", "Your order", false))
    }

    @Test fun `words match anywhere, any case, any of them`() {
        val r = listOf(rule(RuleKind.Words, "loan approved, lottery"))
        assertNotNull(BlockRules.forSms(r, "+919800000000", "Congratulations! Your LOAN APPROVED today", false))
        assertNull(BlockRules.forSms(r, "+919800000000", "See you at 6", false))
    }

    @Test fun `sms-only rules never touch calls`() {
        val r = listOf(rule(RuleKind.Words, "loan", Applies.Both), rule(RuleKind.StartsWith, "98", Applies.Sms))
        assertNull(BlockRules.forCall(r, "+919812345678", false))
    }

    @Test fun `contacts are never caught`() {
        val r = listOf(rule(RuleKind.StartsWith, "98"))
        assertNull(BlockRules.forCall(r, "+919812345678", isContact = true))
        assertNull(BlockRules.forSms(r, "+919812345678", "hi", isContact = true))
    }

    @Test fun `regex checks the number and the message, and a broken one is refused`() {
        val r = listOf(rule(RuleKind.Regex, "free\\s+gift"))
        assertNotNull(BlockRules.forSms(r, "VM-SHOPPY-P", "Claim your FREE  gift", false))
        assertEquals("Not a valid regex", BlockRules.problem(RuleKind.Regex, "([a-z"))
        assertEquals("Only digits and +", BlockRules.problem(RuleKind.StartsWith, "14a"))
        assertNull(BlockRules.problem(RuleKind.StartsWith, "+92"))
    }
}
