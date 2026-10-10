package io.github.hilbert0619.coursekit

import kotlinx.serialization.Serializable

/** The timetable as the app hands it to its widgets, as JSON (the same format as on iOS). */
@Serializable
data class ScheduleSnapshot(
    val version: Int = CURRENT_VERSION,
    val timetable: Timetable,
) {
    fun encoded(): String = CourseKitJson.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1

        /** Throws [SnapshotException] for a snapshot written by a newer app, and kotlinx exceptions for bad JSON. */
        fun decode(json: String): ScheduleSnapshot {
            val snapshot = CourseKitJson.decodeFromString(serializer(), json)
            if (snapshot.version > CURRENT_VERSION) throw SnapshotException.UnsupportedVersion(snapshot.version)
            return snapshot
        }
    }
}

sealed class SnapshotException(message: String) : Exception(message) {
    data class UnsupportedVersion(val version: Int) : SnapshotException("Unsupported snapshot version $version")
}
