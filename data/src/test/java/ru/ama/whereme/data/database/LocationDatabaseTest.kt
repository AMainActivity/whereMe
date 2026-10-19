package ru.ama.whereme.data.database

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocationDatabaseTest : LocationDatabaseContract() {
    override val context: Context get() = RuntimeEnvironment.getApplication()
}
