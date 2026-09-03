package cc.vastsea.zrll.commandSystem

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults

internal object SyntaxExcerpt {
    fun prefix(input: String, end: Int): String =
        input.substring(0, end.coerceIn(0, input.length)).trim()

    fun context(input: String, cursor: Int): String {
        val safeCursor = cursor.coerceIn(0, input.length)
        val start = (safeCursor - 12).coerceAtLeast(0)
        val end = (safeCursor + 12).coerceAtMost(input.length)
        return input.substring(start, end).ifBlank { input }
    }

    fun pointer(input: String, cursor: Int): String =
        " ".repeat(cursor.coerceIn(0, input.length)) + "^"
}

internal object BranchUsage {
    fun <S> hints(
        dispatcher: CommandDispatcher<S>,
        parse: ParseResults<S>,
        source: S,
    ): List<String> {
        val deepestNode = parse.context.nodes.lastOrNull()
        val parent = deepestNode?.node ?: dispatcher.root
        val prefix = SyntaxExcerpt.prefix(parse.reader.string, deepestNode?.range?.end ?: 0)
        return dispatcher.getSmartUsage(parent, source).values
            .filter(String::isNotBlank)
            .map { value ->
                "/${listOf(prefix, value).filter(String::isNotBlank).joinToString(" ")}"
            }
    }
}
