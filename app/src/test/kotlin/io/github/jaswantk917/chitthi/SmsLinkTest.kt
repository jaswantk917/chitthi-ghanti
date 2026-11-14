package io.github.jaswantk917.chitthi

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsLinkTest {
    @Test
    fun `a link with a body fills both`() =
        assertEquals(SmsLink("+919999999999", "hi there"), SmsLink.parse("+919999999999?body=hi%20there"))

    @Test
    fun `a stray percent stays as text`() =
        assertEquals(SmsLink("+123123123", "hi%kat"), SmsLink.parse("+123123123?body=hi%kat"))

    @Test
    fun `smsto with slashes, several recipients and other fields takes the first number and the body`() =
        assertEquals(SmsLink("+911234", "ok & done"), SmsLink.parse("//+911234,+915678?subject=x&body=ok%20%26%20done"))

    @Test
    fun `an encoded number and unicode body decode`() =
        assertEquals(SmsLink("+91 98", "नमस्ते"), SmsLink.parse("%2B91%2098?body=%E0%A4%A8%E0%A4%AE%E0%A4%B8%E0%A5%8D%E0%A4%A4%E0%A5%87"))

    @Test
    fun `no number opens a new chat`() {
        assertEquals(SmsLink(null, "hello"), SmsLink.parse("?body=hello"))
        assertEquals(SmsLink(null, ""), SmsLink.parse(""))
    }

    @Test
    fun `a plus in the number is kept`() = assertEquals(SmsLink("+919999999999", ""), SmsLink.parse("+919999999999"))
}
