package com.example.healthconnectandroid

enum class AppAction {
    PERIODIC_TOGGLE,
    FULL_RESYNC,
    BACKGROUND_NOW,
    UPLOAD_TEST,
    UPLOAD,
    SMART_SYNC,
    EXPORT_HR,
    EXPORT_ALL,
    EXPORT_ZIP,
    EXPORT_TYPE;

    val blocksSyncSettings: Boolean
        get() = when (this) {
            PERIODIC_TOGGLE,
            FULL_RESYNC,
            BACKGROUND_NOW -> true
            else -> false
        }

    val blocksUpload: Boolean
        get() = this == UPLOAD || this == UPLOAD_TEST

    val isExport: Boolean
        get() = when (this) {
            EXPORT_HR,
            EXPORT_ALL,
            EXPORT_ZIP,
            EXPORT_TYPE -> true
            else -> false
        }
}
