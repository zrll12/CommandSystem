package cc.vastsea.zrll.commandSystem.modals

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

interface CommandArgumentResolver {
    val supportedTypes: Set<Class<*>>

    fun brigadierType(): ArgumentType<*>

    fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any

    fun suggest(
        context: CommandContext<CommandDispatchSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return builder.buildFuture()
    }
}

object CommandArgumentResolvers {
    private val resolvers = listOf(
        StringArgumentResolver,
        IntArgumentResolver,
        LongArgumentResolver,
        DoubleArgumentResolver,
        BooleanArgumentResolver,
        PlayerArgumentResolver
    )

    private val resolverByType = mutableMapOf<Class<*>, CommandArgumentResolver>()

    init {
        resolvers.forEach { resolver ->
            register(resolver)
        }
    }

    fun register(resolver: CommandArgumentResolver, replaceExisting: Boolean = true) {
        resolver.supportedTypes.forEach { type ->
            val normalized = boxed(type)
            if (!replaceExisting && resolverByType.containsKey(normalized)) {
                throw IllegalArgumentException("Resolver already exists for type: ${normalized.name}")
            }
            resolverByType[normalized] = resolver
        }
    }

    fun find(type: Class<*>): CommandArgumentResolver {
        val normalized = boxed(type)
        return resolverByType[normalized]
            ?: enumResolverFor(normalized)
            ?: throw IllegalArgumentException("Unsupported argument type: ${type.name}")
    }

    private fun enumResolverFor(type: Class<*>): CommandArgumentResolver? {
        if (!type.isEnum) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val enumType = type as Class<out Enum<*>>
        return EnumArgumentResolver(enumType)
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        Long::class.javaPrimitiveType -> Long::class.javaObjectType
        Double::class.javaPrimitiveType -> Double::class.javaObjectType
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        else -> type
    }
}

private object StringArgumentResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(String::class.java)

    override fun brigadierType(): ArgumentType<*> = StringArgumentType.word()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        return StringArgumentType.getString(context, argumentName)
    }
}

private object IntArgumentResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(Int::class.javaObjectType, Int::class.javaPrimitiveType!!)

    override fun brigadierType(): ArgumentType<*> = IntegerArgumentType.integer()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        return IntegerArgumentType.getInteger(context, argumentName)
    }
}

private object LongArgumentResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(Long::class.javaObjectType, Long::class.javaPrimitiveType!!)

    override fun brigadierType(): ArgumentType<*> = LongArgumentType.longArg()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        return LongArgumentType.getLong(context, argumentName)
    }
}

private object DoubleArgumentResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(Double::class.javaObjectType, Double::class.javaPrimitiveType!!)

    override fun brigadierType(): ArgumentType<*> = DoubleArgumentType.doubleArg()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        return DoubleArgumentType.getDouble(context, argumentName)
    }
}

private object BooleanArgumentResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType!!)

    override fun brigadierType(): ArgumentType<*> = BoolArgumentType.bool()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        return BoolArgumentType.getBool(context, argumentName)
    }

    override fun suggest(
        context: CommandContext<CommandDispatchSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        builder.suggest("true")
        builder.suggest("false")
        return builder.buildFuture()
    }
}

private object PlayerArgumentResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(Player::class.java)

    override fun brigadierType(): ArgumentType<*> = StringArgumentType.word()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        val playerName = StringArgumentType.getString(context, argumentName)
        return Bukkit.getPlayerExact(playerName)
            ?: Bukkit.getPlayer(playerName)
            ?: throw IllegalArgumentException("Player not found: $playerName")
    }

    override fun suggest(
        context: CommandContext<CommandDispatchSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        Bukkit.getOnlinePlayers()
            .asSequence()
            .map { it.name }
            .filter { it.lowercase().startsWith(remaining) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }
}

private class EnumArgumentResolver(
    private val enumType: Class<out Enum<*>>
) : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(enumType)

    override fun brigadierType(): ArgumentType<*> = StringArgumentType.word()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        val raw = StringArgumentType.getString(context, argumentName)
        return enumType.enumConstants.firstOrNull { value ->
            value.name.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException("Invalid enum value '$raw' for ${enumType.simpleName}")
    }

    override fun suggest(
        context: CommandContext<CommandDispatchSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        enumType.enumConstants
            .asSequence()
            .map { it.name.lowercase() }
            .filter { it.startsWith(remaining) }
            .forEach { builder.suggest(it) }
        return builder.buildFuture()
    }
}
