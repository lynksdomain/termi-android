package com.termi.android.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "termi_settings")

// Kotlin port of AppSettings.swift — same fields, DataStore-backed instead
// of UserDefaults. Each field is a StateFlow (reactive Compose reads via
// collectAsState) with a matching setter that persists immediately,
// mirroring the Swift `didSet { UserDefaults... }` write-through pattern.
// Payment/account-gated fields (Lifetime Pro, Last.fm, tip jar, font pack,
// typewriter sounds) are intentionally not ported here — see
// android_port_rules memory on deferred payment/account features. Reading
// goals ARE ported (not account-gated on iOS either) — see
// dailyReadingGoal/pagesReadToday/recordPageAdvance below.
class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _themeId = MutableStateFlow(DEFAULT_THEME_ID)
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    private val _showScanlines = MutableStateFlow(true)
    val showScanlines: StateFlow<Boolean> = _showScanlines.asStateFlow()

    private val _showCrtVignette = MutableStateFlow(true)
    val showCrtVignette: StateFlow<Boolean> = _showCrtVignette.asStateFlow()

    private val _normalizationEnabled = MutableStateFlow(false)
    val normalizationEnabled: StateFlow<Boolean> = _normalizationEnabled.asStateFlow()

    private val _visualizerStyleId = MutableStateFlow("bars")
    val visualizerStyleId: StateFlow<String> = _visualizerStyleId.asStateFlow()

    private val _packageSignature = MutableStateFlow("")
    val packageSignature: StateFlow<String> = _packageSignature.asStateFlow()

    // Mirrors iOS's `@AppStorage("hasCompletedBoot")` — gates which
    // BootSequenceScreen variant shows (full first-launch sequence vs. the
    // condensed return-launch greeting), not whether the boot screen shows
    // at all: it shows on every launch, same as iOS.
    private val _hasCompletedBoot = MutableStateFlow(false)
    val hasCompletedBoot: StateFlow<Boolean> = _hasCompletedBoot.asStateFlow()

    private val _dailyReadingGoal = MutableStateFlow(0)
    val dailyReadingGoal: StateFlow<Int> = _dailyReadingGoal.asStateFlow()

    // Mirrors EReaderView.swift's own UserDefaults-backed pagesReadToday —
    // deliberately kept separate from the goal setting above (that one
    // lives in Theme.swift's AppSettings equivalent; this one is
    // reader-local day-keyed progress), reset whenever the stored date
    // isn't today.
    private val _pagesReadToday = MutableStateFlow(0)
    val pagesReadToday: StateFlow<Int> = _pagesReadToday.asStateFlow()

    init {
        scope.launch {
            appContext.dataStore.data.collect { prefs ->
                _themeId.value = prefs[KEY_THEME_ID] ?: DEFAULT_THEME_ID
                _showScanlines.value = prefs[KEY_SCANLINES] ?: true
                _showCrtVignette.value = prefs[KEY_VIGNETTE] ?: true
                _normalizationEnabled.value = prefs[KEY_NORMALIZATION] ?: false
                _visualizerStyleId.value = prefs[KEY_VISUALIZER] ?: "bars"
                _packageSignature.value = prefs[KEY_PACKAGE_SIGNATURE] ?: ""
                _hasCompletedBoot.value = prefs[KEY_HAS_COMPLETED_BOOT] ?: false
                _dailyReadingGoal.value = prefs[KEY_READING_GOAL] ?: 0
                val storedDate = prefs[KEY_GOAL_DATE] ?: ""
                _pagesReadToday.value = if (storedDate == todayString()) prefs[KEY_GOAL_PAGES_READ] ?: 0 else 0
            }
        }
    }

    private fun todayString(): String = java.time.LocalDate.now().toString()

    fun setDailyReadingGoal(pages: Int) {
        scope.launch { appContext.dataStore.edit { it[KEY_READING_GOAL] = pages } }
    }

    // Mirrors EReaderView.swift's recordPageAdvance() — called on forward
    // page turns only (never on scrub/jump), resetting the counter if the
    // stored date has rolled over to a new day.
    fun recordPageAdvance() {
        scope.launch {
            appContext.dataStore.edit { prefs ->
                val today = todayString()
                val storedDate = prefs[KEY_GOAL_DATE] ?: ""
                val current = if (storedDate == today) prefs[KEY_GOAL_PAGES_READ] ?: 0 else 0
                prefs[KEY_GOAL_DATE] = today
                prefs[KEY_GOAL_PAGES_READ] = current + 1
            }
        }
    }

    fun setHasCompletedBoot(value: Boolean) {
        scope.launch { appContext.dataStore.edit { it[KEY_HAS_COMPLETED_BOOT] = value } }
    }


    // One-shot direct read, bypassing `hasCompletedBoot`'s StateFlow (which
    // always starts at its `false` default until DataStore's first emission
    // lands) — BootSequenceScreen needs the REAL persisted value up front to
    // pick first-launch vs. return-launch, not a value that might still be
    // mid-flight.
    suspend fun hasCompletedBootOnce(): Boolean =
        appContext.dataStore.data.first()[KEY_HAS_COMPLETED_BOOT] ?: false

    fun setThemeId(id: String) {
        scope.launch { appContext.dataStore.edit { it[KEY_THEME_ID] = id } }
    }

    fun setShowScanlines(value: Boolean) {
        scope.launch { appContext.dataStore.edit { it[KEY_SCANLINES] = value } }
    }

    fun setShowCrtVignette(value: Boolean) {
        scope.launch { appContext.dataStore.edit { it[KEY_VIGNETTE] = value } }
    }

    fun setNormalizationEnabled(value: Boolean) {
        scope.launch { appContext.dataStore.edit { it[KEY_NORMALIZATION] = value } }
    }

    fun setVisualizerStyleId(id: String) {
        scope.launch { appContext.dataStore.edit { it[KEY_VISUALIZER] = id } }
    }

    fun setPackageSignature(value: String) {
        scope.launch { appContext.dataStore.edit { it[KEY_PACKAGE_SIGNATURE] = value } }
    }

    companion object {
        private const val DEFAULT_THEME_ID = "apple_ii"
        private val KEY_THEME_ID = stringPreferencesKey("themeId")
        private val KEY_SCANLINES = booleanPreferencesKey("showScanlines")
        private val KEY_VIGNETTE = booleanPreferencesKey("showCRTVignette")
        private val KEY_NORMALIZATION = booleanPreferencesKey("normalizationEnabled")
        private val KEY_VISUALIZER = stringPreferencesKey("visualizerStyleId")
        private val KEY_PACKAGE_SIGNATURE = stringPreferencesKey("packageSignature")
        private val KEY_HAS_COMPLETED_BOOT = booleanPreferencesKey("hasCompletedBoot")
        private val KEY_READING_GOAL = intPreferencesKey("dailyReadingGoal")
        private val KEY_GOAL_DATE = stringPreferencesKey("readingGoalDate")
        private val KEY_GOAL_PAGES_READ = intPreferencesKey("readingGoalPagesRead")

        /// "[@handle]" form shown everywhere a signature is displayed —
        /// mirrors AppSettings.formatPackageSignature.
        fun formatPackageSignature(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) "" else "[@$trimmed]"
        }
    }
}
