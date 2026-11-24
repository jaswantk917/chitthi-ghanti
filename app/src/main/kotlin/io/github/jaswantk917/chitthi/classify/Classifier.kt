package io.github.jaswantk917.chitthi.classify

import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.model.Tag
import io.github.jaswantk917.chitthi.model.Verdict

/**
 * Sorts a message on the phone, in microseconds, from who sent it and what it says. Gemini Nano
 * only gets a second look at the ones marked [Verdict.unsure], and only while the app is open.
 */
object Classifier {
    private fun words(vararg w: String) = Regex("(?i)(" + w.joinToString("|") + ")")

    private val bill = words(
        "bill", "due", "overdue", "expir", "renew", "recharge", "valid till", "validity", "plan ends",
        "autopay", "mandate", "emi", "premium", "समाप्त", "रिचार्ज",
    )
    private val delivery = words(
        "shipped", "delivered", "out for delivery", "dispatched", "pickup", "pick-up", "arriving",
        "courier", "tracking", "order (?:#|no|id)", "your order", "delivery",
    )
    private val travel = words(
        "pnr", "boarding", "flight", "train", "coach", "berth", "check-in", "booking id", "journey",
        "e-ticket", "cab", "ride", "driver",
    )
    private val alert = words(
        "new (?:login|device|sign-?in)", "did not request", "didn't request", "not you", "fraud",
        "blocked", "suspicious", "never share", "do not share",
    )
    private val service = words(
        "a/c", "account", "card", "transaction", "txn", "statement", "upi", "balance", "appointment",
        "consultation", "booked", "confirmed", "reminder", "scheduled", "registered", "kyc",
        "policy", "complaint", "request", "otp", "invoice", "receipt",
    )
    private val promo = words(
        "offer", "discount", "% off", "cashback", "sale\\b", "deal", "hurry", "limited", "\\bfree\\b",
        "\\bwin\\b", "exclusive", "unlock", "upgrade", "click", "t&c", "shop now", "use code",
        "coupon", "pre-?approved", "instant loan", "apply now", "get up to", "upto", "lowest",
        "best price", "subscribe", "reply stop", "sms stop", "missed call", "give a missed",
        "download (?:the )?app", "flat \\d", "bumper", "lucky", "jackpot", "congratulations", "voucher",
        "ऑफर", "छूट", "मुफ्त", "जीतें", "checking their", "don't miss", "dont miss", "explore",
        "awareness", "greetings", "wishes", "happy ",
    )

    fun classify(sender: SenderId, body: String, isContact: Boolean): Verdict {
        val tags = buildList {
            Otp.extract(body)?.let { add(Tag.Otp(it)) }
            Money.parse(body)?.let { add(if (it.debit) Tag.Debit(it.value) else Tag.Credit(it.value)) }
            if (alert.containsMatchIn(body) && none { it is Tag.Otp }) add(Tag.Alert)
            // codes say when they expire too
            if (bill.containsMatchIn(body) && none { it is Tag.Debit || it is Tag.Credit || it is Tag.Otp }) add(Tag.Bill)
            if (delivery.containsMatchIn(body)) add(Tag.Delivery)
            if (travel.containsMatchIn(body)) add(Tag.Travel)
        }
        val important = tags.sumOf {
            when (it) {
                is Tag.Otp -> 6
                is Tag.Debit, is Tag.Credit -> 5
                Tag.Alert -> 4
                Tag.Delivery, Tag.Travel -> 3
                Tag.Bill -> 2
            }
        } + service.findAll(body).count().coerceAtMost(3)
        val promotional = promo.findAll(body).count() * 2 +
            (if (Links.domains(body).isNotEmpty() && important < 3) 1 else 0)

        if (isContact) return Verdict(Category.Personal, tags)
        return when (sender) {
            is SenderId.Phone -> when {
                important >= 3 && important >= promotional -> Verdict(Category.Important, tags)
                promotional >= 4 -> Verdict(Category.Promotions, tags)
                else -> Verdict(Category.Unknown, tags)
            }
            is SenderId.ShortCode -> when {
                important >= 2 && important >= promotional -> Verdict(Category.Important, tags)
                promotional >= 2 -> Verdict(Category.Promotions, tags)
                else -> Verdict(Category.Unknown, tags, unsure = true)
            }
            is SenderId.Business -> business(sender.route, important, promotional, tags)
            is SenderId.RcsBot -> business(null, important, promotional, tags)
        }
    }

    private fun business(route: Char?, important: Int, promotional: Int, tags: List<Tag>): Verdict {
        val hasOtp = tags.any { it is Tag.Otp }
        return when (route) {
            'P' -> Verdict(if (hasOtp) Category.Important else Category.Promotions, tags)
            'S', 'T', 'G' -> when {
                hasOtp || important >= 3 -> Verdict(Category.Important, tags)
                promotional >= 4 -> Verdict(Category.Promotions, tags)
                else -> Verdict(Category.Important, tags, unsure = promotional > 0)
            }
            else -> when {
                hasOtp -> Verdict(Category.Important, tags)
                important >= 3 && important >= promotional -> Verdict(Category.Important, tags)
                promotional > important -> Verdict(Category.Promotions, tags, unsure = promotional - important < 3)
                // no strong words either way: the "Vigilance Awareness Week" kind
                else -> Verdict(Category.Promotions, tags, unsure = true)
            }
        }
    }
}
