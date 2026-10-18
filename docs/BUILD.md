# Android 构建说明

## 0. 环境要求

| 项 | 版本 | 备注 |
|---|---|---|
| Android Studio | Ladybug (2024.2) 或更新 | 能开 AGP 8.7 就行 |
| JDK | 17 | Studio 自带 JBR 也行；本机 `C:\Program Files\Java\jdk 17` |
| Android SDK | Platform 35 + Build Tools 35.0.0 | Studio 首次 Sync 会提示装 |
| 设备 | Android 8.0 (API 26) 及以上 | minSdk 26 |

## 1. 用 Android Studio 打开

1. `File → Open` 选 **`D:\dsh_work\EA\android`**（注意是 android 这一层，不是仓库根）。
2. 等 Gradle Sync：项目自带 wrapper（`gradle/wrapper/gradle wrapper.jar`），发行包地址已指向腾讯云镜像，国内可用。
     若你的网络访问 `services.gradle.org` 更快，把 `gradle/wrapper/gradle wrapper.properties` 里的
     `distributionUrl` 改回 `https\://services.gradle.org/distributions/gradle 8.9 bin.zip` 即可。
3. 若提示缺 SDK：`SDK Manager` 里勾 Android 15 (API 35) + Build Tools 35.0.0。
4. Run ▶ 直接装机；或 `Build → Build Bundle(s)/APK(s) → Build APK(s)`。

产物路径：`android/app/build/outputs/apk/debug/app debug.apk`

## 2. 命令行构建（可选）

```powershell
$env:ANDROID_HOME = "D:\dsh_work\_android_sdk"   # 换成你的 SDK 路径
cd D:\dsh_work\EA\android
.\gradlew.bat assembleDebug
# 或
.\gradlew.bat assembleRelease
```

仓库里没有放 `local.properties`（不同机器 SDK 路径不同）。命令行构建时需要自己在 `android/` 下建一个：

```properties
sdk.dir=D\:\\dsh_work\\_android_sdk
```

或在环境变量里设 `ANDROID_HOME`（Gradle 会自动读）。

## 3. 签名（要装到别人手机上时）

Debug APK 能装但共享不方便。正式分发走这个流程：

```powershell
& "$env:JAVA_HOME\bin\keytool.exe"  genkeypair  v  keystore my release.jks  keyalg RSA  keysize 2048  validity 10000  alias examreader
```

在 `android/app/build.gradle.kts` 里加 `signingConfigs`（或用 Studio 的 `Build → Generate Signed Bundle/APK` 向导，不必改代码）。

> 只自己用的话，Debug APK 足够，不必签名。

## 4. 换成你自己的试卷

App 的种子数据是 `android/app/src/main/assets/seed.epak`。

```powershell
cd D:\dsh_work\EA\framework
node bin/exam.mjs build ..\content\你的试卷\paper.json   target android
# 会自动覆盖 android/app/src/main/assets/seed.epak
```

或者不重新打包：App 装好后直接「导入试卷包」选择任意 `.epak`。**多份试卷可以共存**，在试卷库里切换。

只想换内置示例、不留德阳卷：删掉 `assets/seed.epak` 后重新构建，App 首次启动会是空库。

## 5. 构建验证状态（实测记录）

**已实测通过**（Windows + Gradle 8.13 + AGP 8.7.3 + JDK 17 + Android SDK Platform 35 / Build-Tools 35.0.0）：

```
> Task :app:kspDebugKotlin          # Room 生成 DAO/数据库实现
> Task :app:compileDebugKotlin
> Task :app:packageDebug
BUILD SUCCESSFUL in 51s
警告：0
产物：android/app/build/outputs/apk/debug/app-debug.apk（约 11 MB）
```

安装与启动也已验证：

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.examreader/.MainActivity
adb logcat -b crash -d        # 无崩溃记录
```

> 注意：本仓库 `local.properties` 里 `sdk.dir` 指向 `D:\asdk`（本机 SDK），换机器请改成你自己的路径，或删掉该文件用 `ANDROID_HOME`。

### 命令行走完整构建

```powershell
$env:ANDROID_HOME = "D:\asdk"
cd D:\dsh_work\EA\android
.\gradlew.bat clean assembleDebug
```

## 6. 常见问题

| 现象 | 原因 / 解决 |
|---|---|
| `SDK location not found` | 没配 `ANDROID_HOME` 或没建 `local.properties`，见第 2 节 |
| `dl.google.com` 超时 | Gradle 需要访问 Google Maven；挂代理，或在 `settings.gradle.kts` 里加国内镜像源 |
| `Unsupported class file major version` | JDK 版本不对，项目要 JDK 17（`Gradle JDK` 设成 17） |
| Sync 卡在下载 Gradle | wrapper 已指向腾讯云镜像；再卡就手动改 `distributionUrl` |
| 首次启动看不到试卷 | 检查 `assets/seed.epak` 是否存在；或用「导入试卷包」手动导入 |
| 导入报「不是有效的 .epak」 | 用 `node framework/bin/exam.mjs inspect <file>` 先体检；包必须是 framework 编译产物 |
| 思维导图不显示 | 该篇没有 `mindmaps/<id>.svg`；重新 `exam build` 时会自动带上 |
| Room 升级后闪退 | 当前 `version = 1` + `fallbackToDestructiveMigration()`（升级会清库）。正式迭代前请写 Migration |

## 7. 打包体积参考

| 组成 | 大小 |
|---|---|
| Compose + Room + Coil 运行库（debug，未混淆） | 约 6–9 MB |
| `assets/seed.epak`（德阳卷） | 339 KB |
| 其他内容包 | 取决于卷子大小（纯文本，通常 < 1 MB） |

比旧版 3.52 MB 的 HTML 大，是因为原生运行库；但**不再包含 3.07 MB 音频**，且多份试卷共享同一套代码。

## 8. 联网相关（扫描 / 翻译）

- 权限：`INTERNET`、`ACCESS_NETWORK_STATE`（联网）；`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`（后台扫描）；`POST_NOTIFICATIONS`（完成提醒）。不需要存储/相机权限（选图走系统文件选择器）。
- 老师需要在 App 内填**两个 Key**：千问 API Key（扫描）+ DeepSeek API Key（解析），见 [AI-SETUP.md](AI-SETUP.md)。
  未配置时：App 其余功能完全可用，只有「扫描试卷图片出卷」与「译」按钮会提示去配置。
  如果企业环境要求「纯离线」，可把这两个按钮入口隐藏掉（删 `ScanScreen` 路由与 `EditorScreen` 的「译」按钮即可），数据包仍可通过 `.epak` 导入。
