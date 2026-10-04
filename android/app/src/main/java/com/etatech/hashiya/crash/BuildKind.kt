package com.etatech.hashiya.crash

import android.content.Context
import android.content.pm.ApplicationInfo

/** The app module has no BuildConfig, so debug versus release is read from the installed app's flags. */
fun isDebuggable(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
