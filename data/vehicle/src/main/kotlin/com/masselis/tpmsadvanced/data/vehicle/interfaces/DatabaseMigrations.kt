package com.masselis.tpmsadvanced.data.vehicle.interfaces

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DELTA_THREE_WHEELER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DOUBLE_AXLE_TRAILER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.SINGLE_AXLE_TRAILER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.TADPOLE_THREE_WHEELER

@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
internal fun Database.Companion.afterVersion3(
    locationAdapter: ColumnAdapter<Location, Long>
) = AfterVersion(3) { driver ->
    // Read every vehicle, including those with history but no currently assigned sensors.
    val vehicles = driver.executeQuery(null, "SELECT uuid, kind FROM Vehicle", { cursor ->
        val rows = mutableListOf<Pair<String, Vehicle.Kind>>()
        while (cursor.next().value) rows += checkNotNull(cursor.getString(0)) to
            Vehicle.Kind.valueOf(checkNotNull(cursor.getString(1)))
        QueryResult.Value(rows)
    }, 0).value
    vehicles.forEach { (vehicleUuid, kind) ->
        val locations = SensorLocation.entries.map { old ->
            when (kind) {
                CAR, DOUBLE_AXLE_TRAILER, Vehicle.Kind.CAR_WITH_SPARE, Vehicle.Kind.VAN, Vehicle.Kind.UTE ->
                    old.asWheelLocation()
                Vehicle.Kind.MONOWHEEL -> Location.Auxiliary.SINGLE
                SINGLE_AXLE_TRAILER -> when (old) {
                    FRONT_LEFT, REAR_LEFT -> Location.Side.LEFT
                    FRONT_RIGHT, REAR_RIGHT -> Location.Side.RIGHT
                }
                MOTORCYCLE -> when (old) {
                    FRONT_LEFT, FRONT_RIGHT -> Location.Axle.FRONT
                    REAR_LEFT, REAR_RIGHT -> Location.Axle.REAR
                }
                TADPOLE_THREE_WHEELER -> when (old) {
                    FRONT_LEFT, FRONT_RIGHT -> old.asWheelLocation()
                    REAR_LEFT, REAR_RIGHT -> Location.Axle.REAR
                }
                DELTA_THREE_WHEELER -> when (old) {
                    FRONT_LEFT, FRONT_RIGHT -> Location.Axle.FRONT
                    REAR_LEFT, REAR_RIGHT -> old.asWheelLocation()
                }
            }.let(locationAdapter::encode)
        }
        val sensors = driver.executeQuery(null,
            "SELECT id, location FROM Sensor WHERE vehicleId = ? ORDER BY location, id", { cursor ->
                val rows = mutableListOf<Pair<Long, Int>>()
                while (cursor.next().value) rows += checkNotNull(cursor.getLong(0)) to
                    checkNotNull(cursor.getLong(1)).toInt()
                QueryResult.Value(rows)
            }, 1) { bindString(0, vehicleUuid) }.value
        // Reinsert from a snapshot so old and new numeric location values cannot collide mid-conversion.
        driver.execute(null, "DELETE FROM Sensor WHERE vehicleId = ?", 1) { bindString(0, vehicleUuid) }
        sensors.forEach { (id, old) ->
            // A motorcycle/trailer may collapse two old wheel positions into one. Keep the first assignment.
            driver.execute(null, "INSERT OR IGNORE INTO Sensor(id, location, vehicleId) VALUES (?, ?, ?)", 3) {
                bindLong(0, id); bindLong(1, locations[old]); bindString(2, vehicleUuid)
            }
        }
        driver.execute(null,
            "UPDATE Tyre SET location = CASE location WHEN 0 THEN ? WHEN 1 THEN ? WHEN 2 THEN ? " +
                "WHEN 3 THEN ? ELSE location END WHERE vehicleId = ?", 5) {
            locations.forEachIndexed { index, location -> bindLong(index, location) }
            bindString(4, vehicleUuid)
        }
    }
}

// Copy/pasted from previous version of the code. The enum is kept here for database migration needs
private enum class SensorLocation(
    val axle: Axle,
    val side: Side,
) {
    FRONT_LEFT(Axle.FRONT, Side.LEFT),
    FRONT_RIGHT(Axle.FRONT, Side.RIGHT),
    REAR_LEFT(Axle.REAR, Side.LEFT),
    REAR_RIGHT(Axle.REAR, Side.RIGHT);

    enum class Axle {
        FRONT,
        REAR;
    }

    enum class Side {
        LEFT,
        RIGHT;
    }

    fun asWheelLocation() = when (this) {
        FRONT_LEFT -> Location.Wheel.FRONT_LEFT
        FRONT_RIGHT -> Location.Wheel.FRONT_RIGHT
        REAR_LEFT -> Location.Wheel.REAR_LEFT
        REAR_RIGHT -> Location.Wheel.REAR_RIGHT
    }
}
