package com.masselis.tpmsadvanced.data.vehicle

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.afterVersion3
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
internal class MigrationFrom3Test {

    @ContributesTo(AppScope::class)
    internal interface Extractor {
        val locationAdapter: ColumnAdapter<Location, Long>
    }

    private lateinit var locationAdapter: ColumnAdapter<Location, Long>

    @Before
    fun setup() {
        locationAdapter = (appGraph as Extractor).locationAdapter
    }

    @Test
    fun test() {
        val dbFile = appContext.getDatabasePath("car.db")
        dbFile.delete()
        appContext.assets.open("3.db").use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, OPEN_READWRITE).use { sqlite ->
            val car = "466b2e13-e33a-4adf-a101-a920140a29a1"
            val motorcycle = "8319836f-9c30-4280-ab20-35a07fec5c84"
            sqlite.execSQL("INSERT INTO Sensor(id,location,vehicleId) VALUES (101,1,?), (102,2,?), (103,2,?)",
                arrayOf(car, car, motorcycle))
            // Preserve wheel identity for a historical reading even when that sensor is no longer assigned.
            sqlite.execSQL("INSERT INTO Tyre(id,timestamp,location,pressure,temperature,battery,isAlarm,vehicleId) " +
                "VALUES (104,100,3,230,25,80,0,?)", arrayOf(car))
            Database.Schema.migrate(AndroidSqliteDriver(sqlite), 3, 4, Database.afterVersion3(locationAdapter))
            val sensors = sqlite.rawQuery("SELECT id,location FROM Sensor ORDER BY id", emptyArray<String>()).use {
                val rows = mutableMapOf<Int, Int>()
                while (it.moveToNext()) rows[it.getInt(0)] = it.getInt(1)
                rows
            }
            assertEquals(mapOf(1 to 20, 2 to 0, 101 to 21, 102 to 22, 103 to 1), sensors)
            sqlite.rawQuery("SELECT location FROM Tyre WHERE id=104", emptyArray<String>()).use {
                check(it.moveToFirst())
                assertEquals(23, it.getInt(0))
            }
        }
    }
}
