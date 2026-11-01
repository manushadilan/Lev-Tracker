package com.example.leavetracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

val SCHEMES = listOf(R.style.Scheme0, R.style.Scheme1, R.style.Scheme2, R.style.Scheme3, R.style.Scheme4)

fun nightModeFor(i: Int) = when (i) {
    1 -> AppCompatDelegate.MODE_NIGHT_NO
    2 -> AppCompatDelegate.MODE_NIGHT_YES
    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(nightModeFor(getSharedPreferences("ui", MODE_PRIVATE).getInt("mode", 0)))
    }
}
