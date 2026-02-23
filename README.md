# CommandSystem

一个基于 Kotlin JVM 的库项目（library）。

## 环境要求

- JDK 17+（Gradle 9 运行需要）
- macOS / Linux / Windows

## 运行测试

```bash
./gradlew test
```

测试报告位置：

- `build/reports/tests/test/index.html`

## 构建 JAR

```bash
./gradlew jar
```

产物位置：

- `build/libs/CommandSystem-1.0-SNAPSHOT.jar`

如需同时执行完整构建（含测试）：

```bash
./gradlew build
```

## 发布到 Maven

### 1) 发布到本地 Maven 仓库

```bash
./gradlew publishToMavenLocal
```

发布后的坐标：

- `groupId`: `cc.vastsea.zrll`
- `artifactId`: `CommandSystem`
- `version`: `1.0-SNAPSHOT`

默认本地仓库路径：

- `~/.m2/repository/cc/vastsea/zrll/CommandSystem/1.0-SNAPSHOT/`

### 2) 发布到远端 Maven 仓库（示例）

当前项目已启用 `maven-publish`。如果要发布到私服（如 Nexus / Artifactory），可在 `build.gradle.kts` 的 `publishing` 下新增 `repositories`：

```kotlin
publishing {
    repositories {
        maven {
            name = "internal"
            url = uri("https://your-maven-repo/repository/maven-releases/")
            credentials {
                username = providers.gradleProperty("mavenUser").orNull
                password = providers.gradleProperty("mavenPassword").orNull
            }
        }
    }
}
```

然后在本机 `~/.gradle/gradle.properties` 配置账号：

```properties
mavenUser=your_user
mavenPassword=your_password
```

执行发布：

```bash
./gradlew publish
```

---

常用命令速查：

```bash
./gradlew test                # 运行测试
./gradlew jar                 # 仅打 jar
./gradlew build               # 完整构建
./gradlew publishToMavenLocal # 发布到本地 Maven
./gradlew publish             # 发布到配置的远端 Maven
```
