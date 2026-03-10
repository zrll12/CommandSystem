package cc.vastsea.zrll.testCommandParser

import cc.vastsea.zrll.commandSystem.CommandRunnerSystem
import cc.vastsea.zrll.commandSystem.CommandSystem
import cc.vastsea.zrll.testCommandParser.commands.TestCommand
import com.sun.beans.introspect.PropertyInfo
import org.bukkit.plugin.java.JavaPlugin

class TestCommandParser : JavaPlugin() {
    private val commandRunnerSystem = CommandRunnerSystem()

    override fun onEnable() {
        val commandSystem = CommandSystem()
        commandSystem.register(TestCommand())
        /*
        * You can use the code below to register command,
        * in that way, you don't need to add @CommandHandler annotation:
        *
        * commandSystem.command("testcommandparser") {
        *     literal("get") {
        *         argument("optional", Int::class, true) {
        *             argument("required", String::class) {
        *                 executes(TestCommand::commandGet)
        *             }
        *         }
        *     }
        * }
        */

        commandSystem.finalize(commandRunnerSystem)
        getCommand("testcommandparser")?.let { pluginCommand ->
            pluginCommand.setExecutor(commandRunnerSystem)
            pluginCommand.tabCompleter = commandRunnerSystem
        }
        logger.info("TestCommandParser enabled!")
    }

    override fun onDisable() {
        logger.info("TestCommandParser disabled!")
    }
}
