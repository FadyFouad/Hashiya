package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.ClosedValue

/** Records every call, for tests. */
class FakeAnalytics : Analytics {
    private val lock = Any()
    private val _events = mutableListOf<AnalyticsEvent>()
    private val _properties = mutableMapOf<AnalyticsProperty, String>()
    private val _enabledCalls = mutableListOf<Boolean>()

    val events: List<AnalyticsEvent> get() = synchronized(lock) { _events.toList() }
    val properties: Map<AnalyticsProperty, String> get() = synchronized(lock) { _properties.toMap() }
    val enabledCalls: List<Boolean> get() = synchronized(lock) { _enabledCalls.toList() }

    override fun log(event: AnalyticsEvent) = synchronized(lock) { _events += event }

    override fun setProperty(property: AnalyticsProperty, value: ClosedValue) = synchronized(lock) { _properties[property] = value.id }

    override fun setEnabled(enabled: Boolean) = synchronized(lock) { _enabledCalls += enabled }
}
