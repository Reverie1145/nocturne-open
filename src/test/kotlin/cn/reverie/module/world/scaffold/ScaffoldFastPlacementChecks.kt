package cn.reverie.module.world.scaffold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ScaffoldFastPlacementChecks {
    @Test
    fun snapInteractionSnapbackOrderAndOriginalLookArePreserved() {
        val events = mutableListOf<String>()
        val result = Any()
        var look = -37f to 12f
        val actual = withScaffoldFastRotation(170f, 80f, -37f, 12f,
            sendRotation = { yaw, pitch -> look = yaw to pitch; events += "Rot:$yaw:$pitch" },
            interact = { assertEquals(170f to 80f, look); events += "UseItemOn"; result }
        )
        assertSame(result, actual)
        assertEquals(-37f to 12f, look)
        assertEquals(listOf("Rot:170.0:80.0", "UseItemOn", "Rot:-37.0:12.0"), events)
    }

    @Test
    fun rejectedInteractionStillRestoresLook() {
        val events = mutableListOf<String>()
        assertEquals(false, withScaffoldFastRotation(0f, 90f, 0f, 0f,
            sendRotation = { yaw, pitch -> events += "Rot:$yaw:$pitch" },
            interact = { events += "FAIL"; false }
        ))
        assertEquals(listOf("Rot:0.0:90.0", "FAIL", "Rot:0.0:0.0"), events)
    }

    @Test
    fun throwingInteractionStillRestoresLook() {
        val events = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            withScaffoldFastRotation(0f, 90f, 42f, -10f,
                sendRotation = { yaw, pitch -> events += "Rot:$yaw:$pitch" },
                interact = { events += "UseItemOn"; error("prediction failed") }
            )
        }
        assertEquals(listOf("Rot:0.0:90.0", "UseItemOn", "Rot:42.0:-10.0"), events)
    }

    @Test
    fun throwingSnapStillRestoresLookWithoutInteracting() {
        val events = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            withScaffoldFastRotation(0f, 90f, 42f, -10f,
                sendRotation = { yaw, pitch ->
                    events += "Rot:$yaw:$pitch"
                    if (pitch == 90f) error("send failed")
                },
                interact = { events += "UseItemOn" }
            )
        }
        assertEquals(listOf("Rot:0.0:90.0", "Rot:42.0:-10.0"), events)
    }
}
