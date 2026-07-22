# Account Manager — Multi-account Plugin for JOSM

[English](#english) | [中文](#中文)

<a id="english"></a>

## English

Account Manager lets JOSM save and switch between multiple accounts for OSM-compatible platforms. Each profile contains:

- A custom profile name
- A platform (OSM, OSMDEV, OGF, OHM, or a custom platform)
- An OSM API URL
- An authentication method (OAuth 2.0 or username and password)

Built-in platform URLs:

| Platform | API URL |
| --- | --- |
| OpenStreetMap (OSM) | `https://api.openstreetmap.org/api` |
| OpenStreetMap Dev (OSMDEV) | `https://master.apis.dev.openstreetmap.org/api` |
| OpenGeofiction (OGF) | `https://opengeofiction.net/api` |
| OpenHistoricalMap (OHM) | `https://www.openhistoricalmap.org/api` |

### Features

- Add, edit, and delete any number of account profiles
- Keep token and password fields masked at all times, with no secrets displayed in the account list
- Store tokens, usernames, and passwords through the active JOSM credentials manager, separate from non-secret profile metadata
- Validate the API URL and selected credentials by requesting `/0.6/user/details` before saving or activating an account
- Support fully automatic JOSM-style OAuth 2.0 authorization by opening a browser and retrieving the token through a local callback
- Automatically sync tokens authorized through JOSM's native **OSM Server** settings to Account Manager
- Support manual username and password entry, verified and activated with HTTP Basic Authentication
- Activate a profile with one click, updating the JOSM API URL, authentication method, and active credentials together
- Activate a profile quickly by double-clicking it
- Support custom platforms and API URLs for other OSM Rails Port instances

> Note: JOSM's default credentials backend may still store sensitive data in local preferences. If a JOSM credentials plugin providing a system keychain or encrypted storage is installed, Account Manager automatically uses that backend.

### Usage

1. Install the plugin and restart JOSM.
2. Open **Edit → Preferences → OSM Server**, then click **Manage accounts...** in the **Account Manager** section.
3. Click **Add**, enter a name and platform, and choose an authentication method:
   - **OAuth 2.0**: Click **Authorize now (fully automatic)** to authorize in your browser, or enter a token manually.
   - **Username and password**: Enter the account credentials and let the plugin verify them directly against the API.
4. Select the profile and click **Activate**, or double-click the profile.
5. Subsequent downloads and uploads will use the active platform and account.

Switching profiles only affects subsequent API requests. It does not migrate or modify any currently open data layers. Before uploading, confirm that the layer source matches the active platform to avoid uploading data from one platform to another.

When editing an existing profile, leaving the password or token blank preserves the saved credential. Deleting a profile also asks the active JOSM credentials manager to remove its associated credentials.

### Development

- JDK 21
- Gradle Wrapper 8.14.3
- JOSM build version 19555

Start a standalone development instance:

```powershell
.\gradlew.bat runJosm --offline
```

To use a proxy, configure the development instance in the local `proxy.properties` file, which is excluded from Git. Copy the example file before first use:

```powershell
Copy-Item proxy.properties.example proxy.properties
```

Then add your local settings to `proxy.properties`:

```properties
josmDevProxyEnabled=true
josmDevProxyHost=127.0.0.1
josmDevProxyPort=7890
```

The proxy is disabled by default when `proxy.properties` does not exist. You can also disable it temporarily without editing the file:

```powershell
.\gradlew.bat runJosm --offline -PjosmDevProxyEnabled=false
```

These settings apply only to development instances created by `runJosm`/`debugJosm`. The released plugin does not force them into existing JOSM configurations.

Run tests and build the project:

```powershell
.\gradlew.bat clean check build --offline
```

Create the distribution package:

```powershell
.\gradlew.bat dist --offline
```

The artifact is generated at `build/dist/account_manager.jar`.

### Data storage

Profile names, platforms, API URLs, authentication methods, and internal IDs are stored in the JOSM preference key `account-manager.profiles`. Tokens, usernames, and passwords are stored under separate credential keys and accessed through JOSM's `CredentialsManager`; the plugin never places secrets in the account profile list.

---

<a id="中文"></a>

## 中文

### Account Manager — JOSM 多账号插件

Account Manager 让 JOSM 保存并切换多个 OSM 兼容平台账号。每个配置包含：

- 自定义配置名称
- 平台（OSM、OSMDEV、OGF、OHM 或自定义）
- OSM API 地址
- 认证方式（OAuth 2.0 或账号密码）

内置平台地址：

| 平台 | API 地址 |
| --- | --- |
| OpenStreetMap (OSM) | `https://api.openstreetmap.org/api` |
| OpenStreetMap Dev (OSMDEV) | `https://master.apis.dev.openstreetmap.org/api` |
| OpenGeofiction (OGF) | `https://opengeofiction.net/api` |
| OpenHistoricalMap (OHM) | `https://www.openhistoricalmap.org/api` |

### 功能

- 添加、编辑、删除任意数量的账号配置
- Token 和密码字段全程遮罩，账号列表不显示秘密内容
- Token、用户名和密码交由当前 JOSM 凭据管理器保存，普通配置元数据中不含秘密内容
- 保存和激活账号前自动请求 `/0.6/user/details`，验证 API 地址与所选凭据是否可用
- 支持 JOSM 式 OAuth 2.0 全自动授权：自动打开浏览器并通过本地回调获取 Token
- 在 JOSM 原生“OSM 服务器”设置中完成授权后，Token 会自动同步到 Account Manager
- 支持手动输入账号密码，并使用 HTTP Basic Authentication 验证和激活
- 一键激活配置，同时更新 JOSM API 地址、认证方式和当前凭据
- 双击配置也可快速激活
- 自定义平台和 API 地址，支持其他 OSM Rails Port 实例

> 注意：JOSM 自带的默认凭据后端可能仍将敏感数据保存在本地偏好设置中。若安装了提供系统密钥环/加密存储的 JOSM 凭据插件，本插件会自动使用该后端。

### 使用

1. 安装插件并重启 JOSM。
2. 打开 **编辑 → 首选项 → OSM 服务器**，在 **Account Manager** 区域点击 **Manage accounts...**。
3. 点击 **Add**，填写名称和平台，并选择认证方式：
   - **OAuth 2.0**：点击 **Authorize now (fully automatic)** 在浏览器中授权，也可以手工填写 Token。
   - **Username and password**：填写账号和密码，由插件直接向 API 验证。
4. 选中配置后点击 **Activate**，或双击配置。
5. 此后的下载和上传会使用已激活平台与账号。

切换只影响后续 API 请求，不会迁移或改写当前已打开的数据图层。上传前应确认图层来源与当前激活平台一致，避免将一个平台的数据上传到另一个平台。

编辑已有配置时将密码或 Token 留空会保留原凭据。删除配置会同时请求当前 JOSM 凭据管理器删除对应凭据。

### 开发环境

- JDK 21
- Gradle Wrapper 8.14.3
- JOSM 编译版本 19555

启动独立开发实例：

```powershell
.\gradlew.bat runJosm --offline
```

如需使用代理，开发实例的代理由本地 `proxy.properties` 配置，该文件不会进入 Git。首次使用时复制示例文件：

```powershell
Copy-Item proxy.properties.example proxy.properties
```

然后在 `proxy.properties` 中填写本机配置：

```properties
josmDevProxyEnabled=true
josmDevProxyHost=127.0.0.1
josmDevProxyPort=7890
```

未创建 `proxy.properties` 时代理默认关闭。临时禁用代理时也无需修改文件：

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

### 数据存储

配置名称、平台、API 地址、认证方式和内部 ID 保存在 JOSM 偏好设置的 `account-manager.profiles` 中。Token、用户名和密码使用独立的凭据键保存，并通过 JOSM `CredentialsManager` 读写；插件不会把秘密内容放进账号配置列表。
