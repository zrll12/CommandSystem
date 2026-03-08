package cc.vastsea.zrll.commandSystem

import cc.vastsea.zrll.commandSystem.modals.CommandDispatchSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class CommandRunnerSystem : CommandExecutor, TabCompleter {
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
                val usageHints = collectUsageHints(candidates, source, label)
                sender.sendMessage("命令格式不正确：/$input")
                if (usageHints.isNotEmpty()) {
                    sender.sendMessage("可用写法：")
                    usageHints.forEach { hint -> sender.sendMessage(" - $hint") }
                } else {
                    sender.sendMessage("请检查命令参数，或使用 /$label help")
                }
            } else {
                sender.sendMessage("Error executing command: ${lastError.localizedMessage}")
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
        dispatchers: Set<CommandDispatcher<CommandDispatchSource>>,
        source: CommandDispatchSource,
        label: String
    ): List<String> {
        val hints = linkedSetOf<String>()
        dispatchers.forEach { dispatcher ->
            val usage = dispatcher.getSmartUsage(dispatcher.root, source)
            usage.values
                .filter { it.isNotBlank() }
                .forEach { value -> hints.add("/$value") }
        }
        if (hints.isEmpty()) {
            hints.add("/$label help")
        }
        return hints.toList()
    }
}
