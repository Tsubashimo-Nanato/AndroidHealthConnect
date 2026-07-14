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
    EXPORT_TYPE,
    CLEAR_LOCAL_DATA;

    val blocksSyncSettings: Boolean
        get() = when (this) {
            PERIODIC_TOGGLE,
            FULL_RESYNC,
            BACKGROUND_NOW,
            CLEAR_LOCAL_DATA -> true
            else -> false
        }

    val blocksUpload: Boolean
        get() = this == UPLOAD || this == UPLOAD_TEST || this == CLEAR_LOCAL_DATA

    val isExport: Boolean
        get() = when (this) {
            EXPORT_HR,
            EXPORT_ALL,
            EXPORT_ZIP,
            EXPORT_TYPE -> true
            else -> false
        }

    val blocksDataManagement: Boolean
        get() = isExport || this == CLEAR_LOCAL_DATA
}
