package cc.vastsea.zrll.commandSystem

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandSystemTest {
    @Test
    fun `syntax excerpt points at the failing input and clamps invalid cursors`() {
        assertEquals("command scene", SyntaxExcerpt.prefix("command scene create", 13))
        assertEquals("mmand scene create", SyntaxExcerpt.context("command scene create", 14))
        assertEquals(" ".repeat(20) + "^", SyntaxExcerpt.pointer("command scene create", 100))
        assertEquals("^", SyntaxExcerpt.pointer("command scene create", -1))
    }

    @Test
    fun `usage hints stay within the deepest successfully parsed branch`() {
        val dispatcher = CommandDispatcher<Unit>()
        dispatcher.register(
            literal<Unit>("iam").then(
                literal<Unit>("scene")
                    .then(literal<Unit>("create"))
                    .then(literal<Unit>("list")),
            ),
        )

        val parse = dispatcher.parse("iam scene unknown", Unit)

        assertEquals(
            listOf("/iam scene create", "/iam scene list"),
            BranchUsage.hints(dispatcher, parse, Unit),
        )
    }

    @Test
    fun `missing custom translations fall back instead of exposing message keys`() {
        val custom = { key: String, _: Map<String, String>? -> key }

        assertEquals(
            " - /iam scene list",
            FrameworkMessages.resolve(custom, "command.syntax.usage", mapOf("usage" to "/iam scene list")),
        )
    }

    @Test
    fun `usage can display the alias the sender actually used`() {
        val dispatcher = CommandDispatcher<Unit>()
        dispatcher.register(literal<Unit>("testcommandparser").then(literal<Unit>("get")))
        val parse = dispatcher.parse("testcommandparser unknown", Unit)

        assertEquals(
            listOf("/tcp get"),
            BranchUsage.hints(dispatcher, parse, Unit, displayedRoot = "tcp"),
        )
    }
}
