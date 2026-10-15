package com.r17e.guide

import com.r17e.guide.backend.BackendException
import com.r17e.guide.backend.withAuthRetry
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRetryTest {
    @Test fun expiredTokenIsRefreshedOnce() {
        var tok = "old"; var calls = 0
        val r = withAuthRetry({ tok }, { tok = "new" }) { t -> calls++; if (t == "old") throw BackendException(401, "expired") else "ok:$t" }
        assertEquals("ok:new", r); assertEquals(2, calls)
    }

    @Test(expected = BackendException::class) fun otherErrorsAreNotRetried() {
        var calls = 0
        try { withAuthRetry({ "t" }, {}) { calls++; throw BackendException(500, "x") } } finally { assertEquals(1, calls) }
    }

    @Test(expected = BackendException::class) fun secondRejectionPropagates() {
        withAuthRetry({ "t" }, {}) { throw BackendException(401, "revoked") }
    }
}
