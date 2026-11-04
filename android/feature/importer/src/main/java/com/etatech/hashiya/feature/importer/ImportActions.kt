package com.etatech.hashiya.feature.importer

/** Every user action on the Import screen. Defaults are no-ops so tests set only what they check. */
internal data class ImportActions(
    val onBack: () -> Unit = {},
    val onChooseFile: () -> Unit = {},
    val onAddToCollectionChange: (Boolean) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
    /** Done's "Show in library". */
    val onShowLibrary: () -> Unit = {},
    /** Done's "Import another file", and "Choose another file" after a failure or a cancel. */
    val onChooseAnother: () -> Unit = {}
)
