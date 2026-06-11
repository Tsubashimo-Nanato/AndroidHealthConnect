package com.example.healthconnectandroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActionTest {
    @Test
    fun syncSettingsBusyActionsAreExplicit() {
        assertTrue(AppAction.PERIODIC_TOGGLE.blocksSyncSettings)
        assertTrue(AppAction.FULL_RESYNC.blocksSyncSettings)
        assertTrue(AppAction.BACKGROUND_NOW.blocksSyncSettings)
        assertFalse(AppAction.SMART_SYNC.blocksSyncSettings)
        assertFalse(AppAction.UPLOAD.blocksSyncSettings)
    }

    @Test
    fun uploadBusyActionsAreExplicit() {
        assertTrue(AppAction.UPLOAD.blocksUpload)
        assertTrue(AppAction.UPLOAD_TEST.blocksUpload)
        assertFalse(AppAction.FULL_RESYNC.blocksUpload)
    }

    @Test
    fun exportActionsAreGroupedWithoutStringPrefixes() {
        assertTrue(AppAction.EXPORT_HR.isExport)
        assertTrue(AppAction.EXPORT_ALL.isExport)
        assertTrue(AppAction.EXPORT_ZIP.isExport)
        assertTrue(AppAction.EXPORT_TYPE.isExport)
        assertFalse(AppAction.UPLOAD.isExport)
    }
}
