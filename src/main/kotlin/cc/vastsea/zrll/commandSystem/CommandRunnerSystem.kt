package cc.vastsea.zrll.commandSystem

import cc.vastsea.zrll.commandSystem.modals.CommandDispatchSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class CommandRunnerSystem(
    private val get: (key: String, placeholders: Map<String, String>?) -> String = ::defaultGet
) : CommandExecutor, TabCompleter {
    private val dispatchers = linkedMapOf<String, MutableList<CommandDispatcher<CommandDispatchSource>>>()

    private val sourceMapper: (CommandSender, Command, String, Array<out String>) -> CommandDispatchSource =
        { sender, command, label, args ->
            CommandDispatchSource(sender, command, label, args)
        }

    fun addDispatcher(commandName: String, dispatcher: CommandDispatcher<CommandDispatchSource>) {
        val key = commandName.lowercase()
        dispatchers.getOrPut(key) { mutableListOf() }.add(dispatcher)
    }

    fun execute(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val input = buildInput(command, args, forceTrailingSpace = false)
        val source = sourceMapper(sender, command, label, args)
        val candidates = resolveCandidates(command, label)

        var lastError: Exception? = null
        for (dispatcher in candidates) {
            try {
                if (dispatcher.execute(input, source) > 0) {
                    return true
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (lastError != null) {
            if (lastError is CommandSyntaxException) {
                val parses = candidates.map { dispatcher -> dispatcher to dispatcher.parse(input, source) }
                val cursor = furthestCursor(lastError, parses)
                val usageHints = collectUsageHints(parses, source, label)
                sender.sendMessage(
                    get("command.syntax.invalid", mapOf("input" to input))
                )
                sender.sendMessage(
                    get(
                        "command.syntax.location",
                        mapOf(
                            "cursor" to cursor.toString(),
                            "context" to SyntaxExcerpt.context(input, cursor),
                            "pointer" to SyntaxExcerpt.pointer(input, cursor)
                        )
                    )
                )
                if (usageHints.isNotEmpty()) {
                    sender.sendMessage(get("command.syntax.available", null))
                    usageHints.forEach { hint ->
                        sender.sendMessage(get("command.syntax.usage", mapOf("usage" to hint)))
                    }
                } else {
                    sender.sendMessage(
                        get("command.syntax.check", mapOf("label" to label))
                    )
                }
            } else {
                sender.sendMessage(
                    get(
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
        val input = buildInput(command, args, forceTrailingSpace = args.isEmpty())
        val source = sourceMapper(sender, command, label, args)
        val candidates = resolveCandidates(command, label)
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val suggestions = linkedSetOf<String>()
        candidates.forEach { dispatcher ->
            try {
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
        command: Command,
        args: Array<out String>,
        forceTrailingSpace: Boolean
    ): String {
        return buildString {
            append(command.name)
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
        parses: List<Pair<CommandDispatcher<CommandDispatchSource>, ParseResults<CommandDispatchSource>>>,
        source: CommandDispatchSource,
        label: String
    ): List<String> {
        val hints = linkedSetOf<String>()
        parses.forEach { (dispatcher, parse) ->
            hints.addAll(BranchUsage.hints(dispatcher, parse, source))
        }
        if (hints.isEmpty()) {
            hints.add("/$label help")
        }
        return hints.toList()
    }

    private fun furthestCursor(
        failure: CommandSyntaxException,
        parses: List<Pair<CommandDispatcher<CommandDispatchSource>, ParseResults<CommandDispatchSource>>>
    ): Int = maxOf(
        failure.cursor.coerceAtLeast(0),
        parses.maxOfOrNull { (_, parse) -> parse.reader.cursor } ?: 0
    )

    companion object {
        private val defaultMessages = mapOf(
            "command.syntax.invalid" to "Invalid command format: /{input}",
            "command.syntax.location" to "Problem near `{context}` (character {cursor})\n/{pointer}",
            "command.syntax.available" to "Available usages:",
            "command.syntax.usage" to " - {usage}",
            "command.syntax.check" to "Please check command arguments, or use /{label} help",
            "command.execute.error" to "Error executing command: {message}"
        )

        private fun defaultGet(key: String, placeholders: Map<String, String>?): String {
            val template = defaultMessages[key] ?: key
            if (placeholders.isNullOrEmpty()) {
                return template
            }
            return placeholders.entries.fold(template) { result, (name, value) ->
                result.replace("{$name}", value)
            }
        }
    }
}
