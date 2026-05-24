package com.hushtv.tv.ui.profile.dev

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hushtv.tv.data.ActiveProfile
import com.hushtv.tv.data.HushProfile
import com.hushtv.tv.data.LocalActiveProfile
import com.hushtv.tv.data.ProfileStore
import com.hushtv.tv.ui.profile.HomeProfileButtonApi
import com.hushtv.tv.ui.profile.HomeProfileButtonRegistry
import com.hushtv.tv.ui.profile.NoOpHomeProfileButton
import com.hushtv.tv.ui.profile.NoOpProfileGate
import com.hushtv.tv.ui.profile.ProfileGateApi
import com.hushtv.tv.ui.profile.ProfileGateRegistry

/**
 * v1.44.138-dev — REAL implementation of [ProfileGateApi] for the
 * dev flavor. Registers itself into [ProfileGateRegistry] at app
 * startup via [installIfDevFlavor].
 *
 * State machine inside [Wrap]:
 *
 *   1. profiles.isEmpty()           → ProfileSetupScreen (first-time)
 *   2. active == null               → ProfilePickerScreen
 *   3. active != null && !switching → content()  (let app render)
 *   4. switching                    → ProfilePickerScreen
 *
 * "Switching" is triggered by [requestSwitch] (Settings → Switch
 * Profile). It clears the active id in [ProfileStore] and bumps a
 * recomposition signal, taking us back to state 2.
 */
class DevProfileGate : ProfileGateApi {

    // Process-global recompose signal so requestSwitch() can be
    // called from outside the composition.
    private var switchRequested by mutableStateOf(0)
    private var manageMode by mutableStateOf(false)

    @Composable
    override fun Wrap(xtreamUsername: String, content: @Composable () -> Unit) {
        val ctx = LocalContext.current
        val gateState = remember { mutableStateOf(loadState(ctx, xtreamUsername)) }

        // Re-read profile list whenever switchRequested mutates.
        LaunchedEffect(switchRequested, xtreamUsername) {
            gateState.value = loadState(ctx, xtreamUsername)
        }

        when (val state = gateState.value) {
            is GateState.FirstTimeSetup -> {
                ProfileSetupScreen(
                    xtreamUsername = xtreamUsername,
                    existing = null,
                    allowDelete = false,
                    onSaved = { p ->
                        // v1.44.138-dev — Returning user's first
                        // profile inherits their legacy Continue
                        // Watching / My List / Last Channel data
                        // so they don't see an empty home screen
                        // after the v138 update.
                        com.hushtv.tv.data.ProfileMigration.runOnce(
                            ctx,
                            xtreamUsername,
                            p.id,
                        )
                        ProfileStore.setActive(ctx, xtreamUsername, p.id)
                        ActiveProfile.set(p)
                        gateState.value = GateState.Ready(p)
                    },
                    onDeleted = {},   // not reachable in this mode
                    onCancel = {
                        // Force them to complete setup — there's no
                        // existing profile to fall back to.
                    },
                )
            }
            is GateState.Picking -> {
                ProfileChooserHost(
                    profiles = state.profiles,
                    xtreamUsername = xtreamUsername,
                    startInManageMode = manageMode,
                    onPicked = { p ->
                        ProfileStore.setActive(ctx, xtreamUsername, p.id)
                        ActiveProfile.set(p)
                        manageMode = false
                        gateState.value = GateState.Ready(p)
                    },
                    onListChanged = {
                        gateState.value = loadState(ctx, xtreamUsername)
                    },
                )
            }
            is GateState.Ready -> {
                CompositionLocalProvider(LocalActiveProfile provides state.profile) {
                    content()
                }
            }
        }
    }

    override fun requestSwitch(ctx: Context, xtreamUsername: String) {
        ProfileStore.clearActive(ctx, xtreamUsername)
        ActiveProfile.set(null)
        manageMode = false
        switchRequested++
    }

    override fun requestManage(ctx: Context, xtreamUsername: String) {
        // Same path as requestSwitch — clear active, force re-pick
        // through the gate — but the gate observes manageMode and
        // routes into ProfileManageScreen (edit-mode picker)
        // instead of the regular picker.
        ProfileStore.clearActive(ctx, xtreamUsername)
        ActiveProfile.set(null)
        manageMode = true
        switchRequested++
    }

    private fun loadState(ctx: Context, xtreamUsername: String): GateState {
        val list = ProfileStore.list(ctx, xtreamUsername)
        if (list.isEmpty()) return GateState.FirstTimeSetup
        val active = ProfileStore.active(ctx, xtreamUsername)
        return if (active != null) {
            ActiveProfile.set(active)
            GateState.Ready(active)
        } else {
            ActiveProfile.set(null)
            GateState.Picking(list)
        }
    }

    private sealed interface GateState {
        data object FirstTimeSetup : GateState
        data class Picking(val profiles: List<HushProfile>) : GateState
        data class Ready(val profile: HushProfile) : GateState
    }

    companion object {
        /** Called from HushTVApp.onCreate. No-op on flavors that
         *  haven't been promoted to the profile system. */
        @JvmStatic
        fun installIfDevFlavor() {
            if (ActiveProfile.isActive) {
                ProfileGateRegistry.install(DevProfileGate())
                // v1.44.139-dev — Also install the top-right
                // avatar renderer for the home screen.
                HomeProfileButtonRegistry.install(DevHomeProfileButton)
            } else {
                ProfileGateRegistry.install(NoOpProfileGate)
                HomeProfileButtonRegistry.install(NoOpHomeProfileButton)
            }
        }
    }
}

/**
 * v1.44.139-dev — Dev-flavor implementation of
 * [HomeProfileButtonApi]. Renders the avatar pill in the top-right
 * of the home screen content area; click jumps to the picker.
 */
object DevHomeProfileButton : HomeProfileButtonApi {
    @Composable
    override fun Render(
        xtreamUsername: String,
        avatarFocusRequester: FocusRequester,
        onDownFromAvatar: () -> Unit,
        modifier: Modifier,
    ) {
        val active = LocalActiveProfile.current
            ?: ActiveProfile.get()  // fallback in case LocalActiveProfile wasn't provided
        HomeTopProfileButton(
            activeProfile = active,
            xtreamUsername = xtreamUsername,
            focusRequester = avatarFocusRequester,
            onDownFromAvatar = onDownFromAvatar,
            modifier = modifier,
        )
    }
}

/**
 * Host composable for the "picking" state. Wires the picker
 * screen + "Manage Profiles" view + "Add Profile" routing into a
 * tiny state machine so the gate's main `when` stays readable.
 */
@Composable
private fun ProfileChooserHost(
    profiles: List<HushProfile>,
    xtreamUsername: String,
    startInManageMode: Boolean,
    onPicked: (HushProfile) -> Unit,
    onListChanged: () -> Unit,
) {
    var mode by remember(startInManageMode) {
        mutableStateOf<Mode>(if (startInManageMode) Mode.Managing else Mode.Picking)
    }

    when (val m = mode) {
        Mode.Picking -> {
            ProfilePickerScreen(
                profiles = profiles,
                onPicked = onPicked,
                onAdd = { mode = Mode.Adding },
                onManage = { mode = Mode.Managing },
            )
        }
        Mode.Adding -> {
            ProfileSetupScreen(
                xtreamUsername = xtreamUsername,
                existing = null,
                allowDelete = false,
                onSaved = {
                    onListChanged()
                    mode = Mode.Picking
                },
                onDeleted = {},
                onCancel = { mode = Mode.Picking },
            )
        }
        Mode.Managing -> {
            ProfileManageScreen(
                profiles = profiles,
                onEdit = { mode = Mode.Editing(it) },
                onAdd = { mode = Mode.Adding },
                onBack = { mode = Mode.Picking },
            )
        }
        is Mode.Editing -> {
            ProfileSetupScreen(
                xtreamUsername = xtreamUsername,
                existing = m.profile,
                allowDelete = profiles.size >= 2,
                onSaved = {
                    onListChanged()
                    mode = Mode.Managing
                },
                onDeleted = {
                    onListChanged()
                    mode = Mode.Managing
                },
                onCancel = { mode = Mode.Managing },
            )
        }
    }
}

private sealed interface Mode {
    data object Picking : Mode
    data object Adding : Mode
    data object Managing : Mode
    data class Editing(val profile: HushProfile) : Mode
}

@Composable
private fun ProfileManageScreen(
    profiles: List<HushProfile>,
    onEdit: (HushProfile) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    // Reuses the picker screen visually but with edit-mode semantics:
    // tap = edit, not pick. The simplest way to ship this is to
    // re-render the picker and re-route its `onPicked` to `onEdit`.
    ProfilePickerScreen(
        profiles = profiles,
        onPicked = onEdit,
        onAdd = onAdd,
        onManage = onBack,  // "Manage Profiles" link doubles as Back here
    )
}
