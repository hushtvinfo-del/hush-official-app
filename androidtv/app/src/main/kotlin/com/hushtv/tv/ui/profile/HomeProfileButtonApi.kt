package com.hushtv.tv.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/**
 * v1.44.139-dev — Cross-flavor extension point for the top-right
 * profile-switch avatar button.
 *
 * Just like [ProfileGateApi], the real implementation lives in
 * `src/dev/kotlin/.../HomeTopProfileButton.kt` and is registered
 * into [HomeProfileButtonRegistry] at startup. Canada / official
 * stay on [NoOpHomeProfileButton] which renders nothing — the
 * home screen Box just has empty space where the avatar would be.
 *
 * The renderer takes [xtreamUsername] so a single multi-account
 * device still picks the right profile list, and an explicit
 * [onDownFromAvatar] callback so the dev impl can route D-pad
 * DOWN focus to the home page's first content row without
 * having to know its FocusRequester.
 */
interface HomeProfileButtonApi {
    @Composable
    fun Render(
        xtreamUsername: String,
        avatarFocusRequester: FocusRequester,
        onDownFromAvatar: () -> Unit,
        modifier: Modifier,
    )
}

object NoOpHomeProfileButton : HomeProfileButtonApi {
    @Composable
    override fun Render(
        xtreamUsername: String,
        avatarFocusRequester: FocusRequester,
        onDownFromAvatar: () -> Unit,
        modifier: Modifier,
    ) {
        // Render nothing — canada / official keep the home screen
        // pixel-identical to v1.44.137.
    }
}

object HomeProfileButtonRegistry {
    @Volatile
    var current: HomeProfileButtonApi = NoOpHomeProfileButton
        private set

    fun install(impl: HomeProfileButtonApi) {
        current = impl
    }
}
