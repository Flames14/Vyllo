package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationPromptTest {

    @Test
    fun `numbered provider response preserves line order`() {
        assertEquals(
            listOf("hola", "mundo"),
            TranslationPrompt.parseNumberedLines("2. mundo\n1. hola", expected = 2)
        )
    }

    @Test
    fun `duplicate or missing numbered lines are rejected`() {
        assertNull(TranslationPrompt.parseNumberedLines("1. hola\n1. mundo", expected = 2))
        assertNull(TranslationPrompt.parseNumberedLines("1. hola\n3. mundo", expected = 2))
    }

    @Test
    fun `plain provider response is accepted only with exact line count`() {
        assertEquals(listOf("hola", "mundo"), TranslationPrompt.parseNumberedLines("hola\nmundo", 2))
        assertNull(TranslationPrompt.parseNumberedLines("1. hola\n1. mundo", 2))
    }
}
