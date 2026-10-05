package com.etatech.hashiya.core.analytics

/**
 * Counts how features are used. Only `:app` knows the service behind it. Events, parameters and values are closed lists,
 * so nothing a person typed or read can be attached.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)

    fun setProperty(property: AnalyticsProperty, value: ClosedValue)

    /** Starts or stops collection. Stopping also clears the analytics id and events not yet sent. */
    fun setEnabled(enabled: Boolean)
}

/** Debug builds and tests: counts nothing. */
object NoOpAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit

    override fun setProperty(property: AnalyticsProperty, value: ClosedValue) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}
