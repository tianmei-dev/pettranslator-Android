package com.example.pettranslator

import com.example.pettranslator.ui.recording.rmsToWaveLevel
import com.example.pettranslator.ui.recording.pcmFrameToWaveLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformLevelsTest {
    @Test fun silenceStaysFlatAndSpeechRangeIsVisible() {
        assertEquals(0f, rmsToWaveLevel(0f), 0f)
        assertEquals(0f, rmsToWaveLevel(.0004f), 0f)
        assertEquals(0f, rmsToWaveLevel(.001f), 0f)
        assertTrue(rmsToWaveLevel(.01f) in .20f.. .32f)
    }

    @Test fun loudInputIsBoundedAndInvalidLevelsAreIgnored() {
        assertEquals(1f, rmsToWaveLevel(1f), 0f)
        assertEquals(1f, rmsToWaveLevel(3f), 0f)
        assertEquals(0f, rmsToWaveLevel(Float.NaN), 0f)
        assertEquals(0f, rmsToWaveLevel(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun pcmFrameEnvelopeRespondsToRealPeakWithoutRaisingSilence() {
        assertEquals(0f, pcmFrameToWaveLevel(.0001f, .0004f), 0f)
        val steady = pcmFrameToWaveLevel(.004f, .008f)
        val transient = pcmFrameToWaveLevel(.004f, .12f)
        assertTrue(transient > steady + .15f)
        assertTrue(pcmFrameToWaveLevel(2f, 3f) <= 1f)
    }

    @Test fun speechAndLoudSignalsKeepVisibleVariationAndHeadroom() {
        val quiet = pcmFrameToWaveLevel(.001f, .004f)
        val speech = pcmFrameToWaveLevel(.03f, .20f)
        val loud = pcmFrameToWaveLevel(.15f, .55f)
        assertTrue(quiet < .05f)
        assertTrue(speech in .5f.. .8f)
        assertTrue(loud > speech && loud < .96f)
    }
}
