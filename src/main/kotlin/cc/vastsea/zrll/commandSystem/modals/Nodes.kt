package cc.vastsea.zrll.commandSystem.modals

import cc.vastsea.zrll.commandSystem.RealCommandHandler

internal data class RootNode(
    val name: String,
    val children: MutableList<DslNode> = mutableListOf(),
    val endpoints: MutableList<EndpointDoc> = mutableListOf()
)

internal data class EndpointDoc(
    val path: String,
    val arguments: List<EndpointArgumentDoc>,
    val description: String,
    val permission: String
)

internal data class EndpointArgumentDoc(
    val name: String,
    val type: Class<*>,
    val optional: Boolean,
)

internal sealed class DslNode {
    abstract val children: MutableList<DslNode>

    data class LiteralNode(
        val name: String,
        override val children: MutableList<DslNode> = mutableListOf()
    ) : DslNode()

    data class ArgumentNode(
        val name: String,
        val type: Class<*>,
        val optional: Boolean,
        override val children: MutableList<DslNode> = mutableListOf()
    ) : DslNode()

    data class ExecuteNode(
        val handler: RealCommandHandler,
        override val children: MutableList<DslNode> = mutableListOf()
    ) : DslNode()
}

internal sealed class PathToken {
    data class Literal(val value: String) : PathToken()
    data class Argument(val name: String, val optional: Boolean, val type: Class<*>? = null) : PathToken()
}

internal data class ArgumentSpec(
    val name: String,
    val type: Class<*>,
    val optional: Boolean
)

internal sealed class ParameterBinding {
    data class Sender(val parameterIndex: Int) : ParameterBinding()
    data class Argument(val parameterIndex: Int, val spec: ArgumentSpec) : ParameterBinding()
}
