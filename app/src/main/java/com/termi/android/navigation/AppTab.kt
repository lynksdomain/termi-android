package com.termi.android.navigation

// Kotlin port of AppTab from ContentView.swift — same order and labels:
// MUSIC, BOOKS, JOURNAL, PKGS.
enum class AppTab(val label: String, val route: String) {
    MUSIC("MUSIC", Routes.MUSIC),
    BOOKS("BOOKS", Routes.BOOKS),
    JOURNAL("JOURNAL", Routes.WRITING),
    PACKAGES("PKGS", Routes.PACKAGES),
}
