package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

internal class FindTyreComponentUseCaseTest {
    private fun finder(additional: (Location.Auxiliary) -> TyreComponent): FindTyreComponentUseCase {
        val vehicle = mockk<Vehicle> { every { kind } returns Vehicle.Kind.CAR_WITH_SPARE }
        val unused = lazy { error("Unexpected road wheel") }
        return FindTyreComponentUseCase(vehicle, unused, unused, unused, unused, unused, unused, unused, unused,
            additional)
    }

    @Test fun `concurrent spare lookups share one component and its monitoring state`() {
        val created = AtomicInteger()
        val finder = finder {
            created.incrementAndGet()
            Thread.sleep(20) // Keep the factory in flight while competing consumers enter the cache.
            mockk()
        }
        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..8).map { workers.submit<TyreComponent> {
                start.await()
                finder(Location.Auxiliary.SPARE)
            } }
            start.countDown()
            val components = results.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, created.get())
            components.forEach { assertSame(components.first(), it) }
        } finally { workers.shutdownNow() }
    }

    @Test fun `unsupported wheel is rejected even with JVM assertions disabled`() {
        assertFailsWith<IllegalArgumentException> {
            finder { error("Must not create unsupported wheel") }(Location.Auxiliary.SINGLE)
        }
    }
}
