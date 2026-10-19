package com.example

import com.example.data.model.AspectRatioType
import com.example.data.model.FilterPreset
import com.example.data.repository.VideoEditorRepository
import com.example.service.GeminiAiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    private lateinit var repository: VideoEditorRepository
    private lateinit var geminiService: GeminiAiService

    @Before
    fun setUp() {
        repository = VideoEditorRepository()
        geminiService = GeminiAiService()
    }

    @Test
    fun testInitialProjectsLoaded() {
        val projects = repository.projects.value
        assertTrue("Projects should not be empty", projects.isNotEmpty())
        val current = repository.currentProject.value
        assertNotNull("Current project should be selected", current)
        assertEquals("proj_01", current?.id)
        assertEquals("NEW IN FASHION", current?.title)
    }

    @Test
    fun testCreateNewProject() {
        val newProj = repository.createNewProject("Fashion Runway", AspectRatioType.RATIO_9_16)
        assertEquals("Fashion Runway", newProj.title)
        assertEquals(AspectRatioType.RATIO_9_16, newProj.aspectRatio)
        assertTrue(repository.projects.value.contains(newProj))
    }

    @Test
    fun testUpdateAspectRatioAndFilter() {
        repository.updateAspectRatio(AspectRatioType.RATIO_16_9)
        assertEquals(AspectRatioType.RATIO_16_9, repository.currentProject.value?.aspectRatio)

        repository.updateFilter(FilterPreset.TEAL_ORANGE)
        assertEquals(FilterPreset.TEAL_ORANGE, repository.currentProject.value?.activeFilter)
    }

    @Test
    fun testApplyAutoEdit() {
        val initialCredits = repository.userProfile.value.aiCredits
        repository.applyAutoEditEnhancement()
        val updatedCredits = repository.userProfile.value.aiCredits
        assertEquals(initialCredits - 15, updatedCredits)
        assertEquals("AI Enhanced", repository.currentProject.value?.status)
    }

    @Test
    fun testAudioTranscriptionFallback() = runBlocking {
        val sampleAudio = "Mock Audio Stream".toByteArray()
        val text = geminiService.transcribeAudio(sampleAudio)
        assertTrue("Transcribed text should not be empty", text.isNotBlank())
    }

    @Test
    fun testVeoVideoGeneration() = runBlocking {
        val (success, message) = geminiService.generateVeoVideo("Runway slow motion", "9:16")
        assertTrue("Veo video generation should succeed", success)
        assertTrue("Veo message should mention aspect ratio", message.contains("9:16"))
    }
}
