package com.imashnake.animite.media

import android.content.Context

object LocalLibraryManager {
    private const val PREFS_NAME = "animite_local_library"
    private const val KEY_SAVED_IDS = "saved_media_ids"

    fun isInLibrary(context: Context, animeId: Int): Boolean {
        if (animeId <= 0) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SAVED_IDS, emptySet()) ?: emptySet()
        return set.contains(animeId.toString())
    }

    fun toggleLibrary(context: Context, animeId: Int): Boolean {
        if (animeId <= 0) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SAVED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val isNowAdded = if (set.contains(animeId.toString())) {
            set.remove(animeId.toString())
            false
        } else {
            set.add(animeId.toString())
            true
        }
        prefs.edit().putStringSet(KEY_SAVED_IDS, set).apply()
        return isNowAdded
    }
}
