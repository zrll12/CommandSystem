# CommandSystem

CommandSystem 是一个面向 Bukkit/Spigot/Paper 插件开发的 Kotlin 命令框架，底层基于 Brigadier。

它提供了两种注册方式：

- 注解方式：快速把方法映射为命令。
- DSL 方式：以树结构声明命令节点。

同时内置：

- 参数类型解析（`String` / `Int` / `Long` / `Double` / `Boolean` / `Player` / `Enums` / 自定义类型）
- 注解路径支持 `/root sub [optional] <required>`，参数类型从函数签名自动推断
- 权限自动收集（可直接注册到 Bukkit `Permission`）
- `CommandRunnerSystem` 命令执行与补全
- 语法错误定位到具体字符，并只提示当前命令分支的可用用法
- 可注入 i18n 文案函数
- 支持注册自定义 `CommandArgumentResolver`（让用户自定义 class 参与参数解析与补全）

## 适用场景

- 希望避免手写大量 `onCommand` / `onTabComplete` 分支逻辑
- 需要统一管理多层命令路径与参数
- 想同时支持注解注册与代码 DSL 注册

## 环境要求

- JDK 17+
- Kotlin/JVM 项目
- Bukkit/Spigot/Paper 插件环境

## 快速开始

### 1) 定义命令处理器（注解方式）

```kotlin
import cc.vastsea.zrll.commandSystem.annontation.CommandHandler
import org.bukkit.command.CommandSender

class TestCommand {
    @CommandHandler(
        path = "/testcommandparser get [optional] <required>",
        description = "Get something",
        permission = "testcommand.get",
        allowConsole = true
    )
    fun commandGet(sender: CommandSender, optional: Int?, required: String) {
        sender.sendMessage("optional=$optional, required=$required")
    }
}

// optional -> Int?，required -> String，均由函数参数类型自动匹配
```

### 2) 在插件启动时注册

```kotlin
import cc.vastsea.zrll.commandSystem.CommandRunnerSystem
import cc.vastsea.zrll.commandSystem.CommandSystem
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    private val commandRunnerSystem = CommandRunnerSystem()

    override fun onEnable() {
        val commandSystem = CommandSystem()
        commandSystem.register(TestCommand())
        commandSystem.finalize(commandRunnerSystem)

        commandSystem.permissions().forEach { Bukkit.getPluginManager().addPermission(it) }

        getCommand("testcommandparser")?.let { pluginCommand ->
            pluginCommand.setExecutor(commandRunnerSystem)
            pluginCommand.tabCompleter = commandRunnerSystem
        }
    }
}
```

## DSL 注册示例

```kotlin
commandSystem.command("testcommandparser") {
    literal("get") {
        argument("optional", Int::class, true) {
            argument("required", String::class) {
                executes(testCommand::commandGet)
            }
        }
    }
}
```

## 自定义参数类型

你可以让任意自定义 class 作为命令参数类型，只需实现并注册 `CommandArgumentResolver`。

```kotlin
import cc.vastsea.zrll.commandSystem.CommandSystem
import cc.vastsea.zrll.commandSystem.modals.CommandArgumentResolver
import cc.vastsea.zrll.commandSystem.modals.CommandDispatchSource
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext

data class Region(val name: String)

class RegionResolver : CommandArgumentResolver {
    override val supportedTypes: Set<Class<*>> = setOf(Region::class.java)

    override fun brigadierType() = StringArgumentType.word()

    override fun parse(context: CommandContext<CommandDispatchSource>, argumentName: String): Any {
        val raw = StringArgumentType.getString(context, argumentName)
        return Region(raw)
    }
}

val commandSystem = CommandSystem()
commandSystem.registerArgumentResolver(RegionResolver())
```

## i18n 接入

`CommandRunnerSystem` 支持注入自定义文案函数：

```kotlin
val runner = CommandRunnerSystem { key, placeholders ->
    val template = myI18n.get(key) // 你的 i18n 系统
    placeholders?.entries?.fold(template) { result, (k, v) ->
        result.replace("{$k}", v)
    } ?: template
}
```

函数签名：

```kotlin
get(key: String, placeholders: Map<String, String>? = null): String
```

如果不注入，框架会使用内置英文默认文案。

语法错误会使用以下 key：

- `command.syntax.invalid`：参数 `input`
- `command.syntax.location`：参数 `cursor`、`context`、`pointer`
- `command.syntax.available`
- `command.syntax.usage`：参数 `usage`
- `command.syntax.check`：参数 `label`
- `command.execute.error`：参数 `message`

## 项目中的可运行示例

完整示例插件在 `example` 模块：

- `example/src/main/kotlin/cc/vastsea/zrll/testCommandParser/TestCommandParser.kt`
- `example/src/main/kotlin/cc/vastsea/zrll/testCommandParser/commands/TestCommand.kt`

## 开发命令

```bash
./gradlew test
./gradlew build
./gradlew :example:compileKotlin
```
