// Forecast playback (the desktop's playback bar and _apply_playback).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.ClockFormat
import io.github.shanebl030glitch.rbsbweather.data.Hour
import io.github.shanebl030glitch.rbsbweather.ui.scene.sceneFc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PlaybackTest {
    private val fc = sceneFc(now = "2026-11-15T10:00").let { f ->
        f.copy(hourly = (0 until 30).map { Hour(LocalDateTime.parse("2026-11-15T10:00").plusHours(it.toLong()).toString(), 10.0 + it, 5.0 * it, 0.0, 61) },
            fetchedAt = 0L)
    }

    @Test fun anHourASecond() {
        assertEquals(3600.0 to false, playTick(0.0, 1.0, 86400.0))
        assertEquals(86400.0 to true, playTick(86000.0, 1.0, 86400.0))
        assertEquals(96, playbackSteps(86400.0))
        assertEquals(0, playbackSteps(0.0))
    }

    @Test fun stampAndHour() {
        assertEquals("Sun 13:15", playbackStamp(fc, 3 * 3600 + 900.0, ClockFormat.H24))
        assertEquals("Sun 1:15 pm", playbackStamp(fc, 3 * 3600 + 900.0, ClockFormat.H12))
        assertEquals(13.0, playbackHour(fc, 3 * 3600 + 900.0)!!.temperature!!, 0.0)
        assertNull(playbackHour(fc, 40 * 3600.0))
    }

    @Test fun unavailableSaysWhy() {
        assertTrue(playbackUnavailable(fc.copy(hourly = emptyList(), source = "Weather Underground")).contains("doesn't provide an hourly forecast"))
        assertEquals("No hourly forecast ahead", playbackUnavailable(fc))
    }
}
