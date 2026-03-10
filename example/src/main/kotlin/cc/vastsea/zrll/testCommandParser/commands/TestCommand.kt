package cc.vastsea.zrll.testCommandParser.commands

import cc.vastsea.zrll.commandSystem.annontation.CommandHandler
import org.bukkit.command.CommandSender

class TestCommand {
    @CommandHandler(path = "get [optional:int] <required:string>", command = "testcommandparser", description = "Get something", permission = "testcommand.get", allowConsole = true)
    fun commandGet(sender: CommandSender, optional: Int?, required: String) {
        sender.sendMessage("You executed command get, optional=$optional, required=$required")
    }
}