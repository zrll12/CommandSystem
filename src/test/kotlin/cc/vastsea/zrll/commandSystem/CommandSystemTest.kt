package cc.vastsea.zrll.commandSystem

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandSystemTest {
    @Test
    fun `syntax excerpt highlights invalid tokens without depending on translated prefixes`() {
        assertEquals("command scene", SyntaxExcerpt.prefix("command scene create", 13))
        assertEquals(
            "§fcommand scene §ccreate§f",
            SyntaxExcerpt.highlightError("command scene create", 14),
        )
        assertEquals(
            "§fcommand scene §c<?>",
            SyntaxExcerpt.highlightError("command scene", 100),
        )
        assertEquals("§ccommand§f scene", SyntaxExcerpt.highlightError("command scene", -1))
        assertEquals("§c?bad§f value", SyntaxExcerpt.highlightError("§bad value", 0))
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

    @Test
    fun `usage backtracks from a completed leaf and includes argument types`() {
        val dispatcher = CommandDispatcher<Unit>()
        dispatcher.register(
            literal<Unit>("testcommandparser").then(
                literal<Unit>("get")
                    .then(
                        argument<Unit, Int>("optional", IntegerArgumentType.integer())
                            .then(argument<Unit, String>("required", StringArgumentType.word()).executes { 1 }),
                    )
                    .then(argument<Unit, String>("required", StringArgumentType.word()).executes { 1 }),
            ),
        )
        val parse = dispatcher.parse("testcommandparser get ui 1", Unit)

        val hints = BranchUsage.hints(
            dispatcher,
            parse,
            Unit,
            displayedRoot = "tcp",
            argumentHints = mapOf("optional" to "integer", "required" to "text"),
        )

        kotlin.test.assertTrue(hints.any { it.startsWith("/tcp get ") })
        kotlin.test.assertTrue(hints.any { "optional:integer" in it })
        kotlin.test.assertTrue(hints.any { "required:text" in it })
    }
}
