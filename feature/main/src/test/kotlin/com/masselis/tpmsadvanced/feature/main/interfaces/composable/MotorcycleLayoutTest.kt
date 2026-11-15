package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.core.ui.LocalReadability
import com.masselis.tpmsadvanced.core.ui.Readability
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreIconViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class MotorcycleLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 703,
        density = com.android.resources.Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material3.DayNight.NoActionBar")
    private val icon = object : TyreIconViewModel {
        override val stateFlow = MutableStateFlow<TyreIconStateFlow.State>(TyreIconStateFlow.State.NotDetected)
    }

    @Test fun allDetailsFitAtNormalSize() = render()
    @Test fun screenshotFontKeepsReadingsBelowControls() = render(fontScale = 1.3f)
    @Test fun screenshotFontCanReachRearDetails() = render(fontScale = 1.3f, scrollToEnd = true)
    @Test fun smallScreenLargeReadoutsCanReachRearWarnings() =
        render(fontScale = 2f, readingScale = 2f, height = 500, scrollToEnd = true, warning = true)
    @Test fun smallScreenLargeReadoutsKeepFrontBelowControls() =
        render(fontScale = 2f, readingScale = 2f, height = 500, warning = true)

    @Suppress("LongMethod") // Keep the screenshot fixture and its measured geometry assertions together.
    private fun render(fontScale: Float = 1f, readingScale: Float = 1f, height: Int = 703,
        scrollToEnd: Boolean = false, warning: Boolean = false) {
        val readings = mutableMapOf<Location, Rect>()
        val tyres = mutableMapOf<Location, Rect>()
        val bindings = mutableMapOf<Location, Rect>()
        var viewport = Rect.Zero
        var toolbar = Rect.Zero
        val scrollState = ScrollState(if (scrollToEnd) Int.MAX_VALUE else 0)
        val normal = State.Normal(1_794_611_700.0, 0x562D00, 241.3f.kpa, PressureUnit.PSI, 21f.celsius,
            TemperatureUnit.CELSIUS, SensorBattery.Voltage(2.96f))
        paparazzi.snapshot {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalReadability provides Readability(highVisibility = warning, scale = readingScale)) {
                MaterialTheme { Surface(Modifier.fillMaxWidth().height(height.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        Text("bike", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineLarge)
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Sensors healthy · monitoring not active",
                                    style = MaterialTheme.typography.titleSmall)
                                Text("Monitoring stopped", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        FlowRow(Modifier.onGloballyPositioned {
                            toolbar = Rect(it.positionInRoot(), it.size.toSize())
                        }) {
                            TextButton({}) { Text("Towing combinations") }
                            TextButton({}) { Text("Tyre insights") }
                        }
                        MotorcycleLayout(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                            viewport = Rect(it.positionInRoot(), it.size.toSize())
                        }, scrollState,
                            tyre = { location, modifier -> Tyre(location, SnackbarHostState(), modifier
                                .onGloballyPositioned {
                                    tyres[location] = Rect(it.positionInRoot(), it.size.toSize())
                                },
                                vehicleComponent = lazy { error("Test supplies its own view model") },
                                viewModel = icon) },
                            reading = { location, modifier ->
                                val state = if (location == Location.Axle.FRONT) normal else normal.copy(
                                    pressure = 289.6f.kpa, temperature = 26f.celsius,
                                    sensorBattery = if (warning) SensorBattery.Percentage(5)
                                        else SensorBattery.Voltage(3.02f),
                                    pressureLoss = if (warning) PressureLoss(10f.kpa, 0.0) else null)
                                ReadingValues(location, state, modifier.onGloballyPositioned {
                                    readings[location] = Rect(it.positionInRoot(), it.size.toSize())
                                })
                            },
                            binding = { location, modifier -> TextButton({}, modifier.onGloballyPositioned {
                                bindings[location] = Rect(it.positionInRoot(), it.size.toSize())
                            }) { Text("+") } },
                        )
                    }
                } }
            }
        }
        val front = readings.getValue(Location.Axle.FRONT)
        val rear = readings.getValue(Location.Axle.REAR)
        assertTrue(viewport.top >= toolbar.bottom, "Dashboard controls must have their own space")
        assertTrue(rear.top > front.bottom, "Front and rear readings must not overlap")
        tyres.values.plus(bindings.values).forEach { bounds ->
            assertTrue(bounds.top >= front.bottom && bounds.bottom <= rear.top, "Diagram controls overlap readings")
        }
        readings.values.forEach { bounds ->
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right, "Reading outside viewport width")
        }
        if (scrollToEnd) {
            assertTrue(rear.bottom <= viewport.bottom, "Rear battery, timestamp and freshness must be reachable")
            assertEquals(scrollState.maxValue, scrollState.value)
        } else {
            assertTrue(front.top >= viewport.top, "Front readings must not paint over dashboard controls")
        }
        if (fontScale == 1f) {
            assertEquals(0, scrollState.maxValue, "Normal readings should fit without scrolling")
            assertTrue(rear.bottom <= viewport.bottom)
        }
        if (warning) assertTrue(scrollState.maxValue > 0, "Large text must create real scrollable content")
    }
}
