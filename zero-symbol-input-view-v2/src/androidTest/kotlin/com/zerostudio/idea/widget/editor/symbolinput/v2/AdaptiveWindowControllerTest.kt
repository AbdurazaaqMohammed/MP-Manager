/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.AdaptiveWindowController
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.FoldingFeatureInfo
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowInsetsMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowPosture
import com.zerostudio.idea.widget.editor.symbolinput.v2.testing.DrawerTestHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveWindowControllerTest {
    @Test
    fun controllerSharesSnapshotsAndReleasesListeners() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.content.addView(
                    host,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                host.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(700, android.view.View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, 900, 700)

                val controller = AdaptiveWindowController(
                    host,
                    WindowInsetsMode.VIEW_BOUNDS_ALREADY_EXCLUDE_INSETS
                )
                assertNotNull(controller.current)
                val snapshots = mutableListOf<Float>()
                val postures = mutableListOf<WindowPosture>()
                val callback: (com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowAdaptiveInfo) -> Unit =
                    {
                        snapshots += it.widthDp
                        postures += it.foldingFeature.posture
                    }
                val subscription = controller.addListener(callback)
                val secondSubscription = controller.addListener(callback)
                assertTrue(snapshots.isNotEmpty())

                controller.updateFoldingFeature(
                    FoldingFeatureInfo(
                        posture = WindowPosture.HALF_OPENED,
                        isSeparating = true,
                        boundsLeftDp = 250f,
                        boundsRightDp = 270f,
                        boundsBottomDp = 700f / activity.resources.displayMetrics.density
                    )
                )
                assertEquals(WindowPosture.HALF_OPENED, controller.current?.foldingFeature?.posture)
                assertEquals(WindowPosture.HALF_OPENED, postures.last())

                host.layout(0, 0, 520, 700)
                assertEquals(520f / activity.resources.displayMetrics.density, snapshots.last(), 0.5f)

                subscription.close()
                val countAfterFirstClose = snapshots.size
                host.layout(0, 0, 500, 700)
                assertTrue("Closing one handle must not remove the duplicate subscription", snapshots.size > countAfterFirstClose)

                secondSubscription.close()
                secondSubscription.close()
                val countAfterAllClosed = snapshots.size
                host.layout(0, 0, 480, 700)
                assertEquals(countAfterAllClosed, snapshots.size)

                controller.close()
                assertNull(controller.current)
            }
        }
    }


    @Test
    fun rotationStyleDimensionSwapPublishesNewWindowBounds() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.content.addView(
                    host,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                val density = activity.resources.displayMetrics.density
                host.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, 400, 800)

                val controller = AdaptiveWindowController(
                    host,
                    WindowInsetsMode.VIEW_BOUNDS_ALREADY_EXCLUDE_INSETS
                )
                val snapshots = mutableListOf<Pair<Float, Float>>()
                val subscription = controller.addListener({ snapshots.add(it.widthDp to it.heightDp) })

                // A portrait -> landscape bounds swap is the geometry change
                // delivered to the observer during a real configuration rotation.
                host.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, 800, 400)

                val current = requireNotNull(controller.current)
                assertEquals(800f / density, current.widthDp, 0.5f)
                assertEquals(400f / density, current.heightDp, 0.5f)
                assertTrue("Rotation must publish a new bounds snapshot", snapshots.size >= 2)
                assertEquals(800f / density, snapshots.last().first, 0.5f)
                assertEquals(400f / density, snapshots.last().second, 0.5f)

                subscription.close()
                controller.close()
            }
        }
    }

    @Test
    fun oneFailingListenerIsReportedWithoutCrashingOrStarvingOtherSubscribers() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.content.addView(
                    host,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                host.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(700, android.view.View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, 600, 700)

                val controller = AdaptiveWindowController(
                    host,
                    WindowInsetsMode.VIEW_BOUNDS_ALREADY_EXCLUDE_INSETS
                )
                var delivered = 0
                var reported: Exception? = null
                controller.onListenerError = { reported = it }
                controller.addListener(
                    { throw IllegalStateException("expected listener failure") },
                    emitCurrent = false
                )
                controller.addListener({ delivered++ }, emitCurrent = false)

                host.layout(0, 0, 520, 700)
                assertEquals("expected listener failure", controller.lastListenerFailure?.message)
                assertEquals("expected listener failure", reported?.message)
                assertEquals(1, delivered)
                controller.close()
            }
        }
    }

    @Test
    fun closeIsIdempotentAndRejectsFurtherOperations() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.content.addView(
                    host,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                host.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY)
                )
                host.layout(0, 0, 600, 800)

                val controller = AdaptiveWindowController(
                    host,
                    WindowInsetsMode.VIEW_BOUNDS_ALREADY_EXCLUDE_INSETS
                )
                controller.close()
                controller.close()
                assertNull(controller.current)
                assertTrue(runCatching { controller.addListener({}) }.isFailure)
                assertTrue(runCatching { controller.refresh() }.isFailure)
                assertTrue(
                    runCatching {
                        controller.updateFoldingFeature(
                            FoldingFeatureInfo(
                                posture = WindowPosture.FLAT,
                                isSeparating = false
                            )
                        )
                    }.isFailure
                )
            }
        }
    }

}
