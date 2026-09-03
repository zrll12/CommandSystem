package cc.vastsea.zrll.commandSystem

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.lang.reflect.Proxy
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

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
            argumentHints = mapOf(
                "optional" to ArgumentUsageHint("integer", optional = true),
                "required" to ArgumentUsageHint("text", optional = false),
            ),
        )

        assertTrue(hints.any { it.startsWith("/tcp get ") })
        assertTrue(hints.any { "[optional:integer]" in it })
        assertTrue(hints.any { "required:text" in it })
        assertTrue(hints.none { "<optional:integer>" in it })
    }

    @Test
    fun `alias root help combines endpoints from every matching command tree`() {
        val runner = CommandRunnerSystem()
        CommandSystem().apply {
            command("testcommandparser") { literal("get") { executes(HelpEndpoint::execute) } }
            command("tcp") { literal("set") { executes(HelpEndpoint::execute) } }
            finalize(runner)
        }
        val messages = mutableListOf<String>()
        val sender = Proxy.newProxyInstance(
            CommandSender::class.java.classLoader,
            arrayOf(CommandSender::class.java),
        ) { _, method, args ->
            if (method.name == "sendMessage" && args?.firstOrNull() is String) {
                messages += args[0] as String
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        } as CommandSender
        val command = object : Command("testcommandparser") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>) = false
        }

        runner.execute(sender, command, "tcp", emptyArray())

        assertTrue(messages.any { "/tcp get" in it })
        assertTrue(messages.any { "/tcp set" in it })
        assertEquals(1, messages.count { "Command help" in it })
    }

    @Test
    fun `help argument syntax preserves optional brackets and colors enum separators`() {
        val runner = CommandRunnerSystem()

        assertEquals("[optional:integer]", runner.argumentUsage("optional", Int::class.java, optional = true))
        assertEquals("<required:text>", runner.argumentUsage("required", String::class.java, optional = false))
        assertEquals("§ffast§6|§fsafe§6|§fdebug", runner.argumentTypeHint(Mode::class.java))
    }

    private enum class Mode { FAST, SAFE, DEBUG }
}

class HelpEndpoint {
    fun execute(sender: CommandSender) = Unit
}
