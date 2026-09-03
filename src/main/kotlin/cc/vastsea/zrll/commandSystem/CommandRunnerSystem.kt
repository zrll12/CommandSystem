package cc.vastsea.zrll.commandSystem

import cc.vastsea.zrll.commandSystem.modals.CommandDispatchSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.IdentityHashMap

class CommandRunnerSystem(
    private val get: (key: String, placeholders: Map<String, String>?) -> String = FrameworkMessages::default
) : CommandExecutor, TabCompleter {
    private val dispatchers = linkedMapOf<String, MutableList<CommandDispatcher<CommandDispatchSource>>>()
    private val argumentHints =
        IdentityHashMap<CommandDispatcher<CommandDispatchSource>, Map<String, ArgumentUsageHint>>()
    private val generatedHelp = IdentityHashMap<CommandDispatcher<CommandDispatchSource>, GeneratedHelp>()

    private val sourceMapper: (CommandSender, Command, String, Array<out String>) -> CommandDispatchSource =
        { sender, command, label, args ->
            CommandDispatchSource(sender, command, label, args)
        }

    fun addDispatcher(commandName: String, dispatcher: CommandDispatcher<CommandDispatchSource>) {
        addDispatcher(commandName, dispatcher, emptyMap())
    }

    internal fun addDispatcher(
        commandName: String,
        dispatcher: CommandDispatcher<CommandDispatchSource>,
        hints: Map<String, ArgumentUsageHint>,
        help: GeneratedHelp? = null,
    ) {
        val key = commandName.lowercase()
        dispatchers.getOrPut(key) { mutableListOf() }.add(dispatcher)
        argumentHints[dispatcher] = hints
        if (help != null) generatedHelp[dispatcher] = help
    }

    fun execute(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val displayedInput = buildInput(label, args, forceTrailingSpace = false)
        val source = sourceMapper(sender, command, label, args)
        val candidates = resolveCandidates(command, label)
        val helpRenderers = candidates.mapNotNull(generatedHelp::get)
        val wantsCombinedHelp = helpRenderers.size > 1 && (
            (args.isEmpty() && helpRenderers.all(GeneratedHelp::handlesRoot)) ||
                (args.size == 1 && args[0].equals("help", ignoreCase = true))
            )
        if (wantsCombinedHelp) {
            helpRenderers.forEachIndexed { index, help -> help.render(source, label, index == 0) }
            return true
        }

        var lastError: Exception? = null
        val attempts = mutableListOf<ParseAttempt>()
        for (dispatcher in candidates) {
            val root = dispatcherRoot(dispatcher, command.name)
            val input = buildInput(root, args, forceTrailingSpace = false)
            try {
                if (dispatcher.execute(input, source) > 0) {
                    return true
                }
            } catch (e: Exception) {
                lastError = e
                attempts += ParseAttempt(dispatcher, root, input, e)
            }
        }

        if (lastError != null) {
            if (lastError is CommandSyntaxException) {
                val parses = attempts.map { attempt ->
                    ParsedAttempt(attempt, attempt.dispatcher.parse(attempt.input, source))
                }
                val cursor = furthestCursor(parses, label, displayedInput.length)
                val usageHints = collectUsageHints(parses, source, label)
                sender.sendMessage(
                    message(
                        "command.syntax.invalid",
                        mapOf("input" to SyntaxExcerpt.highlightError(displayedInput, cursor)),
                    )
                )
                if (usageHints.isNotEmpty()) {
                    sender.sendMessage(message("command.syntax.available", null))
                    usageHints.forEach { hint ->
                        sender.sendMessage(message("command.syntax.usage", mapOf("usage" to hint)))
                    }
                } else {
                    sender.sendMessage(
                        message("command.syntax.check", mapOf("label" to label))
                    )
                }
            } else {
                sender.sendMessage(
                    message(
                        "command.execute.error",
                        mapOf("message" to (lastError.localizedMessage ?: "unknown error"))
                    )
                )
                lastError.printStackTrace()
            }
            return true
        }

        return false
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        return execute(sender, command, label, args)
    }

    fun tabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        val source = sourceMapper(sender, command, label, args)
        val candidates = resolveCandidates(command, label)
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val suggestions = linkedSetOf<String>()
        candidates.forEach { dispatcher ->
            try {
                val input = buildInput(
                    dispatcherRoot(dispatcher, command.name),
                    args,
                    forceTrailingSpace = args.isEmpty(),
                )
                val parseResults = dispatcher.parse(input, source)
                val result = dispatcher.getCompletionSuggestions(parseResults).join()
                result.list.forEach { suggestion ->
                    suggestions.add(suggestion.text)
                }
            } catch (_: Exception) {
            }
        }
        return suggestions.toList()
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        return tabComplete(sender, command, alias, args).toMutableList()
    }

    private fun buildInput(
        root: String,
        args: Array<out String>,
        forceTrailingSpace: Boolean
    ): String {
        return buildString {
            append(root)
            if (args.isNotEmpty()) {
                append(' ')
                append(args.joinToString(" "))
            } else if (forceTrailingSpace) {
                append(' ')
            }
        }
    }

    private fun resolveCandidates(
        command: Command,
        label: String
    ): LinkedHashSet<CommandDispatcher<CommandDispatchSource>> {
        val candidates = linkedSetOf<CommandDispatcher<CommandDispatchSource>>()
        dispatchers[command.name.lowercase()]?.let { candidates.addAll(it) }
        dispatchers[label.lowercase()]?.let { candidates.addAll(it) }
        if (candidates.isEmpty()) {
            dispatchers.values.forEach { candidates.addAll(it) }
        }
        return candidates
    }

    private fun collectUsageHints(
        parses: List<ParsedAttempt>,
        source: CommandDispatchSource,
        label: String
    ): List<String> {
        val deepestMatch = parses.maxOfOrNull { it.parse.context.nodes.size } ?: 0
        val hints = linkedSetOf<String>()
        parses.asSequence()
            .filter { it.parse.context.nodes.size == deepestMatch }
            .forEach { attempt ->
            hints.addAll(
                BranchUsage.hints(
                    attempt.attempt.dispatcher,
                    attempt.parse,
                    source,
                    label,
                    argumentHints[attempt.attempt.dispatcher].orEmpty(),
                )
            )
        }
        if (hints.isEmpty()) {
            hints.add("/$label help")
        }
        return hints.take(MAX_USAGE_HINTS)
    }

    private fun furthestCursor(parses: List<ParsedAttempt>, label: String, maximum: Int): Int =
        parses.maxOfOrNull { parsed ->
            val rawCursor = maxOf(
                (parsed.attempt.failure as? CommandSyntaxException)?.cursor ?: 0,
                parsed.parse.reader.cursor,
            )
            label.length + (rawCursor - parsed.attempt.root.length).coerceAtLeast(0)
        }?.coerceIn(0, maximum) ?: maximum

    private fun dispatcherRoot(
        dispatcher: CommandDispatcher<CommandDispatchSource>,
        fallback: String,
    ): String = dispatcher.root.children.singleOrNull()?.name ?: fallback

    internal fun message(key: String, placeholders: Map<String, String>?): String =
        FrameworkMessages.resolve(get, key, placeholders)

    internal fun argumentUsage(name: String, type: Class<*>, optional: Boolean): String {
        val value = "$name:${argumentTypeHint(type)}"
        return if (optional) "[$value]" else "<$value>"
    }

    internal fun argumentTypeHint(type: Class<*>): String {
        if (type.isEnum) {
            return type.enumConstants.joinToString("§6|§f", prefix = "§f") {
                (it as Enum<*>).name.lowercase()
            }
        }
        val key = when (type.name) {
            "java.lang.Integer", "int", "java.lang.Long", "long" -> "command.type.integer"
            "java.lang.Double", "double", "java.lang.Float", "float" -> "command.type.decimal"
            "java.lang.Boolean", "boolean" -> "command.type.boolean"
            "java.lang.String" -> "command.type.text"
            "org.bukkit.entity.Player" -> "command.type.player"
            else -> return type.simpleName
        }
        return message(key, null)
    }

    private data class ParseAttempt(
        val dispatcher: CommandDispatcher<CommandDispatchSource>,
        val root: String,
        val input: String,
        val failure: Exception,
    )

    private data class ParsedAttempt(
        val attempt: ParseAttempt,
        val parse: ParseResults<CommandDispatchSource>,
    )

    private companion object {
        const val MAX_USAGE_HINTS = 4
    }
}

internal data class GeneratedHelp(
    val handlesRoot: Boolean,
    val render: (CommandDispatchSource, displayedRoot: String, includeHeader: Boolean) -> Unit,
)
