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
        argumentHints: Map<String, ArgumentUsageHint> = emptyMap(),
    ): List<String> {
        val parsedNodes = parse.context.nodes
        val selected = parsedNodes.asReversed().firstNotNullOfOrNull { parsedNode ->
            dispatcher.getSmartUsage(parsedNode.node, source).values
                .filter(String::isNotBlank)
                .takeIf(List<String>::isNotEmpty)
                ?.let { parsedNode to it }
        }
        val usages = selected?.second ?: dispatcher.getSmartUsage(dispatcher.root, source).values
            .filter(String::isNotBlank)
        val parsedPrefix = SyntaxExcerpt.prefix(parse.reader.string, selected?.first?.range?.end ?: 0)
        val prefix = displayedRoot?.let { root ->
            listOf(root, parsedPrefix.substringAfter(' ', "")).filter(String::isNotBlank).joinToString(" ")
        } ?: parsedPrefix
        return usages.map { rawValue ->
                val value = removeMisleadingLiteralBrackets(addArgumentHints(rawValue, argumentHints))
                "/${listOf(prefix, value).filter(String::isNotBlank).joinToString(" ")}"
            }
    }

    private fun removeMisleadingLiteralBrackets(usage: String): String =
        LITERAL_BRANCH.replace(usage) { it.groupValues[1] }

    private fun addArgumentHints(usage: String, hints: Map<String, ArgumentUsageHint>): String {
        val optionalFormatted = OPTIONAL_ARGUMENT.replace(usage) { match ->
            val name = match.groupValues[1]
            val hint = hints[name] ?: return@replace match.value
            "[$name:${hint.type}]"
        }
        return ARGUMENT.replace(optionalFormatted) { match ->
            val name = match.groupValues[1]
            val hint = hints[name] ?: return@replace match.value
            if (hint.optional) "[$name:${hint.type}]" else "<$name:${hint.type}>"
        }
    }

    private val OPTIONAL_ARGUMENT = Regex("\\[<([^>:]+)>]")
    private val ARGUMENT = Regex("<([^>:]+)>")
    private val LITERAL_BRANCH = Regex("\\[([a-zA-Z0-9_-]+)]")
}

internal data class ArgumentUsageHint(val type: String, val optional: Boolean)

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
        "command.type.integer" to "integer",
        "command.type.decimal" to "decimal",
        "command.type.boolean" to "true|false",
        "command.type.text" to "text",
        "command.type.player" to "player",
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
