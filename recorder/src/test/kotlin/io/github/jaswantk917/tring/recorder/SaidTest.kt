package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

class SaidTest {
    @Test
    fun `a pause keeps what came before it`() {
        val said = Said()
        said.began()
        said.partial("मेरा नाम")
        said.partial("मेरा नाम राहुल है")
        said.finished("मेरा नाम राहुल है")
        said.began()
        said.partial("कल शाम")
        assertEquals("मेरा नाम राहुल है कल शाम", said.text)
        said.finished(" कल शाम को मिलना है")
        assertEquals("मेरा नाम राहुल है कल शाम को मिलना है", said.text)
    }

    @Test
    fun `the empty stretch that ends the session changes nothing`() {
        val said = Said()
        said.partial("हाँ जी")
        said.finished("हाँ जी")
        said.finished("")
        assertEquals("हाँ जी", said.text)
    }

    @Test
    fun `audio running out mid-stretch keeps that stretch`() {
        val said = Said()
        said.finished("पहली बात")
        said.partial("दूसरी")
        said.finished("")
        assertEquals("पहली बात दूसरी", said.text)
    }

    @Test
    fun `a recognizer that never finishes stretches still loses nothing at a pause`() {
        val said = Said()
        said.began()
        said.partial("मेरा नाम राहुल है")
        said.partial(" मेरा नाम राहुल है")
        said.began()
        said.partial("कल शाम")
        assertEquals("मेरा नाम राहुल है कल शाम", said.text)
    }

    @Test
    fun `a stretch finished after the next one began is not said twice`() {
        val said = Said()
        said.partial("मेरा नाम राहुल है")
        said.began()
        said.finished("मेरा नाम राहुल है")
        said.partial("कल शाम")
        assertEquals("मेरा नाम राहुल है कल शाम", said.text)
    }

    @Test
    fun `nothing heard is nothing said`() {
        val said = Said()
        said.began()
        said.finished("")
        assertEquals("", said.text)
    }
}
