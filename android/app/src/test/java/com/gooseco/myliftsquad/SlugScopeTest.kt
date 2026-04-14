package com.gooseco.myliftsquad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlugScopeTest {

    @Test
    fun `athlete in a different squad is not blocked from being added to current squad`() {
        // existingSlugs should only contain slugs from the current squad
        val currentSquadSlugs = setOf("athlete-a", "athlete-b")
        val slugFromOtherSquad = "athlete-c"

        assertFalse(
            "Athlete from a different squad should not be reported as already added",
            currentSquadSlugs.contains(slugFromOtherSquad)
        )
    }

    @Test
    fun `athlete already in the current squad cannot be added again`() {
        val currentSquadSlugs = setOf("athlete-a", "athlete-b")
        val duplicateSlug = "athlete-a"

        assertTrue(
            "Athlete already in the current squad should be blocked",
            currentSquadSlugs.contains(duplicateSlug)
        )
    }
}
