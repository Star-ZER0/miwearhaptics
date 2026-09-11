# 项目维护指南

本文件供维护本项目的开发者与自动化协作工具参考。面向使用者的接入说明放在 [README.md](README.md)。修改时以实际源码和用户当前要求为准，保持文档与实现一致。

## 项目目标

`miwearhaptics` 通过 Gradle 构建期字节码转换和运行时适配，为原本调用 Google Wear 触觉常量的 Android 应用提供 Google / 小米兼容能力。

维护时保留两个核心体验：

1. 已配置 Wear SDK 的宿主项目，通过 `includeBuild("miwearhaptics")` 和应用 `cc.star0.wear.lib.miwearhaptics` 插件即可接入，无需改写原有业务调用。
2. 默认保持 `GOOGLE_FIRST`：Google 对应接口不可用时回退到小米接口；后续系统更新提供可调用的 Google 接口后，在新的应用进程中优先使用它。

不要将兼容逻辑改为只识别小米手表 5、只调用小米 SDK，或仅凭厂商字符串和系统版本判断能力。

## 仓库组成

| 路径 | 职责 |
| --- | --- |
| `build.gradle.kts` | 插件 ID、入口类、版本、Java 17 目标、AGP API 与 ASM 依赖 |
| `settings.gradle.kts` | 仓库配置；将 `lib` 映射为 `:miwearhaptics` 子项目 |
| `src/main/java/cc/star0/wear/lib/miwearhapticsplugin.java` | 注册 Android 应用/库变体、添加运行时依赖、传递转换参数 |
| `src/main/java/cc/star0/wear/lib/WearHapticsExtension.java` | `wearHaptics` 配置项及默认值 |
| `src/main/java/cc/star0/wear/lib/WearHapticsInstrumentation.java` | AGP 转换工厂、类范围过滤和缓存输入 |
| `src/main/java/cc/star0/wear/lib/WearHapticsClassVisitor.java` | 静态调用、方法句柄和动态常量的重定向及不支持成员检查 |
| `src/main/java/cc/star0/wear/lib/KotlinCallableReferenceVisitor.java` | Kotlin 函数引用生成类中的 owner 类引用修正 |
| `lib/build.gradle.kts` | Java 8 运行时库、源码 JAR 和 Maven 发布配置 |
| `lib/src/main/java/cc/star0/wear/lib/miwearhaptics/WearHapticFeedbackConstantsCompat.java` | 公开兼容 API、效果枚举和策略 |
| `lib/src/main/java/cc/star0/wear/lib/miwearhaptics/HapticConstantsResolver.java` | 反射解析、回退和按效果缓存 |
| `lib/src/main/resources/META-INF/proguard/proguard-rules.pro` | 可选 SDK 类及方法的混淆规则 |

## 构建与依赖约定

- 插件 ID 为 `cc.star0.wear.lib.miwearhaptics`，入口类为 `cc.star0.wear.lib.miwearhapticsplugin`。保留现有命名；确需修改入口时同步插件注册。
- 当前插件与运行时版本均为 `1.0.0`，运行时依赖字符串硬编码在插件入口的 `RUNTIME` 常量中。
- 当前运行时坐标为 `cc.star0.wear.lib:miwearhaptics:1.0.0`，通过 included build 的依赖替换解析到 `lib`。修改版本、group、子项目名或发布 artifactId 时，检查两个构建脚本、插件依赖字符串、settings 映射和 README 是否仍一致。
- 接入示例中的 `includeBuild("miwearhaptics")` 放在宿主 `settings.gradle.kts` 顶层，与 `include(":app")` 同级，让该构建同时参与项目插件解析和运行时库依赖替换；仅用于插件管理的引入方式不能替代此处的运行时依赖接入。
- 插件使用 Java 17；运行时库必须保持 Java 8 源码、字节码和标准库 API 兼容，保留 `options.release.set(8)`。
- AGP API 是 `compileOnly` 依赖，默认 `9.3.2`，由 `wearHapticsAgpVersion` 项目属性覆盖。当前 ASM / ASM Tree 为 `9.10.1`，JSpecify 为编译期依赖。
- included build 独立维护仓库与构建依赖，不依赖宿主的版本目录或固定应用包名。
- 运行时保持纯 Java，不引入 AndroidX、Android 编译依赖或设备 SDK 实现。Google / 小米类通过字符串反射访问。
- `wear-sdk` 清单声明由宿主应用管理；当前插件不生成或修改 AndroidManifest。

## 字节码转换约束

- 仅将 `com/google/wear/input/WearHapticFeedbackConstants` 上受支持的静态 `()I` getter 重定向到 `cc/star0/wear/lib/miwearhaptics/WearHapticFeedbackConstantsCompat`。
- 当前支持 `getScrollItemFocus`、`getScrollTick`、`getScrollLimit`。保持方法名和描述符一致，避免改变调用方的栈行为、装箱逻辑或控制流。
- 应用模块使用 `InstrumentationScope.ALL`，库模块使用 `InstrumentationScope.PROJECT`。不要假设库模块能够转换其所有外部依赖。
- 转换配置必须作为 Gradle 输入传入工厂；新增影响转换结果的参数时补充相应 `@Input`，确保缓存失效条件完整。
- `variants` 为空表示所有变体；包含类前缀为空表示不限制；排除前缀优先；`methods` 为空表示不转换任何 getter。
- 始终排除 `cc.star0.wear.lib.miwearhaptics.`、`com.google.wear.input.`、`com.xiaomi.miwear.input.`，避免改写解析器和设备 SDK 本身。
- 默认严格检查不支持的成员访问。关闭 `failOnUnsupportedCalls` 只保留原始访问，不扩展支持范围；已知但未选中的 getter 也保留 Google 调用。
- 维护方法句柄、`invokedynamic` bootstrap 参数及嵌套 `ConstantDynamic` 的递归处理，不能只覆盖直接调用。
- Kotlin 函数引用除了调用指令，还可能保存目标 owner 的类字面量。当前仅对直接继承 `FunctionReference`、`FunctionReferenceImpl`、`AdaptedFunctionReference` 的生成类补充处理；只有已适配且没有剩余 Google getter 调用时才改写 owner。
- 不要全局替换普通类字面量或字符串。业务代码自建的反射调用不在当前自动转换范围内。

## 运行时行为约束

- 默认全局策略是 `GOOGLE_FIRST`，其余策略为 `XIAOMI_FIRST`、`XIAOMI_ONLY`、`GOOGLE_ONLY` 和 `DISABLED`。带策略参数的调用不修改全局策略。
- 能力判断基于对应方法能否以无参数静态调用返回 `int`。当前实现接受成功返回的整数，不据此探测马达或触觉强度。
- 按 SDK 和效果分别缓存成功结果及不可用结果，保持线程安全。不要引入每次 UI 反馈都重复反射的行为。
- 方法缺失、链接错误及可恢复的反射调用失败应作为该 SDK 效果不可用处理，允许策略继续回退。被调用方法抛出的 `VirtualMachineError` 和 `ThreadDeath` 必须继续传播，避免扩大捕获为吞掉所有 `Throwable`。
- 无可用效果时，整数 API 返回 `NO_HAPTICS = -1`，可空 API 返回 `null`。不要用其他振动常量、固定厂商编号或自定义振动补位。
- 策略调整只影响后续常量读取，不会更新调用方已缓存的值；失败解析也会在当前解析器生命周期内缓存。文档不能承诺热切换会使所有已初始化 UI 立即变化。
- 扩展效果时，至少同步 getter 支持集合、兼容层公开 getter、`Effect` 枚举、混淆规则、相关验证与 README；新接口形式应单独评估字节码规则。

## 修改与验证流程

1. 阅读受影响的构建脚本、插件入口和运行时实现，确认改动属于构建期转换还是运行时解析；尽量保持改动集中。
2. 保持现有 Java 风格：四空格缩进、清晰的类型和方法命名；注释解释兼容原因及边界。
3. 只改文档时，对照源码核实配置名、默认值、接入路径、版本、支持范围和示例；无需为文档变更增加测试或运行完整 Android 构建。
4. 修改构建或 Java 代码时，先完成插件与运行时编译，再按影响范围验证调用转换、运行时回退和宿主接入。
5. 涉及公开 API、默认策略、依赖坐标、接入步骤或兼容范围的变更，同步更新 README；不要宣称未经验证的 AGP 版本、机型或固件已通过测试。

当前仓库没有 Gradle Wrapper，也没有自动化测试源码或测试依赖。在仓库目录可使用满足要求的本机 Gradle 执行：

```text
gradle build
```

如果仓库位于宿主工程的 `miwearhaptics/`，可以在宿主工程根目录执行：

```powershell
.\gradlew.bat -p miwearhaptics build
.\gradlew.bat :app:assembleDebug
```

macOS / Linux 使用 `./gradlew`；根据实际模块名替换 `app`。Gradle 和 JDK 版本需满足宿主 AGP 要求，必要时追加 `-PwearHapticsAgpVersion=<版本号>`。不要在本仓库根目录假设存在 `gradlew.bat` 或 Android `assembleDebug` 任务。

代码变更时根据受影响行为选择验证场景：

| 改动范围 | 重点验证 |
| --- | --- |
| 运行时解析与策略 | 仅 Google 可用、仅小米可用、两者均可用、均不可用；默认优先级和显式策略 |
| 反射与缓存 | 单个效果缺失、getter 抛出异常、部分实现可用、成功与失败缓存、致命错误继续传播 |
| 字节码转换 | 直接调用、Java 方法引用、Kotlin 函数引用；改动涉及句柄或动态常量时检查对应路径 |
| 筛选与严格检查 | 变体和类前缀选择、排除优先级、方法子集、空方法集合、未支持成员的严格与宽松行为 |
| 接入与发布 | included build 的插件解析和运行时依赖替换、应用依赖范围与库模块范围、R8 后反射可用性 |

按需要使用有针对性的 JVM 验证或宿主样例，不把编译成功等同于真机触觉验证。交付时简要说明修改内容、实际执行的验证及仍未验证的部分。
