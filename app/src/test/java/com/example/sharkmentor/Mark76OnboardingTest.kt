package com.example.sharkmentor

import com.example.sharkmentor.ui.components.SuperpowerOnboardingCatalog
import com.example.sharkmentor.ui.components.SuperpowerSlideId
import com.example.sharkmentor.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark76OnboardingTest {

    @Test
    fun catalogDefinesExactlyFourUniqueSuperpowerSlides() {
        val slides = SuperpowerOnboardingCatalog.slides
        assertEquals(4, slides.size)
        assertEquals(
            setOf(
                SuperpowerSlideId.VISUAL_AI_SOLVER,
                SuperpowerSlideId.SHARK_STUDIO_LASSO,
                SuperpowerSlideId.FSRS_ERROR_BANK,
                SuperpowerSlideId.COACHING_FOCUS_LIBRARY
            ),
            slides.map { it.id }.toSet()
        )
        slides.forEach { slide ->
            assertTrue(slide.badge.isNotBlank())
            assertTrue(slide.title.isNotBlank())
            assertTrue(slide.description.isNotBlank())
            assertTrue(slide.primaryActionLabel.isNotBlank())
            assertTrue(slide.secondaryTryLabel.isNotBlank())
            assertTrue(slide.targetRoute.isNotBlank())
        }
    }

    @Test
    fun slideRoutesAndAiPromptPayloadsMatchAppNavigation() {
        val slides = SuperpowerOnboardingCatalog.slides

        val slide1 = slides[0]
        assertEquals(SuperpowerSlideId.VISUAL_AI_SOLVER, slide1.id)
        assertEquals(Screen.Shark.route, slide1.targetRoute)
        assertEquals("/detayli ", slide1.aiPromptPayload)
        assertEquals("Şimdi Dene", slide1.secondaryTryLabel)

        val slide2 = slides[1]
        assertEquals(SuperpowerSlideId.SHARK_STUDIO_LASSO, slide2.id)
        assertEquals(Screen.Notes.route, slide2.targetRoute)
        assertNull(slide2.aiPromptPayload)
        assertEquals("SharkStudio'yu Aç", slide2.secondaryTryLabel)

        val slide3 = slides[2]
        assertEquals(SuperpowerSlideId.FSRS_ERROR_BANK, slide3.id)
        assertEquals(Screen.ErrorBank.route, slide3.targetRoute)
        assertNull(slide3.aiPromptPayload)
        assertEquals("Hata Bankasına Git", slide3.secondaryTryLabel)

        val slide4 = slides[3]
        assertEquals(SuperpowerSlideId.COACHING_FOCUS_LIBRARY, slide4.id)
        assertEquals(Screen.Focus.route, slide4.targetRoute)
        assertNull(slide4.aiPromptPayload)
        assertEquals("Hemen Başla", slide4.primaryActionLabel)
        assertFalse(slide4.secondaryTryLabel.isBlank())
    }
}
