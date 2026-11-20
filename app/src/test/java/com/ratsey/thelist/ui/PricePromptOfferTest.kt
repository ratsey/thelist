package com.ratsey.thelist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PricePromptOfferTest {

    @Test
    fun `one skip does not offer to turn the question off`() {
        val outcome = PricePromptOffer.onSkip(SkipStreak())
        assertFalse(outcome.offerTurnOff)
        assertEquals(1, outcome.streak.consecutive)
    }

    @Test
    fun `two skips in a row offer to turn the question off`() {
        val first = PricePromptOffer.onSkip(SkipStreak())
        val second = PricePromptOffer.onSkip(first.streak)
        assertTrue(second.offerTurnOff)
        assertEquals(0, second.streak.consecutive)
    }

    @Test
    fun `showing the offer starts a fresh run`() {
        val first = PricePromptOffer.onSkip(SkipStreak())
        val second = PricePromptOffer.onSkip(first.streak)
        val afterOffer = PricePromptOffer.onSkip(second.streak)
        assertFalse(afterOffer.offerTurnOff)
        assertEquals(1, afterOffer.streak.consecutive)
    }

    @Test
    fun `a saved price breaks the run`() {
        PricePromptOffer.onSkip(SkipStreak())
        val afterSave = PricePromptOffer.reset()
        assertEquals(0, afterSave.consecutive)
        assertFalse(PricePromptOffer.onSkip(afterSave).offerTurnOff)
    }
}
