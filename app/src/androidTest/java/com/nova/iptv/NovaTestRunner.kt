package com.nova.iptv

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner. Hilt tests can swap this for HiltTestRunner later.
 */
class NovaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, className, context)
    }
}
