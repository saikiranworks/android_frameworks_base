/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.content.Context
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.flags.Flags as ViewFlags
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.PlatformTheme
import com.android.compose.theme.colorAttr
import com.android.systemui.Flags
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.axdynamicbar.ui.compose.AxDynamicBarChip
import com.android.systemui.clock.ClockModernization
import com.android.systemui.clock.ui.composable.Clock
import com.android.systemui.clock.ui.viewmodel.AmPmStyle
import com.android.systemui.clock.ui.viewmodel.ClockViewModel
import com.android.systemui.common.ui.compose.gestures.detectTapGesturesStrict
import com.android.systemui.communal.ui.compose.extensions.detectLongPressGesture
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.headline.ui.compose.Headline
import com.android.systemui.headline.ui.compose.drawWithHeadlineScrim
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.initOnBackPressedDispatcherOwner
import com.android.systemui.lifecycle.WindowLifecycleState
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.viewModel
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.res.R
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.shade.ui.composable.VariableDayDate
import com.android.systemui.statusbar.StatusBarAlwaysUseRegionSampling
import com.android.systemui.statusbar.backtoapp.BackToAppLabel
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChips
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.core.StatusBarEventForwardingModernization
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.events.domain.interactor.SystemStatusEventAnimationInteractor
import com.android.systemui.statusbar.layout.ui.viewmodel.AppHandlesViewModel
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.ConnectedDisplaysStatusBarNotificationIconViewStore
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerStatusBarViewBinder
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.notification.shared.StatusBarHeadline
import com.android.systemui.statusbar.phone.NotificationIconContainer
import com.android.systemui.statusbar.phone.PhoneStatusBarView
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.phone.ui.DarkIconManager
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.pipeline.battery.ui.composable.BatteryWithPercent
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarIconBlockListBinder
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarTouchExclusionRegionBinder
import com.android.systemui.statusbar.pipeline.shared.ui.binder.HomeStatusBarViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.view.SystemStatusIconsLayoutHelper
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel.HomeStatusBarViewModelFactory
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.domain.interactor.SystemStatusIconBlocklistInteractor
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIcons
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModel
import com.android.systemui.statusbar.ui.viewmodel.StatusBarRegionSamplingViewModel
import com.android.systemui.util.boundsOnScreen
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.awaitCancellation

/** Factory to simplify the dependency management for [StatusBarRoot] */
@PerDisplaySingleton
class StatusBarRootFactory
@Inject
constructor(
    private val notificationIconsBinder: NotificationIconContainerStatusBarViewBinder,
    private val iconViewStoreFactory: ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
    private val clockViewModelFactory: ClockViewModel.Factory,
    private val darkIconManagerFactory: DarkIconManager.Factory,
    private val tintedIconManagerFactory: TintedIconManager.Factory,
    private val headlineComposer: Headline,
    private val iconController: StatusBarIconController,
    @DisplayAware private val eventAnimationInteractor: SystemStatusEventAnimationInteractor,
    @DisplayAware private val darkIconDispatcher: DarkIconDispatcher,
    @DisplayAware private val homeStatusBarViewBinder: HomeStatusBarViewBinder,
    @DisplayAware private val homeStatusBarViewModelFactory: HomeStatusBarViewModelFactory,
    @DisplayAware private val headlineViewModelFactory: HeadlineViewModel.Factory,
    private val statusBarRegionSamplingViewModelFactory: StatusBarRegionSamplingViewModel.Factory,
    private val shadeWindowRootView: WindowRootView,
    private val axDynamicBarChipViewModel: AxDynamicBarChipViewModel,
) {
    fun create(root: ViewGroup, andThen: (ViewGroup) -> Unit): ComposeView {
        val composeView = ComposeView(root.context)
        composeView.apply {
            setContent {
                PlatformTheme {
                    StatusBarRoot(
                        parent = root,
                        shadeWindowRootView = shadeWindowRootView,
                        statusBarViewModelFactory = homeStatusBarViewModelFactory,
                        statusBarViewBinder = homeStatusBarViewBinder,
                        notificationIconsBinder = notificationIconsBinder,
                        iconViewStoreFactory = iconViewStoreFactory,
                        clockViewModelFactory = clockViewModelFactory,
                        darkIconManagerFactory = darkIconManagerFactory,
                        tintedIconManagerFactory = tintedIconManagerFactory,
                        headlineViewModelFactory = headlineViewModelFactory,
                        headlineComposer = headlineComposer,
                        iconController = iconController,
                        darkIconDispatcher = darkIconDispatcher,
                        eventAnimationInteractor = eventAnimationInteractor,
                        statusBarRegionSamplingViewModelFactory =
                            statusBarRegionSamplingViewModelFactory,
                        axDynamicBarChipViewModel = axDynamicBarChipViewModel,
                        onViewCreated = andThen,
                        modifier = Modifier.sysUiResTagContainer(),
                    )
                }
            }
        }

        return composeView
    }
}

/**
 * For now, this class exists only to replace the former CollapsedStatusBarFragment. We simply stand
 * up the PhoneStatusBarView here (allowing the component to be initialized from the [init] block).
 * This is the place, for now, where we can manually set up lingering dependencies that came from
 * the fragment until we can move them to recommended-arch style repos.
 *
 * @param onViewCreated called immediately after the view is inflated, and takes as a parameter the
 *   newly-inflated PhoneStatusBarView. This lambda is useful for tying together old initialization
 *   logic until it can be replaced.
 */
@Composable
fun StatusBarRoot(
    parent: ViewGroup,
    shadeWindowRootView: WindowRootView,
    statusBarViewModelFactory: HomeStatusBarViewModelFactory,
    statusBarViewBinder: HomeStatusBarViewBinder,
    notificationIconsBinder: NotificationIconContainerStatusBarViewBinder,
    iconViewStoreFactory: ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
    clockViewModelFactory: ClockViewModel.Factory,
    darkIconManagerFactory: DarkIconManager.Factory,
    tintedIconManagerFactory: TintedIconManager.Factory,
    headlineViewModelFactory: HeadlineViewModel.Factory,
    headlineComposer: Headline,
    iconController: StatusBarIconController,
    darkIconDispatcher: DarkIconDispatcher,
    eventAnimationInteractor: SystemStatusEventAnimationInteractor,
    statusBarRegionSamplingViewModelFactory: StatusBarRegionSamplingViewModel.Factory,
    axDynamicBarChipViewModel: AxDynamicBarChipViewModel,
    onViewCreated: (ViewGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayId = parent.context.displayId
    val statusBarViewModel =
        rememberViewModel("HomeStatusBar") { statusBarViewModelFactory.create() }
    val iconViewStore: NotificationIconContainerViewBinder.IconViewStore =
        rememberViewModel("HomeStatusBar.IconViewStore[$displayId]") {
            iconViewStoreFactory.create(displayId)
        }
    val appHandlesViewModel =
        rememberViewModel("AppHandleBounds") {
            statusBarViewModel.appHandlesViewModelFactory.create(displayId)
        }
    val headlineViewModel =
        if (StatusBarHeadline.isEnabled) {
            rememberViewModel("HeadlineViewModel") { headlineViewModelFactory.create() }
        } else {
            null
        }
    var touchableExclusionRegionDisposableHandle: DisposableHandle? = null

    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Let the DesktopStatusBar compose all the UI if [useDesktopStatusBar] is true.
    if (StatusBarForDesktop.isEnabled && statusBarViewModel.useDesktopStatusBar) {
        DesktopStatusBar(
            viewModel = statusBarViewModel,
            clockViewModelFactory = clockViewModelFactory,
            statusBarIconController = iconController,
            iconManagerFactory = tintedIconManagerFactory,
            iconViewStore = iconViewStore,
            modifier =
                modifier.forwardDragAndSwipeToShadeRootView(shadeWindowRootView, touchSlop) {
                    position,
                    size,
                    isConsumed ->
                    // This call is needed to make sure the shade is preemptively moved to the
                    // display that the user is currently interacting with.
                    statusBarViewModel.onShadeExpansionIntent(position.x, size.width, isConsumed)
                },
        )
        return
    }

    Box {
        AndroidView(
            factory = { context ->
                val inflater = LayoutInflater.from(context)
                val phoneStatusBarView =
                    inflater.inflate(R.layout.status_bar, parent, false) as PhoneStatusBarView

                addStartSideComposable(
                    phoneStatusBarView = phoneStatusBarView,
                    clockViewModelFactory = clockViewModelFactory,
                    statusBarViewModel = statusBarViewModel,
                    iconViewStore = iconViewStore,
                    appHandlesViewModel = appHandlesViewModel,
                    axDynamicBarChipViewModel = axDynamicBarChipViewModel,
                    context = context,
                )

                touchableExclusionRegionDisposableHandle =
                    HomeStatusBarTouchExclusionRegionBinder.bind(
                        phoneStatusBarView,
                        appHandlesViewModel,
                    )

                // For notifications, first inflate the [NotificationIconContainer]
                val notificationIconArea =
                    phoneStatusBarView.requireViewById<ViewGroup>(R.id.notification_icon_area)
                inflater.inflate(R.layout.notification_icon_area, notificationIconArea, true)
                // Then bind it using the icons binder
                val notificationIconContainer =
                    phoneStatusBarView.requireViewById<NotificationIconContainer>(
                        R.id.notificationIcons
                    )

                // If the flag is enabled, create and add a compose section to the end
                // of the system_icons container
                if (SystemStatusIconsInCompose.isEnabled) {
                    phoneStatusBarView.requireViewById<View>(R.id.system_icons).visibility =
                        View.GONE
                    addEndSideComposable(phoneStatusBarView, statusBarViewModel)
                } else {
                    val statusIconContainer =
                        phoneStatusBarView.requireViewById<StatusIconContainer>(R.id.statusIcons)
                    val darkIconManager =
                        darkIconManagerFactory.create(
                            statusIconContainer,
                            StatusBarLocation.HOME,
                            darkIconDispatcher,
                        )

                    HomeStatusBarIconBlockListBinder.bind(
                        statusIconContainer,
                        darkIconManager,
                        statusBarViewModel.iconBlockList,
                        iconController,
                    )

                    if (NewStatusBarIcons.isEnabled) {
                        addBatteryComposable(phoneStatusBarView, statusBarViewModel)
                        // Also adjust the paddings :)
                        SystemStatusIconsLayoutHelper.configurePaddingForNewStatusBarIcons(
                            phoneStatusBarView.requireViewById(R.id.statusIcons)
                        )
                    }
                }

                notificationIconsBinder.bindWhileAttached(
                    notificationIconContainer,
                    context.displayId,
                )

                if (StatusBarAlwaysUseRegionSampling.isAnyRegionSamplingEnabled) {
                    bindRegionSamplingViewModel(
                        context.displayId,
                        phoneStatusBarView,
                        statusBarRegionSamplingViewModelFactory,
                    )
                }

                // This binder handles everything else
                statusBarViewBinder.bind(
                    context.displayId,
                    phoneStatusBarView,
                    statusBarViewModel,
                    eventAnimationInteractor::animateStatusBarContentForChipEnter,
                    eventAnimationInteractor::animateStatusBarContentForChipExit,
                    listener = null,
                )
                onViewCreated(phoneStatusBarView)
                phoneStatusBarView
            },
            modifier =
                modifier
                    .thenIf(StatusBarEventForwardingModernization.isEnabled) {
                        Modifier.pointerInput(Unit) {
                                if (ViewFlags.scrollToTop()) {
                                    detectTapGesturesStrict(
                                        onTap = { statusBarViewModel.onStatusBarTap(it.x) },
                                        onLongPress = {
                                            statusBarViewModel.onStatusBarLongPressed()
                                        },
                                    )
                                } else {
                                    detectLongPressGesture {
                                        statusBarViewModel.onStatusBarLongPressed()
                                    }
                                }
                            }
                            .forwardDragAndSwipeToShadeRootView(shadeWindowRootView, touchSlop) {
                                position,
                                size,
                                isConsumed ->
                                // This call is needed to make sure the shade is preemptively moved
                                // to
                                // the display that the user is currently interacting with.
                                statusBarViewModel.onShadeExpansionIntent(
                                    position.x,
                                    size.width,
                                    isConsumed,
                                )
                            }
                    }
                    .thenIf(headlineViewModel != null) {
                        Modifier.drawWithHeadlineScrim(headlineViewModel!!)
                    },
            onRelease = { touchableExclusionRegionDisposableHandle?.dispose() },
        )

        if (StatusBarHeadline.isEnabled && headlineViewModel != null) {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            parent.initOnBackPressedDispatcherOwner(lifecycle, force = true)
            headlineComposer.Content(headlineViewModel, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Stacks the way-back label under [clockView]. Called for every crDroid clock (left, center and
 * right) so the label follows the active clock position (STATUS_BAR_CLOCK in
 * HomeStatusBarViewBinder). Only the label under the visible clock is shown: the binder toggles
 * each clock's visibility (position, clock auto-hide, denylist, HUN), and the label mirrors its
 * own clock. The start padding is snapshot state updated on layout, so it follows the clock-chip
 * padding applied later by HomeStatusBarViewBinder.applyClockChip. For the right clock the
 * stacked column is moved out of the fixed-height system_icons row only while the feature is
 * enabled (see [moveRightClockColumnOutOfSystemIcons]), otherwise the label would be clipped
 * there; with the feature off it stays inside system_icons, preserving the original layout.
 */
private fun stackBackToAppUnderClock(
    clockView: Clock,
    statusBarViewModel: HomeStatusBarViewModel,
    context: Context,
    childGravity: Int = android.view.Gravity.START,
) {
    val parent = clockView.parent as? ViewGroup ?: return
    val index = parent.indexOfChild(clockView)
    val clockParams = clockView.layoutParams
    val density = context.resources.displayMetrics.density
    val labelStart = mutableStateOf(clockView.paddingStart / density)
    val column =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL or childGravity
            clipChildren = false
            clipToPadding = false
            layoutParams = columnLayoutParams(clockParams)
        }
    parent.removeViewAt(index)
    parent.addView(column, index)
    clockView.minHeight = 0
    clockView.minimumHeight = 0
    clockView.includeFontPadding = false
    column.addView(
        clockView,
        LinearLayout.LayoutParams(clockParams.width, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply {
                if (clockParams is LinearLayout.LayoutParams) gravity = clockParams.gravity
            },
    )
    val labelView =
        ComposeView(context).apply {
            setContent {
                WithAdaptiveTint(
                    isDarkProvider = { bounds -> statusBarViewModel.areaDark.isDarkTheme(bounds) }
                ) { tint ->
                    BackToAppLabel(tint = tint, startPadding = labelStart.value.dp)
                }
            }
        }
    column.addView(
        labelView,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )
    labelView.visibility = clockView.visibility
    labelView.alpha = clockView.alpha
    val syncWithClock = {
        labelStart.value = clockView.paddingStart / density
        // Defer the visibility change out of the layout pass: setting it here
        // would call requestLayout() while a layout is already in progress.
        val clockVisibility = clockView.visibility
        labelView.post {
            if (labelView.visibility != clockVisibility) {
                labelView.visibility = clockVisibility
            }
        }
    }
    clockView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncWithClock() }
    column.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncWithClock() }
    // The binder fades the clock via View.animate() (alpha to 0, then GONE in
    // the end action). Mirror the alpha every frame so the label fades with
    // the clock instead of staying opaque and then vanishing at once.
    // Register on attach (not on the pre-attach floating observer) and remove
    // on detach, so the mirroring survives the status bar window being
    // recreated and re-attached.
    val preDrawListener =
        ViewTreeObserver.OnPreDrawListener {
            if (labelView.alpha != clockView.alpha) labelView.alpha = clockView.alpha
            true
        }
    var preDrawRegistered = false
    val attachListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!preDrawRegistered) {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                    preDrawRegistered = true
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                if (preDrawRegistered && v.viewTreeObserver.isAlive) {
                    v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                }
                preDrawRegistered = false
            }
        }
    clockView.addOnAttachStateChangeListener(attachListener)
    if (clockView.isAttachedToWindow) attachListener.onViewAttachedToWindow(clockView)
}

/** Column params that keep the clock's original layout_gravity and margins. */
private fun columnLayoutParams(clockParams: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
    val width = ViewGroup.LayoutParams.WRAP_CONTENT
    val height = ViewGroup.LayoutParams.MATCH_PARENT
    return when (clockParams) {
        is LinearLayout.LayoutParams ->
            LinearLayout.LayoutParams(width, height).apply {
                gravity = clockParams.gravity
                setMargins(
                    clockParams.leftMargin,
                    clockParams.topMargin,
                    clockParams.rightMargin,
                    clockParams.bottomMargin,
                )
                marginStart = clockParams.marginStart
                marginEnd = clockParams.marginEnd
            }
        is FrameLayout.LayoutParams ->
            FrameLayout.LayoutParams(width, height).apply {
                gravity = clockParams.gravity
                setMargins(
                    clockParams.leftMargin,
                    clockParams.topMargin,
                    clockParams.rightMargin,
                    clockParams.bottomMargin,
                )
                marginStart = clockParams.marginStart
                marginEnd = clockParams.marginEnd
            }
        is ViewGroup.MarginLayoutParams ->
            ViewGroup.MarginLayoutParams(width, height).apply {
                setMargins(
                    clockParams.leftMargin,
                    clockParams.topMargin,
                    clockParams.rightMargin,
                    clockParams.bottomMargin,
                )
                marginStart = clockParams.marginStart
                marginEnd = clockParams.marginEnd
            }
        else -> ViewGroup.LayoutParams(width, height)
    }
}

/**
 * Moves the stacked right-clock column out of system_icons.
 *
 * system_icons is included at a fixed height of status_bar_icon_container_height
 * (22sp) and clips its children, so a 14sp clock plus the ~12dp label cannot
 * fit inside it: the label was clipped and the clock pushed off centre.
 * status_bar_end_side_content is match_parent high and already has
 * clipChildren=false, so the column gets the full status-bar height there,
 * exactly like the left/center columns. It is inserted just after
 * system_icons, preserving the clock's position at the end edge (after the
 * status icons and battery), and its MATCH_PARENT height keeps the
 * clock+label group centred the same way as without the label.
 */
private fun moveRightClockColumnOutOfSystemIcons(
    phoneStatusBarView: PhoneStatusBarView,
    rightClock: Clock,
) {
    val column = rightClock.parent as? ViewGroup ?: return
    val oldParent = column.parent as? ViewGroup ?: return
    val systemIcons = phoneStatusBarView.findViewById<ViewGroup>(R.id.system_icons) ?: return
    val endSideContent =
        phoneStatusBarView.findViewById<ViewGroup>(R.id.status_bar_end_side_content) ?: return
    if (oldParent === endSideContent) return
    oldParent.removeView(column)
    column.clipChildren = false
    column.clipToPadding = false
    endSideContent.clipChildren = false
    endSideContent.clipToPadding = false
    val index = endSideContent.indexOfChild(systemIcons) + 1
    endSideContent.addView(column, index.coerceIn(0, endSideContent.childCount))
    // Wrap the clock+label height so the column's bounds describe its content
    // (region sampling excludes everything above the column's bottom edge).
    column.layoutParams =
        column.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    // Transfer the system_icons end padding onto the column: it originally sat
    // after the clock, and must stay after it rather than between the battery
    // and the relocated clock. PhoneStatusBarView.updatePaddings keeps this in
    // sync on configuration changes.
    val padEnd =
        phoneStatusBarView.resources.getDimensionPixelSize(R.dimen.status_bar_icons_padding_end)
    systemIcons.setPaddingRelative(
        systemIcons.paddingStart,
        systemIcons.paddingTop,
        0,
        systemIcons.paddingBottom,
    )
    column.setPaddingRelative(
        column.paddingStart,
        column.paddingTop,
        padEnd,
        column.paddingBottom,
    )
}

/** Undoes [moveRightClockColumnOutOfSystemIcons], restoring the original layout. */
private fun moveRightClockColumnBackIntoSystemIcons(
    phoneStatusBarView: PhoneStatusBarView,
    rightClock: Clock,
) {
    val column = rightClock.parent as? ViewGroup ?: return
    val oldParent = column.parent as? ViewGroup ?: return
    val systemIcons = phoneStatusBarView.findViewById<ViewGroup>(R.id.system_icons) ?: return
    val rightClockLayout =
        phoneStatusBarView.findViewById<ViewGroup>(R.id.right_clock_layout) ?: return
    if (oldParent === rightClockLayout) return
    oldParent.removeView(column)
    column.setPaddingRelative(column.paddingStart, column.paddingTop, 0, column.paddingBottom)
    val padEnd =
        phoneStatusBarView.resources.getDimensionPixelSize(R.dimen.status_bar_icons_padding_end)
    systemIcons.setPaddingRelative(
        systemIcons.paddingStart,
        systemIcons.paddingTop,
        padEnd,
        systemIcons.paddingBottom,
    )
    rightClockLayout.addView(column)
    column.layoutParams =
        column.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
}

private fun isBackToAppEnabled(context: Context): Boolean =
    Settings.System.getIntForUser(
        context.contentResolver,
        Settings.System.BACK_TO_APP_INDICATOR,
        0,
        UserHandle.USER_CURRENT,
    ) != 0

private fun applyRightClockRelocation(
    phoneStatusBarView: PhoneStatusBarView,
    rightClock: Clock,
    context: Context,
) {
    if (isBackToAppEnabled(context)) {
        moveRightClockColumnOutOfSystemIcons(phoneStatusBarView, rightClock)
    } else {
        moveRightClockColumnBackIntoSystemIcons(phoneStatusBarView, rightClock)
    }
}

/**
 * Relocates the right-clock column only while the way-back feature is enabled, and restores the
 * original (byte-for-byte crDroid) layout when it is turned off. The tracker observes the same
 * setting for the label itself; this observer only moves the view hierarchy.
 */
private fun observeBackToAppEnabledForRightClock(
    phoneStatusBarView: PhoneStatusBarView,
    rightClock: Clock,
    context: Context,
) {
    applyRightClockRelocation(phoneStatusBarView, rightClock, context)
    val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                applyRightClockRelocation(phoneStatusBarView, rightClock, context)
            }
        }
    phoneStatusBarView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                context.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.BACK_TO_APP_INDICATOR),
                    false,
                    observer,
                    UserHandle.USER_ALL,
                )
                applyRightClockRelocation(phoneStatusBarView, rightClock, context)
            }

            override fun onViewDetachedFromWindow(v: View) {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }
    )
}

private fun addStartSideComposable(
    phoneStatusBarView: PhoneStatusBarView,
    clockViewModelFactory: ClockViewModel.Factory,
    statusBarViewModel: HomeStatusBarViewModel,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    appHandlesViewModel: AppHandlesViewModel,
    axDynamicBarChipViewModel: AxDynamicBarChipViewModel,
    context: Context,
) {
    val startSideExceptHeadsUp =
        phoneStatusBarView.requireViewById<LinearLayout>(R.id.status_bar_start_side_except_heads_up)
    val startSideContainerView =
        phoneStatusBarView.requireViewById<View>(R.id.status_bar_start_side_container)
    val clockView = phoneStatusBarView.requireViewById<Clock>(R.id.clock)
    stackBackToAppUnderClock(clockView, statusBarViewModel, context)
    // crDroid can move the clock to the center or right view instead of R.id.clock;
    // stack a label under those too so it follows the active position.
    val centerClock: Clock? = phoneStatusBarView.findViewById(R.id.clock_center)
    centerClock?.let {
        stackBackToAppUnderClock(
            it,
            statusBarViewModel,
            context,
            childGravity = android.view.Gravity.CENTER_HORIZONTAL,
        )
    }
    val rightClock: Clock? = phoneStatusBarView.findViewById(R.id.clock_right)
    rightClock?.let {
        stackBackToAppUnderClock(
            it,
            statusBarViewModel,
            context,
            childGravity = android.view.Gravity.END,
        )
        // Only relocate while the feature is on; with it off the column stays
        // inside system_icons, preserving crDroid's original layout, hover /
        // touch listeners, region sampling and end padding.
        observeBackToAppEnabledForRightClock(phoneStatusBarView, it, context)
    }

    val composeView =
        ComposeView(context).apply {
            val showDate = Flags.statusBarDate() && statusBarViewModel.useDesktopStatusBar

            layoutParams =
                LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    )
                    .apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

            setContent {
                val statusBarBoundsViewModel =
                    rememberViewModel("HomeStatusBar.Bounds") {
                        statusBarViewModel.statusBarBoundsViewModelFactory.create(
                            startSideContainerView = startSideContainerView,
                            clockView = clockView,
                        )
                    }

                var clockViewModel: ClockViewModel? = null
                if (showDate || ClockModernization.isEnabled) {
                    clockViewModel =
                        rememberViewModel("HomeStatusBar.Clock") {
                            clockViewModelFactory.create(AmPmStyle.Gone)
                        }
                }

                if (ClockModernization.isEnabled) {
                    clockView.visibility = View.GONE
                    WithAdaptiveTint(
                        isDarkProvider = { bounds ->
                            statusBarViewModel.areaDark.isDarkTheme(bounds)
                        }
                    ) { tint ->
                        // The legacy clocks are GONE in this mode (their stacked labels
                        // hide with them), so put the label under the Compose clock.
                        Column {
                            Clock(
                                clockViewModel = checkNotNull(clockViewModel),
                                textColor = tint,
                                modifier =
                                    Modifier.padding(end = 2.dp)
                                        .wrapContentSize()
                                        .onGloballyPositioned { coordinates ->
                                            val boundsInWindow = coordinates.boundsInWindow()
                                            val bounds =
                                                Rect(
                                                    boundsInWindow.left.toInt(),
                                                    boundsInWindow.top.toInt(),
                                                    boundsInWindow.right.toInt(),
                                                    boundsInWindow.bottom.toInt(),
                                                )
                                            statusBarBoundsViewModel.updateComposeClockBounds(bounds)
                                        },
                            )
                            BackToAppLabel(tint = tint, startPadding = 0.dp)
                        }
                    }
                }

                if (showDate) {
                    VariableDayDate(
                        longerDateText = checkNotNull(clockViewModel).longerDateText,
                        shorterDateText = clockViewModel.shorterDateText,
                        textColor = colorAttr(R.attr.wallpaperTextColor),
                        modifier =
                            Modifier.padding(horizontal = 4.dp)
                                .wrapContentSize()
                                .onGloballyPositioned { coordinates ->
                                    val boundsInWindow = coordinates.boundsInWindow()
                                    val bounds =
                                        Rect(
                                            boundsInWindow.left.toInt(),
                                            boundsInWindow.top.toInt(),
                                            boundsInWindow.right.toInt(),
                                            boundsInWindow.bottom.toInt(),
                                        )
                                    statusBarBoundsViewModel.updateDateBounds(bounds)
                                },
                    )
                }

                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val density = context.resources.displayMetrics.density

                val chipsMaxWidth: Dp =
                    remember(
                        appHandlesViewModel.appHandleBounds,
                        statusBarBoundsViewModel.startSideContainerBounds,
                        statusBarBoundsViewModel.dateBounds,
                        statusBarBoundsViewModel.clockBounds,
                        isRtl,
                        density,
                    ) {
                        chipsMaxWidth(
                            appHandles = appHandlesViewModel.appHandleBounds,
                            startSideContainerBounds =
                                statusBarBoundsViewModel.startSideContainerBounds,
                            dateBounds = statusBarBoundsViewModel.dateBounds,
                            clockBounds = statusBarBoundsViewModel.clockBounds,
                            isRtl = isRtl,
                            density = density,
                        )
                    }

                val axEnabled by axDynamicBarChipViewModel.interactor.settings.isEnabled.collectAsState()
                if (axEnabled) {
                    AxDynamicBarChip(
                        viewModel = axDynamicBarChipViewModel,
                        modifier = Modifier.widthIn(max = chipsMaxWidth),
                    )
                }
                val chipsVisibilityModel = statusBarViewModel.ongoingActivityChips
                if (chipsVisibilityModel.areChipsAllowed) {
                    OngoingActivityChips(
                        chips = chipsVisibilityModel.chips,
                        iconViewStore = iconViewStore,
                        onChipBoundsChanged = statusBarViewModel::onChipBoundsChanged,
                        modifier = Modifier.sysUiResTagContainer().widthIn(max = chipsMaxWidth),
                    )
                }
            }
        }

    if (!Flags.statusBarChipVisibilityJankFix()) {
        // Add the composable container for ongoingActivityChips before the
        // notification_icon_area to maintain the same ordering for ongoing activity
        // chips in the status bar layout.
        val notificationIconAreaIndex =
            startSideExceptHeadsUp.indexOfChild(
                startSideExceptHeadsUp.findViewById(R.id.start_side_notif_and_chip_container)
            )
        startSideExceptHeadsUp.addView(composeView, notificationIconAreaIndex)
    } else {
        // notif_and_chip_container is a FrameLayout so the chips and notif icon container views
        // don't interfere with each other's layout bounds. They are mutually exclusive per the
        // view model
        val startSideNotifAndChipsView =
            phoneStatusBarView.requireViewById<FrameLayout>(
                R.id.start_side_notif_and_chip_container
            )
        startSideNotifAndChipsView.addView(composeView, 0)
    }
}

@VisibleForTesting
fun chipsMaxWidth(
    appHandles: List<Rect>,
    startSideContainerBounds: Rect,
    dateBounds: Rect,
    clockBounds: Rect,
    isRtl: Boolean,
    density: Float,
): Dp {
    val relevantAppHandles =
        appHandles
            .filterNot { it.isEmpty }
            // Only care about app handles in the same possible region as the chips
            .filter { Rect.intersects(it, startSideContainerBounds) }

    // The chips should be next to the date if it is showing, otherwise they should be next to the
    // clock.
    val clockOrDateBounds = if (dateBounds.isEmpty) clockBounds else dateBounds

    val widthInPx =
        if (isRtl) {
                val chipsLeftBasedOnAppHandles =
                    relevantAppHandles.maxOfOrNull { it.right } ?: Int.MIN_VALUE
                val chipsLeftBasedOnContainer = startSideContainerBounds.left
                val chipsLeft = maxOf(chipsLeftBasedOnAppHandles, chipsLeftBasedOnContainer)
                /* width= */ clockOrDateBounds.left - chipsLeft
            } else { // LTR
                val chipsRightBasedOnAppHandles =
                    relevantAppHandles.minOfOrNull { it.left } ?: Int.MAX_VALUE
                val chipsRightBasedOnContainer = startSideContainerBounds.right
                val chipsRight = minOf(chipsRightBasedOnAppHandles, chipsRightBasedOnContainer)
                /* width= */ chipsRight - clockOrDateBounds.right
            }
            .coerceAtLeast(0)

    return (widthInPx / density).dp
}

/** Create a new [UnifiedBattery] and add it to the end of the system_icons container */
private fun addBatteryComposable(
    phoneStatusBarView: PhoneStatusBarView,
    statusBarViewModel: HomeStatusBarViewModel,
) {
    val batteryComposeView =
        ComposeView(phoneStatusBarView.context).apply {
            setContent {
                val viewModel =
                    rememberViewModel(traceName = "UnifiedBattery") {
                        statusBarViewModel.unifiedBatteryViewModel.create()
                    }
                BatteryWithPercent(
                    modifier = Modifier.sysUiResTagContainer().wrapContentSize(),
                    viewModel = viewModel,
                    isDarkProvider = { statusBarViewModel.areaDark },
                    showPercent = viewModel.isBatteryPercentSettingEnabled,
                )
            }
            setId(R.id.battery_composable_view)
        }
    phoneStatusBarView.findViewById<ViewGroup>(R.id.system_icons).apply {
        addView(batteryComposeView, getChildCount() - 1)
    }
}

/**
 * Create a composable that will replace the status_bar_end_side_content. This is added to the end
 * of the status_bar_end_side_container
 */
private fun addEndSideComposable(
    phoneStatusBarView: PhoneStatusBarView,
    statusBarViewModel: HomeStatusBarViewModel,
) {
    val endSideContainerView =
        phoneStatusBarView.requireViewById<View>(R.id.status_bar_end_side_container)
    val systemStatusIconsComposeView =
        ComposeView(phoneStatusBarView.context).apply {
            setContent {
                val endSideWidth by rememberViewWidthAsState(endSideContainerView)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier.widthIn(max = with(LocalDensity.current) { endSideWidth.toDp() })
                            .sysUiResTagContainer(),
                ) {
                    SystemStatusIconsContainer(
                        viewModelFactory = statusBarViewModel.systemStatusIconsViewModelFactory,
                        isDark = statusBarViewModel.areaDark,
                        modifier = Modifier.weight(1f, fill = false).sysuiResTag("system_icons"),
                        systemStatusIconBlockListInteractor =
                            statusBarViewModel.systemStatusIconBlockListInteractor,
                    )

                    val viewModel =
                        rememberViewModel(traceName = "UnifiedBattery") {
                            statusBarViewModel.unifiedBatteryViewModel.create()
                        }
                    BatteryWithPercent(
                        viewModel = viewModel,
                        isDarkProvider = { statusBarViewModel.areaDark },
                        modifier = Modifier.wrapContentSize(),
                        showPercent = viewModel.isBatteryPercentSettingEnabled,
                    )
                }
            }
        }

    phoneStatusBarView.findViewById<ViewGroup>(R.id.status_bar_end_side_content).apply {
        addView(systemStatusIconsComposeView)
    }
}

@Composable
private fun SystemStatusIconsContainer(
    viewModelFactory: SystemStatusIconsViewModel.Factory,
    isDark: IsAreaDark,
    modifier: Modifier = Modifier,
    systemStatusIconBlockListInteractor: SystemStatusIconBlocklistInteractor,
) {
    var bounds by remember { mutableStateOf(Rect()) }
    val tint = if (isDark.isDarkTheme(bounds)) Color.White else Color.Black
    SystemStatusIcons(
        viewModelFactory = viewModelFactory,
        tint = tint,
        modifier =
            modifier.onLayoutRectChanged { relativeLayoutBounds ->
                bounds =
                    with(relativeLayoutBounds.boundsInScreen) { Rect(left, top, right, bottom) }
            },
        systemStatusIconBlocklistInteractor = systemStatusIconBlockListInteractor,
    )
}

private fun bindRegionSamplingViewModel(
    displayId: Int,
    phoneStatusBarView: PhoneStatusBarView,
    statusBarRegionSamplingViewModelFactory: StatusBarRegionSamplingViewModel.Factory,
) {
    phoneStatusBarView.repeatWhenAttached {
        phoneStatusBarView.viewModel(
            traceName = "StatusBarRegionSamplingViewModel",
            minWindowLifecycleState = WindowLifecycleState.ATTACHED,
            factory = {
                statusBarRegionSamplingViewModelFactory.create(
                    displayId = displayId,
                    attachStateView = phoneStatusBarView,
                    startSideContainerView =
                        phoneStatusBarView.requireViewById(R.id.status_bar_start_side_container),
                    startSideIconView = phoneStatusBarView.requireViewById(R.id.clock),
                    endSideContainerView =
                        phoneStatusBarView.requireViewById(R.id.status_bar_end_side_container),
                    endSideIconView = phoneStatusBarView.requireViewById(R.id.system_icons),
                    // The stacked right-clock column: while relocated it sits
                    // outside system_icons, so sampling must also exclude the
                    // area above its bottom edge (see ViewModel).
                    endSideClockColumnView =
                        (phoneStatusBarView.findViewById<Clock>(R.id.clock_right)?.parent
                            as? View)
                            ?: phoneStatusBarView.requireViewById(R.id.system_icons),
                )
            },
        ) {
            awaitCancellation()
        }
    }
}

/**
 * Tracks the width of a given [view] in pixels and returns it as a [MutableIntState]. The state is
 * updated whenever the view's layout changes.
 */
@Composable
private fun rememberViewWidthAsState(view: View): MutableIntState {
    val viewWidth = remember(view) { mutableIntStateOf(view.boundsOnScreen.width()) }

    DisposableEffect(view) {
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                viewWidth.intValue = view.boundsOnScreen.width()
            }
        view.addOnLayoutChangeListener(layoutListener)

        onDispose { view.removeOnLayoutChangeListener(layoutListener) }
    }
    return viewWidth
}

/**
 * A pointer input modifier that intercepts events and forwards them to a provided view if there is
 * a drag or swipe.
 *
 * It works by:
 * 1. Observing pointer events in the 'Initial' pass, before they reach child composables.
 * 2. Caching events (ACTION_DOWN, ACTION_MOVE) until the vertical drag distance exceeds the system
 *    touch slop.
 * 3. Once the slop is exceeded, it begins "intercepting". It dispatches the entire cached gesture
 *    (starting from ACTION_DOWN) to the provided [view].
 * 4. For the remainder of the gesture, it continues dispatching events directly to the [view] and
 *    consumes them, preventing any child composables from receiving them.
 */
@VisibleForTesting
fun Modifier.forwardDragAndSwipeToShadeRootView(
    view: View,
    touchSlop: Float,
    onDown: (downPosition: Offset, size: IntSize, isConsumed: Boolean) -> Unit,
): Modifier =
    pointerInput(view) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            val isConsumed = down.isConsumed

            val cachedEvents = mutableListOf<MotionEvent>()
            var isIntercepting = false

            // Always cache the down event.
            currentEvent.motionEvent?.let { cachedEvents.add(MotionEvent.obtain(it)) }

            onDown(down.position, size, isConsumed)
            try {
                // Loop to process events for the current gesture.
                do {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val mainChange = event.changes.first()

                    if (isIntercepting) {
                        // If we are already intercepting, dispatch and consume.
                        dispatchAndConsume(event, view)
                    } else {
                        // If not intercepting, check if we should start.
                        val dy = mainChange.position.y - down.position.y
                        if (abs(dy) > touchSlop) {
                            isIntercepting = true

                            // Slop exceeded. Dispatch the cached events...
                            cachedEvents.forEach { view.dispatchTouchEvent(it) }
                            // ...and the current event, then consume it.
                            dispatchAndConsume(event, view)
                        } else {
                            // Slop not exceeded, just cache the event.
                            event.motionEvent?.let { cachedEvents.add(MotionEvent.obtain(it)) }
                        }
                    }

                    // Continue tracking until ALL pointers involved in the gesture are lifted.
                    // This ensures downstream nodes receive their terminal events during
                    // multi-touch gestures.
                } while (event.changes.any { it.pressed })
            } finally {
                // Ensure all cached events are recycled at the end of the gesture.
                cachedEvents.forEach { it.recycle() }
            }
        }
    }

/** Helper to dispatch a copy of the MotionEvent and consume all PointerChanges. */
private fun dispatchAndConsume(event: PointerEvent, legacyView: View) {
    event.motionEvent?.let {
        val copy = MotionEvent.obtain(it)
        try {
            legacyView.dispatchTouchEvent(copy)
        } finally {
            copy.recycle()
        }
    }
    event.changes.forEach { it.consume() }
}
