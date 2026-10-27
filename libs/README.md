# 本地构建依赖

请将下列已验收版本的 JAR 放在本目录。依赖 JAR 不提交到 Git，也不打包进本附属。客户端实际运行时还须安装对应的 SBW 及其依赖。

| 项目 | 文件名 |
|---|---|
| Superb Warfare | `superbwarfare-0.8.9.1.jar`；兼容验收另需 `superbwarfare-0.8.9.2.jar` |
| Cloth Config | `cloth-config-neoforge-15.0.140-neoforge.jar` |
| Curios | `curios-neoforge-9.2.0+1.21.1.jar` |
| Flywheel | `flywheel-neoforge-1.21.1-1.0.6.jar` |
| GeckoLib | `geckolib-neoforge-1.21.1-4.7.5.jar` |
| Ponder | `ponder-neoforge-1.0.87+mc1.21.1.jar` |
| Rhino | `rhino-1.8.1-SNAPSHOT.jar` |
| SimpleBedrockModel | `SimpleBedrockModel-2.5.1.1-neoforge-mc1.21.1.jar` |

这些是本项目本地验收时使用的具体文件名，不代表推荐自动升级到其他版本。SBW 可从其[作者发布页](https://modrinth.com/mod/superb-warfare)获取，其他依赖请从对应项目或 SBW 指定的来源获取。

Kotlin For Forge 5.10.0 由 Gradle 下载；NeoForge 默认构建版本为 21.1.248，运行最低声明为 21.1.240。只构建默认版本需有 0.8.9.1；指定 `-PsbwVersion=0.8.9.2` 时需准备 0.8.9.2。
