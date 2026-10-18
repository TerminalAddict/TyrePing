package com.masselis.tpmsadvanced.ressource

import org.junit.Assert.*
import org.junit.Test

class ResourceReportNameTest {
    @Test fun `long task lists stay bounded and distinct`() {
        val tasks = List(40) { ":feature:module$it:testDebugUnitTest" }
        val first = resourceReportName(tasks)
        assertTrue(first.length < 128)
        assertNotEquals(first, resourceReportName(tasks + ":app:phone:assembleDebug"))
        assertEquals(first, resourceReportName(tasks))
    }

    @Test fun `task separators cannot create file paths`() {
        assertTrue(resourceReportName(listOf("../../bad/任务")).matches(Regex("[A-Za-z0-9_.-]+")))
        assertTrue(resourceReportName(emptyList()).startsWith("build-"))
    }
}
