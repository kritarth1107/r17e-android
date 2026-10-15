package com.r17e.guide.backend

/** Runs [block] with an access token; if the server says it expired or was revoked (401), refreshes once and retries. */
fun <T> withAuthRetry(token: () -> String, invalidate: () -> Unit, block: (String) -> T): T =
    try { block(token()) } catch (e: BackendException) {
        if (e.code != 401) throw e
        invalidate(); block(token())
    }
