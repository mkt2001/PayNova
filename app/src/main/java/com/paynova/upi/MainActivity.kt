package com.paynova.upi

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.Modifier
import com.paynova.upi.ui.AppRoot
import com.paynova.upi.ui.LockScreen
import com.paynova.upi.ui.LockState
import com.paynova.upi.ui.PayNovaTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Graph.settings.lockEnabled) LockState.locked = true
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_STOP -> LockState.lastBg = SystemClock.elapsedRealtime()
                    Lifecycle.Event.ON_START -> {
                        val s = Graph.settings
                        if (s.lockEnabled && SystemClock.elapsedRealtime() - LockState.lastBg >= s.autoLockSec * 1000L) {
                            LockState.locked = true
                        }
                    }
                    else -> {}
                }
            }
        })
        setContent {
            val s = Graph.settings
            PayNovaTheme(s.darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    if (s.lockEnabled && LockState.locked) LockScreen() else AppRoot()
                }
            }
        }
    }
}
