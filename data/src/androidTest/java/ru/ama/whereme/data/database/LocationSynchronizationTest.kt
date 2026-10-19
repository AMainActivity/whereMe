package ru.ama.whereme.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationSynchronizationTest : LocationDatabaseContract() {
    override val context: Context get() = ApplicationProvider.getApplicationContext()
}
