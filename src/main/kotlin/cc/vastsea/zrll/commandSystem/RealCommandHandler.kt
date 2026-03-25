package cc.vastsea.zrll.commandSystem

import cc.vastsea.zrll.commandSystem.modals.ArgumentSpec
import cc.vastsea.zrll.commandSystem.modals.CommandArgumentResolvers
import cc.vastsea.zrll.commandSystem.modals.CommandDispatchSource
import cc.vastsea.zrll.commandSystem.modals.ParameterBinding
import com.mojang.brigadier.context.CommandContext
import org.bukkit.entity.Player
import java.lang.reflect.Method

internal class RealCommandHandler(
    private val instance: Any,
    private val method: Method,
    private val bindings: List<ParameterBinding>,
    private val permission: String,
    private val allowConsole: Boolean
) {
    fun invoke(context: CommandContext<CommandDispatchSource>) {
        val sender = context.source.sender

        if (sender !is Player && !allowConsole) {
            sender.sendMessage("§c该命令不允许控制台执行。")
            return
        }

        if (permission.isNotBlank() && sender is Player && !sender.hasPermission(permission)) {
            sender.sendMessage("§c你没有权限执行此命令。需要权限: $permission")
            return
        }

        val args = arrayOfNulls<Any?>(method.parameterCount)
        bindings.forEach { binding ->
            when (binding) {
                is ParameterBinding.Sender -> {
                    args[binding.parameterIndex] = sender
                }

                is ParameterBinding.Argument -> {
                    val value = extractArgument(context, binding.spec)
                    if (value == null && !binding.spec.optional) {
                        throw IllegalArgumentException("Missing required argument: ${binding.spec.name}")
                    }
                    args[binding.parameterIndex] = value
                }
            }
        }

        method.invoke(instance, *args)
    }

    private fun extractArgument(context: CommandContext<CommandDispatchSource>, spec: ArgumentSpec): Any? {
        return try {
            CommandArgumentResolvers.find(spec.type).parse(context, spec.name)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
