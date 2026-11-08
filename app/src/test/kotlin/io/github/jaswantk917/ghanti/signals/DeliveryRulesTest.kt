package io.github.jaswantk917.ghanti.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DeliveryRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 7, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val settings = ScreenSettings()
    private val stranger = "+91 98765 00001"

    @Test
    fun `app notifications name the app, chat messages name the brand they mention`() {
        val bistro = DeliveryRules.fromNotification("com.blinkit.bistro", "Bistro", "Your order is on the way! Arriving in 8 mins", t(13))
        assertEquals(Signal("Bistro", Strength.Live, t(13)), bistro)
        val whatsapp = DeliveryRules.fromNotification("com.whatsapp", "WhatsApp", "Amazon: Your package will be delivered today", t(9))
        assertEquals("Amazon", whatsapp?.brand)
        assertEquals(Strength.Today, whatsapp?.strength)
    }

    @Test
    fun `an unfamiliar food app still counts when its text says someone is coming`() {
        assertEquals("Snacc", DeliveryRules.fromNotification("com.snacc.app", "Snacc", "Your rider has picked up your order", t(13))?.brand)
    }

    @Test
    fun `ordinary notifications are not signals`() {
        assertNull(DeliveryRules.fromNotification("com.whatsapp", "WhatsApp", "Bhai kal milte hain", t(13)))
        assertNull(DeliveryRules.fromSms("AX-ICICIT-S", "Rs 500 debited from A/c XX1234. Not you? Call 18002586161", t(13)))
    }

    @Test
    fun `delivered beats on the way, and today is only a plan`() {
        assertEquals(Strength.Done, DeliveryRules.strengthOf("Your order has been delivered. Enjoy your meal!"))
        assertEquals(Strength.Live, DeliveryRules.strengthOf("Out for delivery: arriving today by 7 pm"))
        assertEquals(Strength.Today, DeliveryRules.strengthOf("Your shipment will be delivered today"))
    }

    @Test
    fun `courier SMS headers name the company`() {
        val signal = DeliveryRules.fromSms("JD-DLHVRY-S", "Your shipment AWB 123 is out for delivery. Agent: 98765 43210", t(10))
        assertEquals(Signal("Delhivery", Strength.Live, t(10), setOf("9876543210")), signal)
    }

    @Test
    fun `a number in the message makes it a sure thing`() {
        val signals = listOf(Signal("Zomato", Strength.Live, t(13), setOf("9876500001")))
        val guess = DeliveryRules.guess(stranger, signals, t(13, 20), zone)
        assertEquals(Guess("Zomato delivery", confident = true, brands = listOf("Zomato")), guess)
    }

    @Test
    fun `one live order is a probable guess, two are named together`() {
        val one = listOf(Signal("Bistro", Strength.Live, t(13)))
        assertEquals(Guess("Probably Bistro delivery", brands = listOf("Bistro")), DeliveryRules.guess(stranger, one, t(13, 10), zone))
        val two = one + Signal("Zepto", Strength.Live, t(13, 5))
        assertEquals(Guess("Probably a delivery", detail = "Zepto or Bistro", brands = listOf("Zepto", "Bistro")), DeliveryRules.guess(stranger, two, t(13, 10), zone))
    }

    @Test
    fun `rides say driver`() {
        val guess = DeliveryRules.guess(stranger, listOf(Signal("Uber", Strength.Live, t(19))), t(19, 5), zone)
        assertEquals(Guess("Probably Uber driver", ride = true, brands = listOf("Uber")), guess)
    }

    @Test
    fun `live hints fade after an hour and a half, today's at midnight`() {
        val live = listOf(Signal("Swiggy", Strength.Live, t(12)))
        assertNull(DeliveryRules.guess(stranger, live, t(13, 31), zone))
        val today = listOf(Signal("Amazon", Strength.Today, t(9)))
        assertEquals(Guess("Maybe Amazon delivery", brands = listOf("Amazon")), DeliveryRules.guess(stranger, today, t(21), zone))
        assertNull(DeliveryRules.guess(stranger, today, t(9) + 24 * 3_600_000L, zone))
    }

    @Test
    fun `once delivered, that brand stops getting the benefit of the doubt`() {
        val signals = listOf(Signal("Bistro", Strength.Live, t(13)), Signal("Bistro", Strength.Done, t(13, 15)))
        assertNull(DeliveryRules.guess(stranger, signals, t(13, 20), zone))
    }

    @Test
    fun `strangers are silenced unless something is coming or they call twice`() {
        assertFalse(DeliveryRules.decide(stranger, false, emptyList(), null, t(13), settings, zone).ring)
        assertTrue(DeliveryRules.decide(stranger, true, emptyList(), null, t(13), settings, zone).ring)
        val coming = DeliveryRules.decide(stranger, false, listOf(Signal("Bistro", Strength.Live, t(13))), null, t(13, 5), settings, zone)
        assertEquals(Decision(ring = true, guess = Guess("Probably Bistro delivery", brands = listOf("Bistro"))), coming)
        val again = DeliveryRules.decide(stranger, false, emptyList(), lastSilencedFromSame = t(13), now = t(13, 2), settings = settings, zone = zone)
        assertEquals(Decision(ring = true, repeat = true), again)
        assertFalse(DeliveryRules.decide(stranger, false, emptyList(), t(13), t(13, 10), settings, zone).ring)
    }

    @Test
    fun `with silencing off everyone rings`() {
        assertTrue(DeliveryRules.decide(stranger, false, emptyList(), null, t(13), settings.copy(silenceUnknown = false), zone).ring)
    }

    @Test
    fun `numbers are matched on their last ten digits`() {
        assertEquals(setOf("9876543210", "8123456789"), DeliveryRules.numbersIn("Call 098765 43210 or +91-8123456789, not 18002586161"))
    }
}
