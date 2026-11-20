package com.ratsey.thelist.ui

/**
 * The run of skipped "what did it cost?" questions. Two in a row is a strong hint that the question
 * is not wanted in the middle of a shop, so it is worth offering to switch it off.
 */
data class SkipStreak(val consecutive: Int = 0)

/** What one skip did to the run, and whether it is time to ask about turning the prompt off. */
data class SkipOutcome(val streak: SkipStreak, val offerTurnOff: Boolean)

object PricePromptOffer {

    const val AFTER = 2

    fun onSkip(streak: SkipStreak): SkipOutcome =
        if (streak.consecutive + 1 >= AFTER) {
            // Cleared here as well as when the offer is answered: a decline starts a fresh run
            // instead of nagging again on the very next skip.
            SkipOutcome(SkipStreak(), offerTurnOff = true)
        } else {
            SkipOutcome(SkipStreak(streak.consecutive + 1), offerTurnOff = false)
        }

    /** A saved price, an answered offer or a change of setting all end the current run. */
    fun reset(): SkipStreak = SkipStreak()
}
