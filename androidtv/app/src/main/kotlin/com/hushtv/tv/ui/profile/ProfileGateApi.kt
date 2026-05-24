package com.hushtv.tv.ui.profile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * v1.44.138-dev — Cross-flavor seam for the multi-profile feature.
 *
 * The ACTUAL profile gate composable (picker, setup, edit screens)
 * lives in `src/dev/kotlin/com/hushtv/tv/ui/profile/` and uses
 * R.drawable.avatar_* references that only exist in the dev source
 * set. To keep canada / official builds compiling cleanly, the
 * shared call site references this interface instead — and dev
 * registers a real implementation at app startup, while canada /
 * official keep the [NoOpProfileGate] default.
 *
 * Wiring:
 *   • src/main/HushTVApp.kt calls `DevProfileGate.installIfDevFlavor()`
 *     during onCreate. On the dev flavor that swaps the
 *     installed gate to the real impl. On other flavors it's a
 *     no-op (the dev-only registrar class doesn't exist there).
 *   • MainActivity wraps its top-level composable with
 *     `ProfileGateRegistry.current.Wrap(xtreamUsername) { … }`.
 *
 * Net effect: canada and official APKs behave EXACTLY like
 * v1.44.137 — the content composes immediately with no profile
 * picker, no avatar resources required.
 */
interface ProfileGateApi {
    @Composable
    fun Wrap(xtreamUsername: String, content: @Composable () -> Unit)

    /** Called by Settings → "Switch Profile" to force the picker
     *  on the NEXT navigation. On canada / official: no-op. */
    fun requestSwitch(ctx: Context, xtreamUsername: String)
}

/** Default implementation — content renders directly, no profile
 *  affordance. Used on canada / official / any flavor that hasn't
 *  registered a real gate. */
object NoOpProfileGate : ProfileGateApi {
    @Composable
    override fun Wrap(xtreamUsername: String, content: @Composable () -> Unit) {
        content()
    }

    override fun requestSwitch(ctx: Context, xtreamUsername: String) {
        // intentionally nothing
    }
}

/** Process-global registry. Set during HushTVApp.onCreate before
 *  any UI composes. */
object ProfileGateRegistry {
    @Volatile
    var current: ProfileGateApi = NoOpProfileGate
        private set

    fun install(impl: ProfileGateApi) {
        current = impl
    }
}
