# miwearhaptics

为 Android / Wear 应用提供 Google 与小米 Wear SDK 之间的滚动触觉兼容，面向小米手表 5 / 小米手表 5 eSIM（中国大陆版）等适配场景。是否可用取决于设备实际提供的 SDK 方法，不按机型、厂商字符串或系统版本硬编码判断。

> [!WARNING]
>
> ## 本项目即将由新项目取代
>
> **`miwearhaptics` 已停止后续迭代，新的开发工作已迁移至 [`cnwearoverlay`](https://github.com/Star-ZER0/cnwearoverlay)。**
>
> 如需使用最新版本或获取后续更新，请前往：
>
> **[Star-ZER0/cnwearoverlay](https://github.com/Star-ZER0/cnwearoverlay)**
>
> 本仓库仅保留用于历史版本、已有项目迁移以及相关参考。迁移流程会在新项目迭代完成后发布于本README。

## 迁移至 **[Star-ZER0/cnwearoverlay](https://github.com/Star-ZER0/cnwearoverlay)**

### 1.在项目目录下：

删除miwearhaptics

```bash
git submodule deinit miwearhaptics
git rm -f miwearhaptics
```

新增cnwearoverlay

```bash
git submodule add https://github.com/Star-ZER0/cnwearoverlay.git cnwearoverlay
```

### 2.在宿主 `settings.gradle.kts` 中：

将

```kotlin
includeBuild("miwearhaptics")
```

改为

```kotlin
includeBuild("cnwearoverlay")
```

### 3.在应用模块（通常为 `app/`）的 `build.gradle.kts` 中：

将

```kotlin
id("cc.star0.wear.lib.miwearhaptics")
```

改为

```kotlin
id("cc.star0.wear.lib.cnwearoverlay")
```

## 接入方式

项目提供两种接入方式：

| 方式 | 适用场景 | 工作方式 | 默认优先级 |
| --- | --- | --- | --- |
| Gradle 插件 | 可以重新构建应用 | 构建时将 Google getter 调用重定向到兼容层 | Google 原始方法 → 小米 → 禁用该效果 |
| Xposed 模块 | 通过支持 Modern API 102 的框架加载模块 | Hook Google getter；Google 类缺失时补入三个 getter 的兼容入口 | Google 原始方法 → 小米 → 禁用该效果 |

两种方式都只负责获取触觉常量，实际反馈仍由应用的 `View.performHapticFeedback()` 等调用触发。项目由 AI 辅助编写。

如果只需在自己编写的代码中使用兼容 API，也可以[单独依赖运行时库](#单独使用运行时库)，无需启用字节码转换。

## 阅读导航

- [支持范围](#支持范围)
- [Gradle 插件接入与配置](#方式一gradle-插件)
- [运行时 API 与缓存行为](#运行时策略与-api)
- [Xposed 模块接入与日志](#方式二xposed-模块)
- [构建、发布与验证](#构建与验证)
- [常见问题](#常见问题)
- [维护与协作指南](AGENTS.md)

## 支持范围

目前支持以下无参数、返回 `int` 的静态方法，JVM 签名均为 `()I`：

| Google / 小米 SDK 方法 | 兼容层 `Effect` | 用途 |
| --- | --- | --- |
| `getScrollItemFocus()` | `SCROLL_ITEM_FOCUS` | 滚动条目获得焦点 |
| `getScrollTick()` | `SCROLL_TICK` | 滚动刻度 |
| `getScrollLimit()` | `SCROLL_LIMIT` | 滚动到达边界 |

目标类分别为：

- `com.google.wear.input.WearHapticFeedbackConstants`
- `com.xiaomi.miwear.input.WearHapticFeedbackConstants`

每种效果独立判断接口是否可调用。兼容层或已安装的 Hook 在对应 SDK 不可用时按策略回退；均不可用时返回 `NO_HAPTICS = -1`。成功返回的整数（包括 `-1`、`0`）会被接受，不会因为该数值而继续回退，也不会替换为其他振动效果。Xposed 无法安装 Hook 的调用不经过这套回退逻辑。

## 方式一：Gradle 插件

### 接入

将本仓库完整放在 Android 工程根目录下，例如：

```text
your-android-project/
├── settings.gradle.kts
├── app/
│   └── build.gradle.kts
└── miwearhaptics/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── src/
    ├── lib/
    └── xposed/
```

在宿主 `settings.gradle.kts` 的顶层、已有 `pluginManagement` 等配置块之后添加，与 `include(":app")` 同级：

```kotlin
includeBuild("miwearhaptics")
```

在需要适配的 Android 应用或库模块的 `build.gradle.kts` 中应用插件：

```kotlin
plugins {
    // 保留模块现有的 Android 插件。
    id("cc.star0.wear.lib.miwearhaptics")
}
```

插件自动添加运行时依赖 `cc.star0.wear.lib:miwearhaptics:1.0.0`，由 included build 中的 `lib` 子项目通过依赖替换提供，无需先发布到 Maven。顶层 `includeBuild` 同时用于项目插件解析和运行时依赖替换，不要只在 `pluginManagement` 中引入。

自动注册仅针对应用了 `com.android.application` 或 `com.android.library` 的模块。在没有 Android 插件的工程根项目中应用它，只会创建 `wearHaptics` 扩展，不会为子模块添加依赖或转换调用。

对于已经配置好 Wear SDK 的项目，上述两处配置即可接入。原有业务调用保持不变，例如：

```kotlin
import com.google.wear.input.WearHapticFeedbackConstants

view.performHapticFeedback(WearHapticFeedbackConstants.getScrollTick())
```

构建后，受支持 getter 的调用目标变为 `WearHapticFeedbackConstantsCompat` 的同名方法。

### Wear SDK 前提

如果源码直接引用 Google SDK 类型，仍需保留原有编译期 SDK 依赖：转换发生在源码编译之后。在应用的 `AndroidManifest.xml` 中，确保 `<application>` 内有以下声明：

```xml
<uses-library
    android:name="wear-sdk"
    android:required="false" />
```

插件不修改清单，也不提供设备 SDK。设备实现由系统共享库提供；编译用 SDK 桩应作为编译期依赖，避免将其打包进正式 APK。

### 转换范围与配置

应用模块使用 `InstrumentationScope.ALL`，处理本模块及依赖类；库模块使用 `InstrumentationScope.PROJECT`，只处理本库模块的类。需要覆盖第三方依赖时，应在最终应用模块启用插件。

默认覆盖所有变体和符合条件的类。可在应用插件的模块中通过 `wearHaptics` 缩小范围：

```kotlin
wearHaptics {
    enabled.set(true)
    variants.set(setOf("debug", "release"))
    includedClassPrefixes.set(listOf("com.example.app."))
    excludedClassPrefixes.set(listOf("com.example.app.legacy."))
    methods.set(setOf("getScrollItemFocus", "getScrollTick", "getScrollLimit"))
    failOnUnsupportedCalls.set(true)
}
```

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `enabled` | `true` | 启用转换；关闭后仍添加运行时依赖 |
| `variants` | 空集合 | 空集合表示全部；否则匹配完整变体名，如 `demoDebug` |
| `includedClassPrefixes` | 空列表 | 空列表表示不限制；否则仅处理匹配前缀的类 |
| `excludedClassPrefixes` | 空列表 | 排除匹配前缀的类，优先于包含规则 |
| `methods` | 上述三个 getter | 选择要转换的方法；空集合不转换 getter，但仍按 `failOnUnsupportedCalls` 检查不支持的成员 |
| `failOnUnsupportedCalls` | `true` | 在处理范围内遇到不支持的 Google 成员访问时使构建失败 |

类名前缀按点号形式进行字符串匹配。示例中的包含列表会限制第三方依赖的覆盖范围；需要全部覆盖时保留空列表。`methods` 使用不带括号的方法名，不接受支持列表以外的名称。

前缀和变体名区分大小写，不支持通配符或正则表达式；按包筛选时建议保留末尾的点，避免 `com.example.app` 同时匹配 `com.example.application`。列表中的空字符串会匹配所有类，因此 `excludedClassPrefixes.set(listOf(""))` 会排除全部类，不能用它代替空列表。

兼容层自身，以及 `com.google.wear.input.`、`com.xiaomi.miwear.input.` 始终排除。未选中的已知 getter 保留原始调用；关闭严格检查也只会保留不支持的访问，不会使其获得兼容能力。

转换支持普通静态调用、方法句柄、`invokedynamic` 和嵌套 `ConstantDynamic`。对直接继承 `FunctionReference`、`FunctionReferenceImpl` 或 `AdaptedFunctionReference` 的 Kotlin 函数引用生成类，还会按条件修正其保存的 owner 类引用。业务代码中的反射字符串和普通类字面量不作全局替换。

严格检查针对字节码中对 Google 目标类的成员访问：字段、构造方法、实例方法、其他方法名或非 `()I` 签名都不属于支持范围。已经被编译器内联的常量、方法签名里的类型引用和通过字符串反射的访问，不会因此自动适配或被完整检查。选择方法子集时，Kotlin owner 修正还要求该生成类已有适配调用，且没有剩余 Google getter 调用。

### 关闭转换与移除接入

`enabled.set(false)` 关闭转换和严格成员检查，但仍添加运行时依赖；它不会禁止原始 Google 调用或关闭应用触觉。`methods.set(emptySet())` 只让三个 getter 保持原样，严格检查仍由 `failOnUnsupportedCalls` 控制。

要完全移除插件接入，应移除相关 Android 模块中的插件声明及 `wearHaptics` 配置，确认没有直接使用兼容 API 或依赖运行时库后，再移除顶层 `includeBuild` 并重新构建应用。

### 运行时策略与 API

运行时公开类为 `cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat`：

| `Policy` | 行为 |
| --- | --- |
| `GOOGLE_FIRST` | 默认策略，Google 不可用时尝试小米 |
| `XIAOMI_FIRST` | 小米不可用时尝试 Google |
| `GOOGLE_ONLY` | 只使用 Google |
| `XIAOMI_ONLY` | 只使用小米 |
| `DISABLED` | 返回不可用结果 |

以下成员均为静态 API：

| 成员 | 返回值与作用 |
| --- | --- |
| `NO_HAPTICS` | 整数常量 `-1` |
| `getScrollItemFocus()` / `getScrollTick()` / `getScrollLimit()` | 使用全局策略，返回对应效果的 `int` |
| `get(Effect)` / `get(Effect, Policy)` | 返回效果常量；不可用时返回 `NO_HAPTICS` |
| `getOrNull(Effect)` / `getOrNull(Effect, Policy)` | 返回 `Integer`；不可用时返回 `null` |
| `getPolicy()` | 读取当前全局策略 |
| `setPolicy(Policy)` | 设置后续读取使用的全局策略，返回 `void` |

自动接入无需主动调用这些 API。需要修改全局策略时，在 UI 或相关库初始化前设置：

```kotlin
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat as Haptics

Haptics.setPolicy(Haptics.Policy.XIAOMI_FIRST)
```

也可以单次指定策略，并在不可用时跳过反馈调用：

```kotlin
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat as Haptics

Haptics.getOrNull(Haptics.Effect.SCROLL_TICK, Haptics.Policy.GOOGLE_FIRST)
    ?.let { view.performHapticFeedback(it) }
```

`get(effect)` / `getOrNull(effect)` 使用全局策略；带 `Policy` 的重载只影响本次调用。不可用时，整数 API 返回 `-1`，可空 API 返回 `null`。

所有 `Effect` 和 `Policy` 参数均不能为 `null`，否则抛出 `NullPointerException`，即使策略为 `DISABLED` 也一样。SDK 成功返回 `-1` 时，`getOrNull` 返回装箱后的 `-1`，不会返回 `null`。`DISABLED` 跳过 SDK 解析，只影响经由兼容 API 的读取，不会关闭其他来源的触觉反馈。

### 解析、异常与缓存

运行时在首次需要某个 SDK 的某个效果时，通过兼容类自身的类加载器加载并初始化 SDK 类，查找公开、静态、无参数且返回原始类型 `int` 的方法。返回 `Integer` 的方法不符合要求。这里只验证调用是否成功，不测量设备马达，也不验证返回常量的物理效果。

| 解析结果 | 处理方式 |
| --- | --- |
| 返回任意 `int` | 缓存并使用，不再尝试后备 SDK |
| 类或方法缺失、非法签名、链接错误、可恢复的反射或调用失败 | 缓存该 SDK 效果不可用，按当前策略尝试后备 SDK |
| 被调用方法抛出 `VirtualMachineError` 或 `ThreadDeath` | 继续传播，不作为普通不可用结果缓存 |

解析器按 SDK 和效果缓存成功及不可用结果。修改策略只影响后续读取，不清除解析缓存，也不更新调用方已经保存的常量。设备系统更新后，应重新启动应用进程以重新解析能力；默认策略会优先选择更新后可调用的 Google 接口。

缓存使用同步保护，全局策略通过 `volatile` 保证可见性；公开 API 不提供缓存清除、类加载器替换或当前实际命中 SDK 的查询接口。`getPolicy()` 返回的是选择策略。不同类加载器加载的兼容类可持有各自的策略和缓存，因此这里的“全局”不等于设备或跨进程设置。

### 单独使用运行时库

保留宿主 settings 顶层的 `includeBuild("miwearhaptics")`，在 Android 模块中直接添加依赖即可；无需应用本项目的 Gradle 插件：

```kotlin
dependencies {
    implementation("cc.star0.wear.lib:miwearhaptics:1.0.0")
}
```

然后直接调用兼容 API：

```kotlin
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat as Haptics

Haptics.getOrNull(Haptics.Effect.SCROLL_TICK)
    ?.let { view.performHapticFeedback(it) }
```

这种方式只影响主动调用兼容 API 的代码，不会转换业务代码或第三方依赖中已有的 Google 调用。如果应用源码完全不引用设备 SDK 类型，就无需为这些兼容 API 调用添加 Google 编译桩；设备 SDK 的可见性仍由宿主清单和设备系统决定。运行时是纯 Java JAR，没有 Android `minSdk` 声明，Java 8 编译目标也不能作为所有 Android 版本均已通过运行验证的依据。

## 方式二：Xposed 模块

`xposed/` 是独立 Android 构建，复用 `lib/` 的解析代码。模块包名为 `cc.star0.wear.xposed.miwearhaptics`，最低 Android API 为 26，需要提供 **Modern libxposed API 102** 的框架。仅支持旧版 Xposed API 的框架不满足要求。

模块面向兼容 API 102 的 Root 框架和 JingMatrix/LSPatch 的应用内嵌模块方式设计。后者无需模块自身获取 Root 权限；具体框架版本的兼容性需通过实际运行验证。

### 构建与加载

在仓库根目录执行：

```powershell
.\xposed\gradlew.bat -p xposed assembleDebug
```

生成模块 APK：

```text
xposed/build/outputs/apk/debug/miwearhaptics-xposed-debug.apk
```

根据框架选择加载方式：

- **Root 框架**：安装模块 APK，在框架管理器中启用模块并选择目标应用作用域，然后重新启动目标应用进程。
- **JingMatrix/LSPatch**：使用提供 Modern API 102 的版本及其工具，将模块嵌入目标 APK，再安装修补后的应用。模块更新后需要重新修补、安装并重启目标进程；具体修补参数与签名方式以所用工具版本为准。

模块没有启动界面或策略设置页面，`scope.list` 为空，不预置目标应用。当前固定采用 **Google 优先、不可用时回退小米、均不可用时返回 `-1`**，与 Gradle 插件默认策略一致；Gradle 插件的 `wearHaptics` 配置和兼容层全局策略不会改变该 Hook 的选择逻辑。模块关闭自动热重载，更新模块或作用域后需重新启动目标进程。

### 运行条件与限制

模块在 `onPackageReady` 中通过目标应用的最终类加载器查找 Google 类，并逐个 Hook 可用的公开静态 getter。先通过原始方法调用器执行 Google 实现；方法不可用或发生可恢复调用失败时再解析小米接口；均不可用时返回 `-1`。成功及失败结果均按效果缓存。Google 返回的任何整数（包括 `-1`、`0`）均直接接受，不再调用小米。小米回调同一 Google getter 时复用 Google 缓存，不会反复调用已失败的原方法；尚在递归解析的效果返回 `-1`，其他效果可独立解析。

安装阶段不主动初始化 Google 类。只处理该类自身声明的公开、静态、非抽象、无参数、返回 `int` 的 getter；继承来的同名方法不在当前查找范围内。某个效果缺失、签名不符或 Hook 安装失败时会跳过该效果，继续尝试其他效果。同一模块实例记录已 Hook 的 `Method`，多个包回调解析到同一共享方法时不会重复注册。

小米解析固定使用 `XIAOMI_ONLY`，Google 优先调用通过 `Invoker.Type.ORIGIN` 执行原始方法，绕过该方法的 Hook 链。模块使用 `ExceptionMode.PASSTHROUGH`，使致命错误可以继续传播。和其他 Hook 模块一起使用时，应单独验证调用顺序与回退行为。

**Google 类完全缺失时，模块会补入只含上述三个 getter 的兼容类。** 兼容类单独编译为 DEX 资源，借助 `InMemoryDexClassLoader` 将其追加到目标 `BaseDexClassLoader` 的 DEX 路径；原有类保持优先。兼容类通过仅使用 Java 标准类型的回调访问目标加载器的小米接口，均不可用时返回 `-1`，不会引入其他振动效果。它不是 Google 设备 SDK 实现，也不提供未支持的字段或方法。

补类依赖框架允许访问 Android 的 `pathList` / `dexElements` 内部字段。自定义非 `BaseDexClassLoader`、内部字段访问受限、Google 类已因初始化错误损坏，或应用在模块回调前已触发缺类异常的情况仍需单独处理；安装失败会明确记录日志。已有 Google 类只缺部分 getter 时，不能通过补类为它添加方法。模块不会补充 `wear-sdk` 清单声明或设备实现，`-1` 只禁用这些调用对应的效果，不会全局关闭应用震动。

模块跳过自身、`android` 包和 `system_server` 进程。Hook 只影响安装之后实际执行的目标 getter，应用提前缓存的常量不会自动更新；具体框架、固件和应用组合需单独验证。

### 模块日志

模块通过框架日志接口以 `MiWearHaptics` 为标签记录信息，内容带有 `包名/进程名`。先查看框架管理器的模块日志；框架是否同时转发到 logcat 取决于框架实现。

| 日志正文 | 含义与检查方向 |
| --- | --- |
| `Installed N getter hooks (GOOGLE_FIRST)` | 本次回调新装了 N 个 Hook，最多为 3；不证明 SDK 已解析或验证马达 |
| `Installed missing Google class fallback (GOOGLE_FIRST)` | 为缺失 Google 类的目标加载器安装了兼容入口；不代表小米接口或马达可用 |
| `Cannot install missing Google class fallback: <异常类型>` | 补类失败，检查目标加载器类型及框架对 Android 内部字段的访问支持 |
| `Google class cannot be loaded: <异常类型>` | Google 类加载发生链接或运行时错误，没有用兼容类覆盖它 |
| `Unsupported signature: <getter>` | 找到了无参数同名方法，但访问修饰符或返回类型不符合要求 |
| `Cannot hook <getter>: <异常类型>` | 查找或注册该方法失败；只有带参数的重载时也会走此路径 |

重复回调没有新增 Hook 时不会再输出 `Installed`。模块和共享运行时不为每次反馈或每次 SDK 回退写日志，所以日志中没有小米解析错误不能证明小米接口可用。

### 停用与重新验证

在 Root 框架中停用模块或移除目标作用域后，重启目标应用进程。LSPatch 内嵌方式需要重新安装未嵌入本模块的应用版本；仅卸载单独的模块 APK 不会移除已经嵌入目标 APK 的代码。重新验证时使用新的目标进程，避免此前保存的常量影响判断。

## 构建与验证

### 环境与构建命令

仓库在 `xposed/` 提供 Gradle Wrapper（9.7.1）；也可使用满足 AGP 要求的本机 Gradle 或宿主 Wrapper。根目录与 `xposed/` 是两个独立构建，共享 `lib/build/` 输出，应顺序执行，避免同时写入共享产物。

| 构建部分 | 当前配置 |
| --- | --- |
| 根目录 Gradle 插件 | 版本 `1.0.0`；Java 17；AGP API `9.3.2`（`compileOnly`）；ASM / ASM Tree `9.10.1` |
| `lib/` 运行时 | 纯 Java，使用 `--release 8`；版本 `1.0.0` |
| `xposed/` 模块 | versionName `1.0.0` / versionCode `1`；AGP `9.3.3`；Java 8 编译目标；compileSdk / targetSdk 37；minSdk 26 |

根插件的 AGP API 编译依赖可通过 `-PwearHapticsAgpVersion=<版本号>` 覆盖。该属性不更改宿主 AGP，也不更改 `xposed/` 中的 AGP 版本；覆盖版本后需验证兼容性。Xposed 构建另需 Android SDK Platform 37，通过本机 SDK 环境或 `xposed/local.properties` 配置路径。

根构建独立使用 Google Maven 和 Maven Central，不读取宿主版本目录；Xposed 的插件解析还使用 Gradle Plugin Portal，并禁止在项目脚本中另加依赖仓库。根构建只编译插件和 Java 运行时，不需要本机 Android SDK；宿主 Android 构建与独立 Xposed 构建需要各自可用的 SDK 配置。`lib/` 已由 settings 映射为 `:miwearhaptics`，以下任务通过根目录或 `xposed/` 执行。

在仓库根目录运行：

```powershell
# 构建 Gradle 插件与 Java 运行时，不包含 Xposed 构建
gradle build

# Xposed Debug APK 和启用 R8 的 Release APK
.\xposed\gradlew.bat -p xposed assembleDebug assembleRelease
```

如果仓库位于宿主工程的 `miwearhaptics/`，可在宿主根目录执行：

```powershell
.\gradlew.bat -p miwearhaptics build
.\gradlew.bat :app:assembleDebug
.\gradlew.bat -p miwearhaptics/xposed assembleDebug
```

macOS / Linux 使用 `./gradlew`，模块名不是 `app` 时替换任务路径。

### 产物与发布

| 产物 | 默认输出路径 |
| --- | --- |
| Gradle 插件 JAR | `build/libs/miwearhaptics-plugin-1.0.0.jar` |
| Java 运行时 JAR | `lib/build/libs/miwearhaptics-1.0.0.jar` |
| 运行时源码 JAR | `lib/build/libs/miwearhaptics-1.0.0-sources.jar` |
| Xposed Debug APK | `xposed/build/outputs/apk/debug/miwearhaptics-xposed-debug.apk` |
| Xposed Release APK | `xposed/build/outputs/apk/release/miwearhaptics-xposed-release-unsigned.apk` |

Debug 模块使用 Android 默认调试签名；Release 启用 R8 代码压缩和资源压缩，未配置签名，安装或分发前需自行签名。`io.github.libxposed:api:102.0.0` 是正式模块的 `compileOnly` 依赖，由框架提供；测试 SDK 桩和框架 API 类不能打包进模块 APK。

运行时 JAR 包含 `META-INF/proguard/proguard-rules.pro`，用于保留反射目标名称和方法并处理可选类缺失警告；它不会提供 SDK 实现。Xposed 另用 `xposed/proguard-rules.pro` 保留入口构造方法和生命周期回调。检查最终 Release APK 时，应确认 `META-INF/xposed/java_init.list` 中的入口仍存在，并核对 `module.prop` 和 `scope.list`；R8 构建成功不能替代实际框架加载验证。

运行时配置了名为 `maven` 的 Maven publication，可从仓库根目录发布到本机 Maven 仓库：

```powershell
gradle :miwearhaptics:publishToMavenLocal
```

坐标为 `cc.star0.wear.lib:miwearhaptics:1.0.0`。消费本机发布时，需在宿主的依赖仓库中配置 `mavenLocal()`；included build 接入无需此步骤。当前未配置远程发布仓库，文档中的坐标不表示已发布到 Maven Central 或 Gradle Plugin Portal。根插件也没有配置远程插件发布流程。

### 验证范围

仓库不保留测试源码和冒烟测试脚本。构建命令验证编译、打包及配置，不等于完整行为测试。Google 优先、小米回退、均不可用、缺类补入及递归行为，应在目标应用和框架中按需验证；实际触觉表现还需在设备上确认。

检查宿主运行时依赖替换时，可在宿主工程根目录执行（按实际模块和变体替换 `app`、`debugRuntimeClasspath`）：

```powershell
.\gradlew.bat :app:dependencyInsight --dependency cc.star0.wear.lib:miwearhaptics --configuration debugRuntimeClasspath
```

实际框架验证应记录模块版本、框架版本、目标应用、设备系统和运行结果，每轮使用新进程清除缓存影响。

## 常见问题

| 现象 | 检查方向 |
| --- | --- |
| 找不到插件或运行时依赖 | 仓库目录是否完整；顶层 `includeBuild` 路径是否正确；是否在 Android 模块应用插件 |
| 源码编译时找不到 Google 类 | 编译期 Wear SDK 依赖是否保留；插件在编译之后才转换调用 |
| 构建报告不支持的 Google 成员 | 按错误中的调用方定位；扩展适配或排除该调用方，关闭严格检查只保留原始访问 |
| `wearHaptics.methods supports only ...` | `methods` 中只填写三个支持的方法名，不带括号，不受关闭严格成员检查影响 |
| 关闭插件后依赖仍然存在 | `enabled=false` 仅关闭转换；完全移除需删除插件声明及不再需要的运行时接入 |
| Gradle 接入后没有反馈 | `wear-sdk` 清单声明、转换过滤条件、运行时策略及设备对应方法是否可用 |
| Google 调用仍未转换 | 是否在正确的 Android 模块启用、变体名是否匹配、包含/排除规则是否过滤调用方、方法是否选中；反射字符串不在转换范围内 |
| Xposed 日志出现 `Google class unavailable; no hooks installed` | 正在运行旧模块；更新模块并重启。LSPatch 内嵌方式须用新模块重新修补目标 APK |
| Xposed 未生效 | 框架是否提供 API 102、作用域或嵌入方式是否正确、目标进程是否重启；查看 `MiWearHaptics` 日志 |
| 调整策略或更新系统后效果不变 | 解析结果和调用方常量可能已缓存，重新启动目标应用进程后再验证 |
| 同时使用 Gradle 插件与 Xposed 时优先级难以判断 | 兼容层反射调用的 Google getter 也可能已被 Hook；优先选择一种接入路径，叠加使用需单独验证 |
| 返回非空常量仍没有触觉 | SDK 返回整数只证明方法可调用；检查实际反馈调用、应用/系统设置和设备表现 |

反馈问题时，记录接入方式、相关 DSL/策略、宿主 AGP、Gradle 与 JDK 版本，以及设备系统、框架版本和目标进程。涉及 Xposed 时附模块日志；涉及测试时附失败场景和本次运行结果，并区分 JVM、ART 与实际触觉验证。

## 仓库结构

```text
miwearhaptics/
├── build.gradle.kts          # Gradle 插件定义
├── settings.gradle.kts       # 将 lib 映射为 :miwearhaptics
├── src/main/java/            # 插件入口、DSL、ASM 转换
├── lib/
│   ├── build.gradle.kts      # Java 运行时与 Maven 发布配置
│   └── src/main/             # 兼容 API、解析器、混淆规则
├── xposed/
│   ├── build.gradle.kts      # 独立模块 APK 构建
│   ├── settings.gradle.kts   # 复用 ../lib
│   ├── src/main/             # Modern API 102 入口与 Hook
│   └── src/fallback/         # Google 缺类兼容入口（独立 DEX 资源）
├── AGENTS.md                 # 维护与协作约定
└── LICENSE
```

维护说明见 [AGENTS.md](AGENTS.md)。许可证见 [LICENSE](LICENSE)（GNU LGPL v3）。
