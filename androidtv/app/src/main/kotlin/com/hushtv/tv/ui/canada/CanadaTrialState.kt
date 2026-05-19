package com.hushtv.tv.ui.canada

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.44.94 — Canada free trial countdown state.
 *
 * Lives across the activity lifecycle so the top-bar badge in the
 * Canada flavor can subscribe without having to plumb the value down
 * from CanadaLicenseGate through every layout level.
 *
 * Updated by [CanadaLicenseGate] every poll (~2 s). The home screen
 * top bar observes [expiresAtMs] and renders a "Free trial: 2d 4h"
 * pill while it's non-null.
 */
object CanadaTrialState {
    private val _expiresAtMs = MutableStateFlow<Long?>(null)
    val expiresAtMs: StateFlow<Long?> = _expiresAtMs.asStateFlow()

    fun set(expiresAtMs: Long?) {
        _expiresAtMs.value = expiresAtMs
    }
}
