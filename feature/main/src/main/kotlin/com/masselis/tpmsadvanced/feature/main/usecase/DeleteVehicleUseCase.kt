package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

internal class DeleteVehicleUseCase(
    private val vehicle: Vehicle,
    private val database: VehicleDatabase,
    private val scope: CoroutineScope,
) {

    suspend fun delete() = withContext(NonCancellable + IO) {
        val prepared = RideRuntime.selectionMutex.withLock {
            database.prepareDeletion(vehicle.uuid).also {
                if (it) RideRuntime.manualSelection.value = true
            }
        }
        if (prepared.not()) return@withContext
        scope.cancel()
        launch {
            // Deletes the vehicle after a 1-second timeout
            delay(1.seconds)
            database.delete(vehicle.uuid)
        }
    }
}
