package com.example.healthconnectandroid

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.MedicineItemEntity
import com.example.healthconnectandroid.hc.upload.HealthUploadWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalProfileIsolationTest {
    @Test
    fun secondary_profiles_use_independent_databases() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.nanoTime().toString()
        val first = requireCreated(LocalProfileStore.create(context, "Isolation A $suffix"))
        val second = requireCreated(LocalProfileStore.create(context, "Isolation B $suffix"))

        assertNotEquals(first.databaseName, second.databaseName)
        assertTrue(!first.ownsHealthConnect && !second.ownsHealthConnect)

        val now = System.currentTimeMillis()
        AppDb.get(context, first.id).medicineDao().insertMedicine(
            MedicineItemEntity(
                name = "Profile A medicine",
                notes = null,
                doseText = null,
                summary = null,
                details = null,
                active = true,
                createdEpochMillis = now,
                updatedEpochMillis = now
            )
        )

        assertEquals(1, AppDb.get(context, first.id).medicineDao().allMedicines().size)
        assertTrue(AppDb.get(context, second.id).medicineDao().allMedicines().isEmpty())
    }

    @Test
    fun secondary_profiles_use_independent_upload_preferences() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.nanoTime().toString()
        val first = requireCreated(LocalProfileStore.create(context, "Upload A $suffix"))
        val second = requireCreated(LocalProfileStore.create(context, "Upload B $suffix"))
        val firstSettings = AppPreferences.loadUploadSettings(context, first.id).settings.copy(
            productionBaseUrl = "https://first.example.test/health/api/v1/ingest/batches",
            apiKey = "f".repeat(32),
            autoUploadEnabled = true
        )
        val secondSettings = AppPreferences.loadUploadSettings(context, second.id).settings.copy(
            productionBaseUrl = "https://second.example.test/health/api/v1/ingest/batches",
            apiKey = "s".repeat(32),
            autoUploadEnabled = false
        )

        assertTrue(AppPreferences.setUploadSettings(context, firstSettings, first.id))
        assertTrue(AppPreferences.setUploadSettings(context, secondSettings, second.id))

        val firstReloaded = AppPreferences.loadUploadSettings(context, first.id)
        val secondReloaded = AppPreferences.loadUploadSettings(context, second.id)
        assertTrue(firstReloaded is UploadSettingsLoadResult.Available)
        assertTrue(secondReloaded is UploadSettingsLoadResult.Available)
        assertEquals(firstSettings, firstReloaded.settings)
        assertEquals(secondSettings, secondReloaded.settings)
        assertFalse(firstReloaded.settings.apiKey == secondReloaded.settings.apiKey)
        assertNotEquals(
            firstReloaded.settings.deviceId,
            secondReloaded.settings.deviceId
        )

        HealthUploadWorker.enqueue(context, "missing-$suffix")
    }

    private fun requireCreated(result: CreateProfileResult): LocalProfile =
        when (result) {
            is CreateProfileResult.Created -> result.profile
            is CreateProfileResult.Rejected -> error(result.message)
        }
}
