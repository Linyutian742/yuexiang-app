# WebViewShell · Android 网页套壳工程

零第三方依赖的 Android 原生 WebView 套壳工程，**已按 `yuexiang.dpdns.org` 配置好**：全屏展示网页、顶部加载进度条、返回键站内回退、加载失败重试页、外链交给系统浏览器，并针对 DDNS 站点做了 https→http 自动回退。

> 📱 想直接拿到能装到手机的 APK：看 **[安装到手机.md](安装到手机.md)**，里面是用 GitHub Actions 云端出包的三步流程，不需要本地装 Android Studio。

## 一、环境与版本

| 项目 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.6.1 |
| Gradle | 8.7（wrapper 已内置） |
| Kotlin | 2.0.21 |
| JDK | 17 |
| compileSdk / targetSdk | 35 |
| minSdk | 24（Android 7.0+） |
| 建议 IDE | Android Studio Ladybug（2024.2）或更新 |

工程只依赖 Android SDK 与 Kotlin 标准库，没有引入 AndroidX / Material / 任何 WebView 三方库。

## 二、目录结构

```
WebViewShell/
├── .github/workflows/build-apk.yml   # ★ 云端一键编译可安装 APK
├── 安装到手机.md                     # ★ 手机安装与出包指南
├── settings.gradle
├── build.gradle                      # 插件版本
├── gradle.properties
├── gradlew / gradlew.bat              # Gradle Wrapper（含 jar，可直接执行）
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── icon-source/yuexiang.jpg           # 图标源图（已去掉 .txt 后缀，实际为 JPEG）
├── app/
│   ├── build.gradle                  # ★ 套壳地址在这里配置
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/webviewshell/
│       │   ├── MainActivity.kt          # WebView 配置、生命周期、进度条、错误页、协议回退
│       │   └── ShellWebViewClient.kt    # 链接白名单、SSL 与加载错误处理
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/                  # 字符串、颜色、浅色主题
│           ├── values-night/            # 深色模式
│           ├── xml/network_security_config.xml
│           ├── drawable/                # 旧矢量占位图（已不再被引用，可删）
│           ├── mipmap-{mdpi~xxxhdpi}/   # 用户徽标 PNG：普通 / 圆形 / 自适应前景 / 兼容层
│           ├── mipmap-anydpi/           # API 24-25 兜底，引用 ic_launcher_legacy.png
│           └── mipmap-anydpi-v26/       # 自适应图标（黑底 + 徽标前景）
├── .gitignore
└── README.md
```

> Kotlin 源码包名与 `namespace` 均为 `com.example.webviewshell`（`R`、`BuildConfig` 与 Manifest 里的 `.MainActivity` 都按它解析），`applicationId` 为 `com.yuexiang.shell`（只决定 APK 包名）。两者可以不同，但**不要单独改 `namespace`**，否则会编译不过或启动闪退。

## 三、快速开始

### 方式 A：云端出包（推荐，无需本地开发环境）

把工程推到 GitHub 仓库 → `Actions` → `编译可安装 APK` → `Run workflow` → 在 `Releases` 下载 `yuexiang-shell.apk`。完整步骤见 **[安装到手机.md](安装到手机.md)**。

workflow 支持两个输入参数，不用改代码就能换网址：

| 输入 | 默认值 | 说明 |
| --- | --- | --- |
| `site_url` | `https://yuexiang.dpdns.org/` | 首页地址，只填域名会自动补 `https://` |
| `allowed_hosts` | `yuexiang.dpdns.org` | 允许在 App 内打开的域名，多个用英文逗号分隔 |

### 方式 B：Android Studio

1. `File → Open`，选择 `WebViewShell/` 目录。
2. 等待 `Gradle Sync` 完成（首次会下载 Gradle 8.7 与依赖，需要联网）。
3. 连接真机或启动模拟器，点 `Run 'app'`。

### 方式 C：命令行

```bash
# 按需设置 JDK 17 与 Android SDK 路径
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk

cd WebViewShell
./gradlew assembleDebug
# Windows 用：gradlew.bat assembleDebug

# 产物：app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Debug APK 使用自动生成的调试签名，**可直接安装自测**；正式上架需要自己的签名，见第六节。

## 四、必改配置（都在 `app/build.gradle`）

当前值：

```groovy
buildConfigField 'String', 'SHELL_URL', '"https://yuexiang.dpdns.org/"'
buildConfigField 'String', 'ALLOWED_HOSTS', '"yuexiang.dpdns.org"'
buildConfigField 'boolean', 'ENABLE_HTTP_FALLBACK', 'true'
```

| 字段 | 说明 |
| --- | --- |
| `SHELL_URL` | App 启动时加载的首页，必须带 `https://` 或 `http://` |
| `ALLOWED_HOSTS` | 允许在 WebView 内打开的域名，多个用英文逗号分隔；子域名自动放行。写 `"*"` 表示所有 http/https 链接都在 App 内打开 |
| `ENABLE_HTTP_FALLBACK` | 首页 https 加载失败时，自动改用 http 重试一次。改为 `false` 可关闭 |

改完重新编译即可，不需要动 Kotlin 代码。顺手可改：

- `applicationId 'com.yuexiang.shell'` → 你自己的包名（上架后不可随意更改）
- `res/values/strings.xml` 里的 `app_name`（当前为「悦翔电竞」）
- 桌面图标：见下一节「自定义图标」

## 四之二、自定义图标

当前桌面图标是 `icon-source/yuexiang.jpg`（黑底金纹盾形 X 徽标）。上传时文件名带 `.txt` 后缀，去掉后**实际内容是 JPEG 而不是 ICO**；Android 不使用 ICO，所以已按真实格式转成 PNG 资源，源图备份在 `icon-source/`。

处理时做了两件事：裁掉原图右下角残留的白色方框、去掉四周多余黑边让徽标居中放大。资源结构：

| 文件 | 作用 |
| --- | --- |
| `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` | 普通图标（48/72/96/144/192 px） |
| `…/ic_launcher_round.png` | 圆形图标 |
| `…/ic_launcher_foreground.png` | 自适应图标前景，108dp 画布、主体居中占 70%、其余透明（108/162/216/324/432 px） |
| `…/ic_launcher_legacy.png` | 供 `mipmap-anydpi/ic_launcher.xml` 引用的兼容层 |
| `mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | 黑底 + 徽标前景的自适应图标（Android 8.0+） |
| `values/colors.xml` 的 `ic_launcher_background` | 图标底色，已设为与原图一致 `#050505` |

**以后换图标**：准备一张正方形 PNG/JPG（建议 ≥1024×1024），替换 5 个密度目录里的 4 类 PNG 即可；若新图底色不是黑色，同步改 `ic_launcher_background`。注意 `mipmap-anydpi/` 优先级高于 `mipmap-{density}/`，所以那个 XML 必须指向新图，否则桌面仍显示旧图标。`drawable/ic_launcher_foreground.xml` 是早期默认矢量图，现已无任何引用，删掉不影响。

> 想让顶部进度条也配黑金风格，可把 `values/colors.xml` 的 `primary` 从 `#1565C0` 改成 `#C9A227`（夜间模式改 `values-night/colors.xml` 的 `primary`）。

## 五、链接、协议与安全策略

- **站内链接**：白名单域名（含子域名）在 WebView 内打开，返回键可逐级回退，退到首页再按一次才退出 App。
- **站外链接**：交给系统浏览器 / 对应 App 处理，避免套壳应用变成通用浏览器，也降低被应用商店判定为「浏览器类应用」的风险。
- **第三方登录 / 支付回调**：如果 OAuth、微信/支付宝支付等跳转域名不在白名单里，会被送进外部浏览器，可能回不到 App。把相关域名补进 `ALLOWED_HOSTS`，或临时设为 `"*"` 验证。
- **HTTPS 优先 + HTTP 回退**：DDNS 站点常见「只有 HTTP」或「HTTPS 自签证书」。工程默认 `https://` 起步，主框架加载失败或证书校验失败时自动改用 `http://` 重试一次，用户无感。这是**换协议重试，不是绕过证书校验**——`onReceivedSslError` 里始终 `handler.cancel()`，绝不调用 `proceed()`。
- **明文流量范围可控**：`network_security_config.xml` 的 `base-config` 全局禁止 HTTP，只对 `yuexiang.dpdns.org`（含子域名）开了 `cleartextTrafficPermitted="true"`；`AndroidManifest.xml` 的 `usesCleartextTraffic="true"` 在存在 NSC 时由 NSC 覆盖，不会全局放开。
- **混合内容**：HTTPS 页面里加载 HTTP 图片/脚本仍被 `MIXED_CONTENT_NEVER_ALLOW` 禁止。若页面因此显示不全，需要把页面资源一并改成 HTTPS。
- **换成自己的域名时**：除了 `build.gradle`，记得同步改 `network_security_config.xml` 里 `<domain>` 的值（走 GitHub Actions 出包时 workflow 会自动改）。

> 安全红线：不要在 `onReceivedSslError` 里调用 `handler.proceed()`，不要开启 `setAllowUniversalAccessFromFileURLs`。本工程没有这样做，后续扩展时也请保持。

## 六、打包签名 APK

1. 生成密钥库（妥善保管，丢失将无法更新应用）：

   ```bash
   keytool -genkey -v -keystore keystore/shell.jks -alias shell \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. 在 `app/build.gradle` 的 `android {}` 中增加（**不要**把密码写进仓库，建议从 `local.properties` 或环境变量读取）：

   ```groovy
   signingConfigs {
       release {
           storeFile file('../keystore/shell.jks')
           storePassword project.findProperty('SHELL_STORE_PASSWORD') ?: ''
           keyAlias 'shell'
           keyPassword project.findProperty('SHELL_KEY_PASSWORD') ?: ''
       }
   }
   buildTypes {
       release {
           signingConfig signingConfigs.release
           minifyEnabled false
       }
   }
   ```

3. 构建：

   ```bash
   ./gradlew assembleRelease
   # 产物：app/build/outputs/apk/release/app-release.apk
   ```

## 七、已经内置 / 尚未内置

**已内置（对应「纯展示」选型）**

- 全屏无标题栏、浅色/深色主题
- JavaScript、DOM Storage、viewport 适配（现代网页不白屏）
- 禁用缩放，保留网页自身的响应式布局
- 顶部加载进度条、网络失败页与「重新加载」
- 返回键站内回退、旋转屏幕不重载页面、进程回收后恢复浏览状态
- 下载链接交给系统处理
- https 失败自动回退 http（可关闭）、证书错误严格拒绝

**未内置（需要时再加）**

- 相机、定位、麦克风、文件上传等系统权限
- 推送通知
- JS 与原生互调（`@JavascriptInterface`）、自定义 User-Agent、离线资源包
- 启动页、底部导航栏、多标签、下拉刷新

## 八、上架与合规提醒

- 套壳他人网站前请确认已获得授权；应用商店（含国内各渠道与 Google Play）对「仅包裹网页、缺少原生功能」的应用有明确审核限制，纯 WebView 应用有被拒风险，建议补充至少一项原生能力。
- 若网页涉及登录、表单或个人信息，需在 App 内提供隐私政策，说明 Cookie、本地存储、网络请求等数据的使用方式；网页里的第三方统计/广告 SDK 也需要一并声明。
- 网页内容更新不需要重新发版，但首页域名、白名单、权限声明的变更需要重新打包。
- DDNS 域名解析到动态 IP，服务器换 IP 后等 DNS 生效即可，App 无需重新出包。

## 九、常见问题

| 现象 | 排查方向 |
| --- | --- |
| 打开白屏 | JS 已默认开启；重点看 HTTPS 证书是否受系统信任、是否有被拦截的 HTTP 混合内容、首页是否 302 跳到白名单外域名、网页是否针对 WebView UA 做了限制 |
| 一直转圈 / 加载失败 | 先用手机浏览器直接打开 `yuexiang.dpdns.org`：DDNS 指向的服务器或内网穿透服务可能没在线，或手机网络访问不到该域名 |
| 站点只有 HTTP | 工程已自动回退；也可把 `SHELL_URL` 直接写成 `http://yuexiang.dpdns.org/` |
| 点登录/支付后跳走回不来 | 把回调域名补进 `ALLOWED_HOSTS`，或设为 `"*"` |
| HTTP 地址打不开 | 确认 `network_security_config.xml` 的 `<domain>` 与该域名一致 |
| 页面里的图片/字体不显示 | 多为混合内容或 CDN 证书问题，看 Logcat 的 `WebView` / `chromium` 日志 |
| 旋转屏幕后回到首页 | 已用 `configChanges` + `saveState/restoreState` 处理；若自定义了 Manifest 请保留配置 |
| 返回键直接退出 | 符合预期：先逐级回退网页历史，退到无可回退才退出 |
| 提示「应用未安装」 | 装过同包名但签名不同的包，先卸载旧版 |
