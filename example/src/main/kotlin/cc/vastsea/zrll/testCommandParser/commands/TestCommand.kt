package cc.vastsea.zrll.testCommandParser.commands

import cc.vastsea.zrll.commandSystem.annontation.CommandHandler
import org.bukkit.command.CommandSender

class TestCommand {
    enum class TcpType {
        FAST,
        SAFE,
        DEBUG
    }

    @CommandHandler(path = "/testcommandparser get [optional] <required>", description = "Get something", permission = "testcommand.get", allowConsole = true)
    fun commandGet(sender: CommandSender, optional: Int?, required: String) {
        sender.sendMessage("You executed command get, optional=$optional, required=$required")
    }

    @CommandHandler(path = "/tcp set <type>", description = "Set tcp type", permission = "testcommand.set", allowConsole = true)
    fun commandSet(sender: CommandSender, type: TcpType) {
        sender.sendMessage("You set tcp type to ${type.name.lowercase()}")
    }
}