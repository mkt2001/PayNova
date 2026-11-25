package com.paynova.upi

import android.app.Application
import androidx.room.Room
import com.paynova.upi.data.AppDb
import com.paynova.upi.data.SettingsStore
import com.paynova.upi.data.TxRepository
import com.paynova.upi.payment.BackendPaymentProvider
import com.paynova.upi.payment.DemoPaymentProvider
import com.paynova.upi.payment.PaymentProvider
import com.paynova.upi.payment.ProviderConfig
import com.paynova.upi.voice.Announcer

/** Tiny service locator (keeps the project dependency-injection-library free). */
object Graph {
    private lateinit var application: Application
    val app: Application get() = application
    fun init(a: Application) { application = a }

    val settings: SettingsStore by lazy { SettingsStore(application) }
    val db: AppDb by lazy { Room.databaseBuilder(application, AppDb::class.java, "paynova.db").build() }
    val announcer: Announcer by lazy { Announcer(application, settings) }
    val repo: TxRepository by lazy { TxRepository(db.txDao(), settings, announcer) }

    /** Demo and production are strictly separate: production needs the toggle AND a configured HTTPS backend. */
    val isDemo: Boolean get() = !(settings.prod && ProviderConfig.isConfigured)
    val provider: PaymentProvider get() = providerFor(isDemo)
    fun providerFor(demo: Boolean): PaymentProvider = if (demo) DemoPaymentProvider else BackendPaymentProvider
}
