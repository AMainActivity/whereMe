package ru.ama.whereme.ui

import android.app.Application
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ama.whereme.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PackagedLicensesTest {
    @Test
    fun everyPackagedLibraryHasAnOfflineLicenseText() {
        val text =
            RuntimeEnvironment.getApplication()
                .resources
                .openRawResource(R.raw.aboutlibraries)
                .bufferedReader()
                .use { it.readText() }
        val catalog = Json.parseToJsonElement(text).jsonObject
        val libraries = catalog.getValue("libraries").jsonArray
        val licenses = catalog.getValue("licenses").jsonObject
        assertTrue(libraries.isNotEmpty())
        libraries.forEach { item ->
            val library = item.jsonObject
            val id = library.getValue("uniqueId").jsonPrimitive.content
            val keys = library["licenses"]?.jsonArray.orEmpty()
            assertTrue("Missing license for $id", keys.isNotEmpty())
            keys.forEach { key ->
                val content =
                    licenses[key.jsonPrimitive.content]
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.content
                assertFalse("Missing offline text for $id", content.isNullOrBlank())
            }
            assertFalse("Build plugin in runtime catalog: $id", id.contains("gradle-plugin"))
            assertFalse(
                "Unit test dependency in runtime catalog: $id",
                id.contains("robolectric") || id.startsWith("junit:"),
            )
        }
    }
}
