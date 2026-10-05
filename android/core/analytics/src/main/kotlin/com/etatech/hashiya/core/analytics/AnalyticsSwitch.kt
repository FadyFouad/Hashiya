package com.etatech.hashiya.core.analytics

/**
 * The on/off state behind the Firebase tracker: user properties are remembered, sent only while on, and sent again when
 * collection is turned back on (resetting analytics data clears them on the service side).
 */
class AnalyticsSwitch(private val apply: (Boolean) -> Unit, private val send: (String, String) -> Unit) {
    private val properties = linkedMapOf<AnalyticsProperty, String>()
    private var on = false

    val isOn: Boolean
        @Synchronized get() = on

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        on = enabled
        apply(enabled)
        if (enabled) properties.forEach { (property, value) -> send(property.id, value) }
    }

    @Synchronized
    fun setProperty(property: AnalyticsProperty, value: ClosedValue) {
        properties[property] = value.id
        if (on) send(property.id, value.id)
    }
}
