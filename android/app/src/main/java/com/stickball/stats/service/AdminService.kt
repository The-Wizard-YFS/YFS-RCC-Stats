package com.stickball.stats.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Admin mode with PIN authentication (resets when the app closes) */
class AdminService {
    companion object {
        val shared = AdminService()
    }

    private val adminPIN = "2234"

    var isAdminMode by mutableStateOf(false)
        private set

    /** Returns true if the PIN is correct */
    fun authenticate(pin: String): Boolean {
        if (pin == adminPIN) {
            isAdminMode = true
            return true
        }
        return false
    }

    fun logout() {
        isAdminMode = false
    }
}
