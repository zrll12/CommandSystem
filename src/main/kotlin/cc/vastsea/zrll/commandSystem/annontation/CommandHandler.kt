package cc.vastsea.zrll.commandSystem.annontation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CommandHandler(
    val path: String,
    val command: String = "",
    val description: String = "",
    val permission: String = "",
    val allowConsole: Boolean = false
)