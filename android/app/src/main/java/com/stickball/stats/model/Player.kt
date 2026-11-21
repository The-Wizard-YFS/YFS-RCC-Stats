package com.stickball.stats.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/** Firestore "players" document (same fields the iOS app reads and writes) */
data class Player(
    val id: String? = null,
    val name: String,
    val jerseyNumber: Int? = null,
    val teamName: String? = null,
    val isActive: Boolean = true,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
) {
    /** Portland players only play in the Coattail Classic and don't count toward LA stats */
    val isPortland: Boolean get() = teamName == PORTLAND_CHAPTER

    val displayName: String get() = if (jerseyNumber != null) "#$jerseyNumber $name" else name

    fun toFirestore(): Map<String, Any> = buildMap {
        put("name", name)
        jerseyNumber?.let { put("jerseyNumber", it) }
        teamName?.let { put("teamName", it) }
        put("isActive", isActive)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        /** teamName used to mark visiting players from the Portland chapter */
        const val PORTLAND_CHAPTER = "Portland"

        /** Returns null when a required field is missing, like the iOS decoder */
        fun from(doc: DocumentSnapshot): Player? {
            return Player(
                id = doc.id,
                name = doc.getString("name") ?: return null,
                jerseyNumber = doc.int("jerseyNumber"),
                teamName = doc.getString("teamName"),
                isActive = doc.getBoolean("isActive") ?: return null,
                createdAt = doc.date("createdAt") ?: return null,
                updatedAt = doc.date("updatedAt") ?: return null,
            )
        }
    }
}

internal fun DocumentSnapshot.int(field: String): Int? = (get(field) as? Number)?.toInt()

internal fun DocumentSnapshot.date(field: String): Date? = try {
    getDate(field)
} catch (e: RuntimeException) {
    null
}
