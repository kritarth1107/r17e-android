package com.r17e.guide

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r17e.guide.a11y.A11yStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusOnDeviceTest {
    @Test fun connectedFlagRoundTrips() {
        val before = A11yStatus.connected.value
        A11yStatus.setConnected(true); assertEquals(true, A11yStatus.connected.value)
        A11yStatus.setConnected(before)
    }
}
