# Account Manager — JOSM 插件最小实例

这是一个可被 JOSM 加载的最小插件。加载后，它会在 **工具** 菜单中添加
**Account Manager: Hello**，点击后显示运行提示。

## 环境与本地缓存

- JDK 21
- Gradle Wrapper 固定为 8.14.3
- JOSM 编译版本固定为 19555
- Gradle、官方 JOSM Gradle 插件和 JOSM 19555 的缓存均保存在项目内的
  `.gradle-user-home/`

Wrapper 默认使用项目内缓存，不依赖用户目录中的 `~/.gradle`。该缓存目录属于本机构建
数据，已被 `.gitignore` 排除；如果要把项目复制到另一台电脑并保持完全离线，需要同时
复制 `.gradle-user-home`。

## 一条命令启动开发实例

```powershell
.\gradlew.bat runJosm --offline
```

该命令会自动编译插件并启动使用临时配置目录的全新 JOSM。修改代码后，关闭 JOSM，
再次执行同一条命令即可。

构建已为 Java 21 自动加入 JOSM 19555 所需的 `--add-exports` JVM 参数，无需手工输入。
命令行的 `--offline` 只禁止 Gradle 下载构建依赖。JOSM 本身保持联网，并通过
`127.0.0.1:7890` HTTP 代理访问 OSM API、影像列表等网络资源。

## 构建

```powershell
.\gradlew.bat clean build --offline
```

生成发布用插件：

```powershell
.\gradlew.bat dist --offline
```

发布产物位于：

```text
build/dist/account_manager.jar
```

## 验证

启动开发实例后，点击 **工具 → Account Manager: Hello**。看到
“Account Manager plugin is running.” 即表示插件成功加载。

## 项目结构

```text
src/main/java/com/example/josm/accountmanager/
├── AccountManagerPlugin.java  # 插件入口与生命周期
└── HelloAction.java           # 最小可见功能
```

后续开发时，建议把 `com.example` 改成你自己的反向域名包名。
