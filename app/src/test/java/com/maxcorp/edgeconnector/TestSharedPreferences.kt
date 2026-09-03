package com.maxcorp.gosha.mobile

import android.content.SharedPreferences

internal class TestSharedPreferences : SharedPreferences {
    private val lock = Any()
    private val values = LinkedHashMap<String, Any>()
    private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = synchronized(lock) {
        LinkedHashMap(values)
    }

    override fun getString(key: String?, defValue: String?): String? = synchronized(lock) {
        values[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            val stored = values[key] as? Set<String>
            stored?.toMutableSet() ?: defValues
        }

    override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) {
        values[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) {
        values[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) {
        values[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) {
        values[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean = synchronized(lock) {
        values.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener == null) return
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any>()
        private val removals = LinkedHashSet<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            stage(key, value)
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply {
            stage(key, values?.toSet())
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            stage(key, value)
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            stage(key, value)
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            stage(key, value)
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            stage(key, value)
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key == null) return@apply
            changes.remove(key)
            removals.add(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
            changes.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun stage(key: String?, value: Any?) {
            if (key == null) return
            if (value == null) {
                changes.remove(key)
                removals.add(key)
            } else {
                removals.remove(key)
                changes[key] = value
            }
        }

        private fun applyChanges() {
            val changedKeys: Set<String>
            val listenersSnapshot: List<SharedPreferences.OnSharedPreferenceChangeListener>
            synchronized(lock) {
                val changed = LinkedHashSet<String>()
                if (clearRequested) {
                    changed.addAll(values.keys)
                    values.clear()
                }
                for (key in removals) {
                    if (values.remove(key) != null) {
                        changed.add(key)
                    }
                }
                for ((key, value) in changes) {
                    if (values[key] != value) {
                        changed.add(key)
                    }
                    values[key] = value
                }
                changedKeys = changed
                listenersSnapshot = listeners.toList()
            }
            for (key in changedKeys) {
                for (listener in listenersSnapshot) {
                    listener.onSharedPreferenceChanged(this@TestSharedPreferences, key)
                }
            }
        }
    }
}
