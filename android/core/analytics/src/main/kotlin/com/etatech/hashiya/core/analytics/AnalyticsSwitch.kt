package com.etatech.hashiya.core.analytics

/**
 * The on/off state behind the Firebase tracker: user properties are remembered, sent only while on, and sent again when
 * collection is turned back on (resetting analytics data clears them on the service side). Events logged before the launch
 * decides (the first [setEnabled]) are held, up to 20, and sent if it turns collection on; otherwise dropped.
 */
class AnalyticsSwitch(
    private val apply: (Boolean) -> Unit,
    private val send: (String, String) -> Unit,
    private val logEvent: (AnalyticsEvent) -> Unit
) {
    private val properties = linkedMapOf<AnalyticsProperty, String>()
    private var on = false

    /** Null once the launch has decided. */
    private var earlyEvents: MutableList<AnalyticsEvent>? = mutableListOf()

    val isOn: Boolean
        @Synchronized get() = on

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        on = enabled
        apply(enabled)
        if (enabled) properties.forEach { (property, value) -> send(property.id, value) }
        val held = earlyEvents
        earlyEvents = null
        if (enabled) held?.forEach(logEvent)
    }

    @Synchronized
    fun setProperty(property: AnalyticsProperty, value: ClosedValue) {
        properties[property] = value.id
        if (on) send(property.id, value.id)
    }

    @Synchronized
    fun log(event: AnalyticsEvent) {
        val held = earlyEvents
        when {
            on -> logEvent(event)
            held != null && held.size < EARLY_EVENTS -> held += event
        }
    }

    private companion object {
        const val EARLY_EVENTS = 20
    }
}
