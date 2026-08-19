package com.nova.iptv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun homeAndPlayer() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // Exercise Home rows, then open the first playable focused item.
        repeat(4) { device.pressDPadDown() }
        repeat(3) { device.pressDPadRight() }
        device.pressEnter()
        device.waitForIdle()
        device.pressBack()
    }
}

internal const val PACKAGE_NAME = "com.nova.iptv"
