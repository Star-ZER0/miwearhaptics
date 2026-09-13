# miwearhaptics

面向 小米手表 5 / 小米手表 5 eSIM (中国大陆版) 设备的 Wear 触觉反馈兼容插件。它在 Android 构建过程中，将受支持的 `com.google.wear.input.WearHapticFeedbackConstants` 调用自动转向运行时兼容层，再根据设备实际可调用的接口选择 Google 或小米实现。

原有触觉反馈调用方式可以保持不变，无需逐处替换业务代码或修改第三方库源码。

本项目由 AI 辅助编写。

## 项目优点

1. **两行配置即可接入**：对于已经配置好 Wear SDK 的 Android 项目，只需新增 `includeBuild("miwearhaptics")` 和 `plugins { id("cc.star0.wear.lib.miwearhaptics") }`，无需修改原有业务代码。
2. **兼容后续系统更新**：默认优先调用 Google 接口，Google 接口不可用时回退到小米接口。后续小米手表 5 系统更新支持 `com.google.wear.input.WearHapticFeedbackConstants` 的对应方法后，应用在新进程中会优先使用 Google 实现，仍可正常使用触觉反馈，无需为此移除插件或修改调用代码。
3. **可覆盖第三方依赖**：应用模块会处理当前模块及其依赖中的受支持调用，适合业务代码和依赖库共同使用 Wear 触觉反馈的场景。
4. **按能力选择实现**：逐项检查方法是否可调用，不依赖厂商名称或系统版本猜测；一个效果不可用时，不影响其他效果的解析。
5. **运行时依赖轻量**：兼容层是以 Java 8 为目标的纯 Java 库，不依赖 AndroidX，也不直接链接 Google 或小米 SDK 类型。
6. **支持精细控制**：可以按构建变体、类名前缀和触觉方法选择转换范围，也可以按需调整运行时选择策略。

## 快速接入

### 1. 放置插件目录

将本项目完整放在 Android 工程根目录下，目录名为 `miwearhaptics`：

```text
your-android-project/
├── settings.gradle.kts
├── app/
│   └── build.gradle.kts
└── miwearhaptics/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── src/
    └── lib/
```

### 2. 新增两行配置

在工程的 `settings.gradle.kts` 顶层新增下面这一行，放在已有的 `pluginManagement` 等配置块之后，与 `include(":app")` 同级：

```kotlin
includeBuild("miwearhaptics")
```

本项目同时提供 Gradle 插件和运行时库，顶层 `includeBuild` 用于同时参与项目插件解析和运行时依赖替换。

在需要适配的 Android 模块（通常是 `app`）的 `build.gradle.kts` 中应用插件：

```kotlin
plugins {
    // 保留原有的 Android 应用或库插件。
    id("cc.star0.wear.lib.miwearhaptics")
}
```

同步 Gradle 并重新构建应用即可。插件会自动添加运行时依赖 `cc.star0.wear.lib:miwearhaptics:1.0.0`，由 included build 中的 `lib` 模块提供，无需手动添加该依赖或预先发布到 Maven 仓库。

### 原项目的 Wear SDK 配置

两行接入以原项目已具备相应的 Wear SDK 配置为前提：

- 如果源码直接引用 Google Wear SDK 类型，仍需保留原有的编译期 SDK 依赖；字节码转换发生在源码编译之后。
- 在应用 `AndroidManifest.xml` 的 `<application>` 中声明可选的 `wear-sdk` 共享库。已有声明时无需重复添加，尚未配置时补充：

```xml
<uses-library
    android:name="wear-sdk"
    android:required="false" />
```

插件不会自动修改清单，也不提供设备 SDK 实现。设备 SDK 应由系统共享库提供，编译使用的 SDK 桩应作为编译期依赖，避免将其实现打包进 APK。

### 构建环境

- 适用于应用了 `com.android.application` 或 `com.android.library` 的模块。
- 插件以 Java 17 为编译目标；Gradle 和运行它的 JDK 还需满足宿主项目所用 Android Gradle Plugin（AGP）的要求。
- 当前默认编译依赖为 AGP API `9.3.2`，可通过 Gradle 项目属性 `wearHapticsAgpVersion` 覆盖；更换版本后应验证实际构建兼容性。
- included build 自带 `google()` 和 `mavenCentral()` 仓库配置，不读取宿主项目的版本目录。

## 支持的触觉效果

当前自动适配以下三个无参数、返回 `int` 的静态方法，JVM 签名均为 `()I`：

| 原始 Google 方法 | 兼容层效果枚举 | 用途 |
| --- | --- | --- |
| `getScrollItemFocus()` | `SCROLL_ITEM_FOCUS` | 滚动时条目获得焦点的触觉反馈 |
| `getScrollTick()` | `SCROLL_TICK` | 滚动刻度触觉反馈 |
| `getScrollLimit()` | `SCROLL_LIMIT` | 滚动到边界的触觉反馈 |

例如，原有代码可以继续保持为：

```kotlin
import com.google.wear.input.WearHapticFeedbackConstants

view.performHapticFeedback(WearHapticFeedbackConstants.getScrollTick())
```

构建后，对 getter 的调用会指向 `WearHapticFeedbackConstantsCompat` 的同名方法；实际触发反馈的调用仍由原来的 View 或上层库完成。

## 兼容机制

默认策略为 `GOOGLE_FIRST`，对每一种效果分别执行以下逻辑：

1. 尝试反射调用 `com.google.wear.input.WearHapticFeedbackConstants` 的对应方法。
2. 如果类、方法或调用不可用，尝试 `com.xiaomi.miwear.input.WearHapticFeedbackConstants` 的对应方法。
3. 两者均不可用时，getter 返回 `NO_HAPTICS`（`-1`），不映射成其他振动效果；直接使用 `getOrNull(effect)` 时则返回 `null`。

解析结果按 SDK 和效果分别缓存，包括不可用的结果，避免重复反射。系统升级后的能力变化会在应用进程重新启动后重新解析。判断依据是静态方法能否成功返回 `int`，插件本身不检测实际振动效果。

构建时使用 AGP Instrumentation API 和 ASM 转换字节码，覆盖普通静态调用、方法句柄、`invokedynamic` 和动态常量中的相关引用，并对受支持的 Kotlin 函数引用生成类补充处理其保存的 owner 类引用。

### 模块范围

| 应用插件的位置 | 转换范围 |
| --- | --- |
| Android 应用模块 | 当前模块及依赖类，使用 `InstrumentationScope.ALL` |
| Android 库模块 | 当前库模块的类，使用 `InstrumentationScope.PROJECT` |

需要覆盖最终应用中的第三方依赖时，应在应用模块启用插件。兼容层自身，以及 Google、小米的 input SDK 包始终排除在转换范围之外。

## 可选构建配置

默认配置即可接入。需要限制处理范围时，在应用插件的模块中配置 `wearHaptics`：

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

此示例只处理 `com.example.app.` 下的类，并排除其中的 `legacy` 包；若要继续覆盖其他包和第三方依赖，保留默认的空包含列表即可。使用 product flavor 时，`variants` 需要填写完整变体名，例如 `demoDebug`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用字节码转换；关闭后仍会添加运行时依赖 |
| `variants` | 空集合 | 空集合表示所有变体；非空时按完整变体名匹配 |
| `includedClassPrefixes` | 空列表 | 空列表表示不限制类名；非空时只处理匹配前缀的类 |
| `excludedClassPrefixes` | 空列表 | 排除匹配前缀的类，优先于包含规则 |
| `methods` | 上述三个 getter | 只转换选中的方法；空集合不转换任何 getter |
| `failOnUnsupportedCalls` | `true` | 检测到不支持的 Google 常量成员访问时使构建失败 |

类名前缀使用点号形式，例如 `com.example.app.`，通过字符串前缀匹配。`methods` 填写方法名，不带括号；传入支持列表以外的方法名会导致构建失败。

未被选中的类、变体和方法保留原始调用。将 `failOnUnsupportedCalls` 设为 `false` 只会让不支持的成员访问保持原样，不会使这些调用获得兼容能力。

## 可选运行时 API

自动接入不需要调用这些 API。需要主动控制实现来源或跳过不可用效果时，可以使用 `cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat`。

| 策略 | 行为 |
| --- | --- |
| `GOOGLE_FIRST` | 默认值，Google 不可用时尝试小米 |
| `XIAOMI_FIRST` | 小米不可用时尝试 Google |
| `GOOGLE_ONLY` | 只使用 Google |
| `XIAOMI_ONLY` | 只使用小米 |
| `DISABLED` | 返回不可用结果 |

例如，在 UI 或相关库初始化之前修改全局策略：

```kotlin
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat as Haptics

Haptics.setPolicy(Haptics.Policy.XIAOMI_FIRST)
```

全局策略会影响后续通过兼容层获取常量的调用；已被业务代码或第三方库缓存的常量不会随之更新。

也可以只为某一次调用指定策略，不修改全局设置：

```kotlin
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat as Haptics

Haptics.getOrNull(Haptics.Effect.SCROLL_TICK, Haptics.Policy.GOOGLE_FIRST)
    ?.let { view.performHapticFeedback(it) }
```

`get(effect)` 和 `getOrNull(effect)` 使用当前全局策略；带 `Policy` 参数的重载使用本次指定的策略。前者在不可用时返回 `-1`，后者返回 `null`。

## 适配边界与排查

- **仅适配列出的三个 getter**：不支持的字段访问、方法签名或调用形式默认会在构建时报错，错误信息包含成员和调用方类名。
- **反射字符串不会被改写**：业务代码自行通过 `Class.forName()` 等方式查找 Google 类时，不属于自动适配范围；普通类字面量也不会被整体替换。
- **Kotlin 函数引用有明确范围**：当前额外处理直接继承 `FunctionReference`、`FunctionReferenceImpl` 或 `AdaptedFunctionReference` 的生成类，其他字节码形式应单独验证。
- **没有反馈时**：检查应用的 `wear-sdk` 清单声明、设备对应接口是否可调用，以及当前构建过滤条件和运行时策略是否覆盖该调用。两个 SDK 都不可用时返回无反馈结果属于预期行为。
- **提示不支持的成员时**：先根据错误定位调用点，再选择扩展兼容层或缩小转换范围；关闭严格检查会保留该成员的 Google 引用。
- **找不到插件或运行时依赖时**：检查 included build 的目录是否完整、`includeBuild` 是否位于 settings 顶层且相对路径正确，以及是否在实际 Android 模块中应用了插件。

运行时 JAR 自带 `META-INF/proguard/proguard-rules.pro`，包含 Google、小米目标类的成员保留规则及可选类缺失警告规则。发布构建仍应结合宿主项目的 R8 配置验证。

## 项目结构与构建

```text
miwearhaptics/
├── build.gradle.kts                 # Gradle 插件定义与构建依赖
├── settings.gradle.kts              # included build 与运行时子项目
├── src/main/java/cc/star0/wear/lib/
│   ├── miwearhapticsplugin.java      # 插件入口、依赖注入与变体注册
│   ├── WearHapticsExtension.java     # wearHaptics DSL
│   ├── WearHapticsInstrumentation.java
│   ├── WearHapticsClassVisitor.java  # 受支持调用的字节码重定向
│   └── KotlinCallableReferenceVisitor.java
├── lib/
│   ├── build.gradle.kts             # 纯 Java 运行时库
│   └── src/main/
│       ├── java/cc/star0/wear/lib/miwearhaptics/
│       │   ├── WearHapticFeedbackConstantsCompat.java
│       │   └── HapticConstantsResolver.java
│       └── resources/META-INF/proguard/proguard-rules.pro
├── README.md
└── AGENTS.md                         # 项目维护与协作约定
```

本项目未附带 Gradle Wrapper。可使用满足构建要求的本机 Gradle，在本目录运行 `gradle build`；也可以在接入项目的根目录复用其 Wrapper：

```powershell
.\gradlew.bat -p miwearhaptics build
.\gradlew.bat :app:assembleDebug
```

macOS / Linux 使用对应的 `./gradlew`。若应用模块不叫 `app`，请替换任务路径。需要覆盖插件编译使用的 AGP API 版本时，可向命令追加 `-PwearHapticsAgpVersion=<版本号>`。

构建通过可以验证编译和接入过程，实际触觉表现需要在目标设备上确认。维护本项目时请参阅 [AGENTS.md](AGENTS.md)。
