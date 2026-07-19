# Account Manager — JOSM 多账号插件

Account Manager 让 JOSM 保存并切换多个 OSM 兼容平台账号。每个配置包含：

- 自定义配置名称
- 平台（OSM、OGF、OHM 或自定义）
- OSM API 地址
- OAuth 2.0 Access Token

内置平台地址：

| 平台 | API 地址 |
| --- | --- |
| OpenStreetMap (OSM) | `https://api.openstreetmap.org/api` |
| OpenGeofiction (OGF) | `https://opengeofiction.net/api` |
| OpenHistoricalMap (OHM) | `https://www.openhistoricalmap.org/api` |

## 功能

- 添加、编辑、删除任意数量的账号配置
- Token 字段全程遮罩，账号列表不显示 Token 内容
- Token 交由当前 JOSM 凭据管理器保存，普通配置元数据中不含 Token
- 一键激活配置，同时更新 JOSM API 地址、OAuth 2.0 认证方式和当前 Access Token
- 双击配置也可快速激活
- 自定义平台和 API 地址，支持其他 OSM Rails Port 实例

> 注意：JOSM 自带的默认凭据后端可能仍将敏感数据保存在本地偏好设置中。若安装了提供系统密钥环/加密存储的 JOSM 凭据插件，本插件会自动使用该后端。

## 使用

1. 安装插件并重启 JOSM。
2. 打开 **工具 → Account Manager...**。
3. 点击 **Add**，填写名称、平台和该平台签发的 OAuth 2.0 Access Token。
4. 选中配置后点击 **Activate**，或双击配置。
5. 此后的下载和上传会使用已激活平台与账号。

切换只影响后续 API 请求，不会迁移或改写当前已打开的数据图层。上传前应确认图层来源与当前激活平台一致，避免将一个平台的数据上传到另一个平台。

编辑已有配置时将 Token 留空会保留原 Token。删除配置会同时请求当前 JOSM 凭据管理器删除对应 Token。

## 开发环境

- JDK 21
- Gradle Wrapper 8.14.3
- JOSM 编译版本 19555

启动独立开发实例：

```powershell
.\gradlew.bat runJosm --offline
```

开发实例默认通过 `127.0.0.1:7890` HTTP 代理联网。可在 `gradle.properties` 中修改：

```properties
josmDevProxyEnabled=true
josmDevProxyHost=127.0.0.1
josmDevProxyPort=7890
```

临时禁用代理时无需修改文件：

```powershell
.\gradlew.bat runJosm --offline -PjosmDevProxyEnabled=false
```

这些设置只用于 `runJosm`/`debugJosm` 创建的开发实例，不会由发布插件强制写入其他用户现有的 JOSM 配置。

运行测试和构建：

```powershell
.\gradlew.bat clean check build --offline
```

生成发布包：

```powershell
.\gradlew.bat dist --offline
```

产物位于 `build/dist/account_manager.jar`。

## 数据存储

配置名称、平台、API 地址和内部 ID 保存在 JOSM 偏好设置的 `account-manager.profiles` 中。Token 使用独立的凭据键保存，并通过 JOSM `CredentialsManager` 读写；插件不会把 Token 放进账号配置列表。
