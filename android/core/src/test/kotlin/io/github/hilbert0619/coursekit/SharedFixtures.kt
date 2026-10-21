package io.github.hilbert0619.coursekit

import java.io.File
import kotlinx.serialization.serializer

/** The shared test cases in `spec/fixtures/` (the test task sets `fixtures.dir`). */
object SharedFixtures {
    val directory = File(System.getProperty("fixtures.dir") ?: error("The test task sets fixtures.dir"))

    fun text(name: String): String = File(directory, name).readText()

    inline fun <reified T> decode(name: String): T = CourseKitJson.decodeFromString(serializer<T>(), text(name))
}
