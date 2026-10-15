package com.r17e.guide.a11y

import com.r17e.guide.a11y.A11yStatus.State
import org.junit.Assert.assertEquals
import org.junit.Test

class A11yStatusTest {
    @Test fun connectedIsOnEvenIfSettingLags() = assertEquals(State.ON, A11yStatus.state(false, true, null, 0))
    @Test fun settingOffIsOff() = assertEquals(State.OFF, A11yStatus.state(false, false, null, 0))
    @Test fun enabledButNotBoundIsStartingDuringGrace() = assertEquals(State.STARTING, A11yStatus.state(true, false, 1_000, 1_000 + A11yStatus.GRACE_MS - 1))
    @Test fun enabledButNeverBoundBecomesStuck() = assertEquals(State.STUCK, A11yStatus.state(true, false, 1_000, 1_000 + A11yStatus.GRACE_MS))
    @Test fun noFalseOffRightAfterReinstall() = assertEquals(State.STARTING, A11yStatus.state(true, false, null, 5))
}
