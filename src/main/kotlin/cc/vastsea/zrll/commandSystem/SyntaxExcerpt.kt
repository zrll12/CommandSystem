package cc.vastsea.zrll.commandSystem

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults

internal object SyntaxExcerpt {
    fun prefix(input: String, end: Int): String =
        input.substring(0, end.coerceIn(0, input.length)).trim()

    fun highlightError(input: String, cursor: Int): String {
        val safeInput = input.replace('§', '?')
        var start = cursor.coerceIn(0, safeInput.length)
        while (start < safeInput.length && safeInput[start].isWhitespace()) start++
        if (start == safeInput.length) return "§f$safeInput §c<?>"
        while (start > 0 && !safeInput[start - 1].isWhitespace()) start--
        var end = start
        while (end < safeInput.length && !safeInput[end].isWhitespace()) end++
        return buildString {
            if (start > 0) {
                append("§f")
                append(safeInput, 0, start)
            }
            append("§c")
            append(safeInput, start, end)
            append("§f")
            append(safeInput, end, safeInput.length)
        }
    }
}

internal object BranchUsage {
    fun <S> hints(
        dispatcher: CommandDispatcher<S>,
        parse: ParseResults<S>,
        source: S,
        displayedRoot: String? = null,
    ): List<String> {
        val deepestNode = parse.context.nodes.lastOrNull()
        val parent = deepestNode?.node ?: dispatcher.root
        val parsedPrefix = SyntaxExcerpt.prefix(parse.reader.string, deepestNode?.range?.end ?: 0)
        val prefix = displayedRoot?.let { root ->
            listOf(root, parsedPrefix.substringAfter(' ', "")).filter(String::isNotBlank).joinToString(" ")
        } ?: parsedPrefix
        return dispatcher.getSmartUsage(parent, source).values
            .filter(String::isNotBlank)
            .map { value ->
                "/${listOf(prefix, value).filter(String::isNotBlank).joinToString(" ")}"
            }
    }
}

internal object FrameworkMessages {
    private val defaults = mapOf(
        "command.syntax.invalid" to "§7Invalid command format: /{input}",
        "command.syntax.available" to "Available usages:",
        "command.syntax.usage" to " - {usage}",
        "command.syntax.check" to "Please check command arguments, or use /{label} help",
        "command.execute.error" to "Error executing command: {message}",
        "command.help.header" to "§eCommand help: §f/{root}",
        "command.help.empty" to "§7No command endpoints are registered.",
        "command.help.endpoint" to "§a/{root}{path} §f{params} §7— {description} §8[{permission}]",
        "command.help.no-description" to "No description",
        "command.help.no-permission" to "no permission required",
        "command.help.no-params" to "",
    )

    fun default(key: String, placeholders: Map<String, String>?): String =
        format(defaults[key] ?: key, placeholders)

    fun resolve(
        get: (String, Map<String, String>?) -> String,
        key: String,
        placeholders: Map<String, String>?,
    ): String {
        val translated = get(key, placeholders)
        return if (translated == key) default(key, placeholders) else translated
    }

    private fun format(template: String, placeholders: Map<String, String>?): String =
        placeholders.orEmpty().entries.fold(template) { result, (name, value) ->
            result.replace("{$name}", value)
        }
}
