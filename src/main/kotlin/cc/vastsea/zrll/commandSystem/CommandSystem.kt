package cc.vastsea.zrll.commandSystem

import cc.vastsea.zrll.commandSystem.annontation.CommandHandler
import cc.vastsea.zrll.commandSystem.modals.*
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod

class CommandSystem {
    private val roots = linkedMapOf<String, RootNode>()

    fun registerArgumentResolver(resolver: CommandArgumentResolver, replaceExisting: Boolean = true) {
        CommandArgumentResolvers.register(resolver, replaceExisting)
    }

    fun register(instance: Any) {
        instance::class.java.declaredMethods.forEach { method ->
            val annotation = method.getAnnotation(CommandHandler::class.java) ?: return@forEach
            registerAnnotatedMethod(instance, method, annotation)
        }
    }

    fun command(root: String, block: CommandDsl.() -> Unit) {
        val rootName = root.trim().lowercase()
        require(rootName.isNotBlank()) { "Root command cannot be blank" }

        val dsl = CommandDsl(rootName, mutableListOf(), emptyList(), emptyList())
        dsl.block()

        val rootNode = roots.getOrPut(rootName) { RootNode(rootName) }
        rootNode.children.addAll(dsl.takeChildren())
    }

    fun finalize(runnerSystem: CommandRunnerSystem) {
        roots.values.forEach { root ->
            val dispatcher = CommandDispatcher<CommandDispatchSource>()
            val rootLiteral = literal<CommandDispatchSource>(root.name)
            root.children.forEach { child -> buildNode(rootLiteral, child) }
            if (root.children.none { it is DslNode.LiteralNode && it.name.equals("help", ignoreCase = true) }) {
                rootLiteral.then(
                    literal<CommandDispatchSource>("help").executes { context ->
                        val sender = context.source.sender
                        sender.sendMessage("§e/${root.name} 命令帮助")
                        if (root.endpoints.isEmpty()) {
                            sender.sendMessage("§7暂无已注册 endpoint")
                        } else {
                            root.endpoints.forEach { endpoint ->
                                sender.sendMessage("§a路径: §f/${root.name}${endpoint.path}")
                                sender.sendMessage("§7参数: ${endpoint.params}")
                                sender.sendMessage("§7描述: ${endpoint.description.ifBlank { "(无)" }}")
                                sender.sendMessage("§7权限: ${endpoint.permission.ifBlank { "(无)" }}")
                            }
                        }
                        Command.SINGLE_SUCCESS
                    }
                )
            }
            dispatcher.register(rootLiteral)
            runnerSystem.addDispatcher(root.name, dispatcher)
        }
    }

    fun permissions(): List<Permission> {
        return roots.values
            .asSequence()
            .flatMap { it.endpoints }
            .map { it.permission }
            .filter { it.isNotBlank() }
            .distinct()
            .map { Permission(it) }
            .toList()
    }

    private fun registerAnnotatedMethod(instance: Any, method: Method, annotation: CommandHandler) {
        val tokens = parseAnnotatedPath(annotation.path)
        val hasExplicitCommand = annotation.command.isNotBlank()
        val commandName = annotation.command.ifBlank {
            (tokens.firstOrNull() as? PathToken.Literal)?.value.orEmpty()
        }.trim().lowercase()
        require(commandName.isNotBlank()) { "Command name cannot be empty for method ${method.name}" }

        val nodeTokens = if (hasExplicitCommand) {
            tokens
        } else {
            val first = tokens.firstOrNull()
            if (first is PathToken.Literal && first.value.equals(commandName, ignoreCase = true)) {
                tokens.drop(1)
            } else {
                tokens
            }
        }

        val pathArguments = nodeTokens.filterIsInstance<PathToken.Argument>()
        val methodArgumentTypes = resolveMethodArgumentTypes(method)
        require(pathArguments.size == methodArgumentTypes.size) {
            "Method ${method.name} expects ${methodArgumentTypes.size} non-sender parameters, but path defines ${pathArguments.size}"
        }

        val argumentSpecs = pathArguments.mapIndexed { index, argument ->
            val parameterType = boxed(methodArgumentTypes[index])
            argument.type?.let { declaredType ->
                require(isTypeCompatible(parameterType, declaredType)) {
                    "Type mismatch for ${method.name} parameter #$index: expected ${parameterType.name}, got ${declaredType.name}"
                }
            }
            requireArgumentResolver(parameterType)
            ArgumentSpec(argument.name, parameterType, argument.optional)
        }

        var resolvedArgIndex = 0
        val resolvedNodeTokens = nodeTokens.map { token ->
            if (token is PathToken.Argument) {
                val spec = argumentSpecs[resolvedArgIndex++]
                PathToken.Argument(token.name, token.optional, spec.type)
            } else {
                token
            }
        }

        val handler = createHandler(
            instance,
            method,
            argumentSpecs,
            annotation.permission,
            annotation.allowConsole
        )
        addPath(commandName, resolvedNodeTokens, handler)
        addEndpointDoc(commandName, resolvedNodeTokens, annotation.description, annotation.permission)
    }

    private fun addEndpointDoc(
        root: String,
        pathTokens: List<PathToken>,
        description: String,
        permission: String
    ) {
        val rootNode = roots.getOrPut(root) { RootNode(root) }
        val literalPath = pathTokens
            .filterIsInstance<PathToken.Literal>()
            .joinToString(" ") { it.value }
            .trim()
        val pathPart = if (literalPath.isBlank()) "" else " $literalPath"
        val paramsPart = pathTokens
            .filterIsInstance<PathToken.Argument>()
            .joinToString(" ") { argument ->
                val typeName = argument.type?.simpleName?.lowercase() ?: "unknown"
                if (argument.optional) "[${argument.name}:$typeName]" else "<${argument.name}:$typeName>"
            }
            .trim()

        rootNode.endpoints.add(
            EndpointDoc(
                path = pathPart,
                params = paramsPart.ifBlank { "(无)" },
                description = description,
                permission = permission
            )
        )
    }

    private fun addPath(root: String, pathTokens: List<PathToken>, handler: RealCommandHandler) {
        val rootNode = roots.getOrPut(root) { RootNode(root) }
        var children = rootNode.children

        pathTokens.forEach { token ->
            val existing = children.firstOrNull { node ->
                when (token) {
                    is PathToken.Literal -> node is DslNode.LiteralNode && node.name == token.value
                    is PathToken.Argument -> node is DslNode.ArgumentNode && node.name == token.name && node.type == token.type && node.optional == token.optional
                }
            }

            val next = existing ?: when (token) {
                is PathToken.Literal -> DslNode.LiteralNode(token.value)
                is PathToken.Argument -> {
                    val resolvedType = requireNotNull(token.type) {
                        "Argument type for '${token.name}' was not resolved"
                    }
                    DslNode.ArgumentNode(token.name, resolvedType, token.optional)
                }
            }.also { children.add(it) }

            children = next.children
        }

        children.add(DslNode.ExecuteNode(handler))
    }

    private fun buildNode(parent: ArgumentBuilder<CommandDispatchSource, *>, node: DslNode) {
        when (node) {
            is DslNode.LiteralNode -> {
                val literalNode = literal<CommandDispatchSource>(node.name)
                node.children.forEach { buildNode(literalNode, it) }
                parent.then(literalNode)
            }

            is DslNode.ArgumentNode -> {
                val resolver = requireArgumentResolver(node.type)
                val argumentNode = argumentNode(node.name, resolver)
                node.children.forEach { buildNode(argumentNode, it) }
                if (node.optional) {
                    node.children.forEach { buildNode(parent, it) }
                }
                parent.then(argumentNode)
            }

            is DslNode.ExecuteNode -> {
                parent.executes { context ->
                    node.handler.invoke(context)
                    Command.SINGLE_SUCCESS
                }
            }
        }
    }

    private fun createHandler(
        instance: Any,
        method: Method,
        argumentSpecs: List<ArgumentSpec>,
        permission: String = "",
        allowConsole: Boolean = true
    ): RealCommandHandler {
        method.isAccessible = true
        val valueParameters = method.parameters
        val senderIndex = valueParameters.indexOfFirst { parameter ->
            CommandSender::class.java.isAssignableFrom(parameter.type)
        }

        val argIndexes = valueParameters.indices.filter { it != senderIndex }
        require(argIndexes.size == argumentSpecs.size) {
            "Method ${method.name} expects ${argIndexes.size} non-sender parameters, but path defines ${argumentSpecs.size}"
        }

        val bindings = mutableListOf<ParameterBinding>()
        if (senderIndex >= 0) {
            bindings.add(ParameterBinding.Sender(senderIndex))
        }

        argIndexes.forEachIndexed { argOrder, parameterIndex ->
            val parameterType = valueParameters[parameterIndex].type
            val spec = argumentSpecs[argOrder]
            require(isTypeCompatible(parameterType, spec.type)) {
                "Type mismatch for ${method.name} parameter #$parameterIndex: expected ${parameterType.name}, got ${spec.type.name}"
            }
            bindings.add(ParameterBinding.Argument(parameterIndex, spec))
        }

        return RealCommandHandler(instance, method, bindings, permission, allowConsole)
    }

    private fun resolveMethodArgumentTypes(method: Method): List<Class<*>> {
        val valueParameters = method.parameters
        val senderIndex = valueParameters.indexOfFirst { parameter ->
            CommandSender::class.java.isAssignableFrom(parameter.type)
        }
        return valueParameters.indices
            .filter { it != senderIndex }
            .map { index -> valueParameters[index].type }
    }

    private fun parseAnnotatedPath(path: String): List<PathToken> {
        val tokens = path.trim().split(" ").filter { it.isNotBlank() }
        return tokens.map { token ->
            val normalized = if (token.startsWith("/")) token.substring(1) else token
            require(normalized.isNotBlank()) { "Invalid path token: $token" }
            when {
                normalized.startsWith("[") && normalized.endsWith("]") -> {
                    parseArgumentToken(normalized.substring(1, normalized.length - 1), optional = true)
                }

                normalized.startsWith("<") && normalized.endsWith(">") -> {
                    parseArgumentToken(normalized.substring(1, normalized.length - 1), optional = false)
                }

                normalized.contains(":") -> {
                    parseArgumentToken(normalized, optional = false)
                }

                else -> PathToken.Literal(normalized)
            }
        }
    }

    private fun parseArgumentToken(raw: String, optional: Boolean): PathToken.Argument {
        val parts = raw.split(":")
        require(parts.size in 1..2) { "Invalid argument token: $raw" }

        val name = parts[0].trim()
        require(name.isNotBlank()) { "Argument name cannot be blank: $raw" }

        val type = parts.getOrNull(1)?.trim()?.lowercase()?.let(::parseType)
        return PathToken.Argument(name, optional, type)
    }

    private fun parseType(typeName: String): Class<*> = when (typeName) {
        "string", "str" -> String::class.java
        "int", "integer" -> Int::class.javaObjectType
        "long" -> Long::class.javaObjectType
        "double" -> Double::class.javaObjectType
        "player" -> org.bukkit.entity.Player::class.java
        "bool", "boolean" -> Boolean::class.javaObjectType
        else -> throw IllegalArgumentException("Unsupported argument type: $typeName")
    }

    @Suppress("UNCHECKED_CAST")
    private fun argumentNode(
        name: String,
        resolver: CommandArgumentResolver
    ): RequiredArgumentBuilder<CommandDispatchSource, Any> {
        val brigadierType = resolver.brigadierType() as ArgumentType<Any>
        return argument<CommandDispatchSource, Any>(name, brigadierType)
            .suggests { context, builder -> resolver.suggest(context, builder) }
    }

    private fun requireArgumentResolver(type: Class<*>): CommandArgumentResolver {
        return CommandArgumentResolvers.find(type)
    }

    private fun isTypeCompatible(parameterType: Class<*>, specType: Class<*>): Boolean {
        return boxed(parameterType) == boxed(specType)
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        Long::class.javaPrimitiveType -> Long::class.javaObjectType
        Double::class.javaPrimitiveType -> Double::class.javaObjectType
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        else -> type
    }

    inner class CommandDsl internal constructor(
        private val rootName: String,
        private val children: MutableList<DslNode>,
        private val pathArguments: List<ArgumentSpec>,
        private val pathTokens: List<PathToken>
    ) {
        internal fun takeChildren(): List<DslNode> = children

        fun literal(name: String, block: CommandDsl.() -> Unit) {
            val node = DslNode.LiteralNode(name)
            CommandDsl(rootName, node.children, pathArguments, pathTokens + PathToken.Literal(name)).block()
            children.add(node)
        }

        fun argument(
            name: String,
            type: KClass<*>,
            optional: Boolean = false,
            block: CommandDsl.() -> Unit
        ) {
            val spec = ArgumentSpec(name, type.java, optional)
            requireArgumentResolver(spec.type)
            val node = DslNode.ArgumentNode(name, spec.type, optional)
            CommandDsl(
                rootName,
                node.children,
                pathArguments + spec,
                pathTokens + PathToken.Argument(name, optional, spec.type)
            ).block()
            children.add(node)
        }

        fun executes(handler: Any) {
            val (instance, method) = when (handler) {
                is KFunction<*> -> {
                    val instanceParam = handler.parameters.firstOrNull {
                        it.kind == KParameter.Kind.INSTANCE || it.kind == KParameter.Kind.EXTENSION_RECEIVER
                    }
                    if (instanceParam != null) {
                        // Unbound TestClass::method, needs to create instance and find java method
                        val receiverClass = (instanceParam.type.classifier as KClass<*>).java
                        val receiverInstance = try {
                            receiverClass.getDeclaredConstructor().newInstance()
                        } catch (e: Exception) {
                            throw IllegalArgumentException(
                                "Unbound function reference requires ${receiverClass.name} to have a no-arg constructor. " +
                                "Use a bound reference (instance::method) instead.", e
                            )
                        }
                        val javaMethod = requireNotNull(handler.javaMethod) {
                            "Cannot get underlying Java method from KFunction '${handler.name}'"
                        }
                        Pair(receiverInstance as Any, javaMethod)
                    } else {
                        // Bound instance::method, non-bridge invoke
                        val invokeMethod = handler::class.java.methods
                            .filter { it.name == "invoke" && !it.isBridge && !it.isSynthetic }
                            .maxByOrNull { it.parameterCount }
                            ?: throw IllegalArgumentException(
                                "Cannot resolve invoke method from bound function reference"
                            )
                        Pair(handler as Any, invokeMethod)
                    }
                }
                else -> {
                    val invokeMethod = resolveInvokeMethod(handler)
                    Pair(handler, invokeMethod)
                }
            }
            val registered = createHandler(instance, method, pathArguments, "", true)
            children.add(DslNode.ExecuteNode(registered))
            addEndpointDoc(rootName, pathTokens, "", "")
        }
    }

    private fun resolveInvokeMethod(handler: Any): Method {
        return handler::class.java.methods
            .filter { it.name == "invoke" }
            .maxByOrNull { it.parameterCount }
            ?: throw IllegalArgumentException("Cannot resolve invoke method from handler reference")
    }
}
