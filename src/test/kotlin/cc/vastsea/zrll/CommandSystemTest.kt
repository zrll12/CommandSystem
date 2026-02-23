package cc.vastsea.zrll

import kotlin.test.Test
import kotlin.test.assertEquals

class CommandSystemTest {
    @Test
    fun greetReturnsExpectedMessage() {
        val library = CommandSystem()
        assertEquals("Hello, Kotlin!", library.greet("Kotlin"))
    }
}
