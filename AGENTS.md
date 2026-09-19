# 项目维护与协作指南

本文件适用于整个仓库，供开发者与自动化协作工具使用。面向使用者的接入说明放在 [README.md](README.md)。维护时以实际代码和用户当前要求为准，不把已有文档中的兼容性描述当作测试证据。

## 工作原则

- 开始修改前查看 `git status --short` 和受影响文件。保留用户已有修改、删除和未跟踪文件，不擅自恢复、覆盖或清理。
- 先确认改动涉及 Gradle 转换、共享运行时还是 Xposed Hook，尽量保持修改集中。
- Java 使用四空格缩进，保留现有命名与风格；注释说明兼容原因、缓存和错误处理边界。
- 不提交构建目录、本机 SDK 路径、签名密钥或设备日志。`xposed/local.properties` 是本机配置。
- 公开 API、默认策略、支持效果、版本坐标、接入步骤或测试流程变化时，同步对应 README。

## 项目组成

仓库有两个独立 Gradle 构建，共享同一个 `lib/`：

| 位置 | 职责 |
| --- | --- |
| 根 `build.gradle.kts` / `settings.gradle.kts` | Gradle 插件构建；将 `lib/` 映射为 `:miwearhaptics` |
| `src/main/java/cc/star0/wear/lib/miwearhapticsplugin.java` | 插件入口、运行时依赖注入、Android 变体注册 |
| 同目录 `WearHapticsExtension.java` | `wearHaptics` DSL 与默认值 |
| 同目录 `WearHapticsInstrumentation.java` | AGP 转换工厂、类过滤与缓存输入 |
| 同目录 `WearHapticsClassVisitor.java` | 调用、方法句柄、动态常量转换与严格检查 |
| 同目录 `KotlinCallableReferenceVisitor.java` | Kotlin 函数引用中的 owner 类引用修正 |
| `lib/build.gradle.kts` | Java 8 目标、源码 JAR、名为 `maven` 的运行时发布配置 |
| `lib/src/main/java/cc/star0/wear/lib/miwearhaptics/` | `WearHapticFeedbackConstantsCompat` 公开 API 与 `HapticConstantsResolver` |
| `lib/src/main/resources/META-INF/proguard/proguard-rules.pro` | 可选设备 SDK 的混淆规则 |
| `xposed/build.gradle.kts` / `xposed/settings.gradle.kts` | 独立 Android 模块构建，直接依赖 `../lib/` |
| `xposed/src/main/java/cc/star0/wear/lib/miwearhaptics/` | `WearHapticsXposed` 入口与 `XiaomiFirstHook` 选择逻辑 |
| `xposed/src/main/resources/META-INF/xposed/` | Modern Xposed 入口、API 要求和作用域元数据 |
| `xposed/src/main/AndroidManifest.xml` / `res/` | 无启动组件的模块清单、双语名称和描述、图标 |
| `xposed/src/test/` | JUnit 回归测试及测试 SDK 桩 |

根构建不包含 `xposed/`。Xposed 构建不加载根目录的字节码转换插件。二者共用 `lib/build/` 输出，验证这两个构建时顺序执行，避免同时写入共享产物。

## 必须区分的行为

- **Gradle 插件 / 公开运行时默认 `GOOGLE_FIRST`**：Google getter 不可用时回退小米。不能为了 Xposed 的需求修改该默认值。
- **Xposed Hook 固定 `XIAOMI_FIRST`**：小米不可用时调用 Google 原始方法。不能通过修改兼容层全局策略实现 Hook 回退，否则可能再次进入 Hook。
- 两条路径都按方法实际可调用性判断能力，不按机型、厂商字符串或系统版本选择实现。
- 成功返回的任意 `int`（包括 `-1`、`0`）都是有效解析结果；只有不可用结果才触发回退。项目不探测马达、反馈强度或常量对应的实际效果。
- 不可用时返回 `NO_HAPTICS = -1`；可空 API 返回 `null`。不硬编码厂商常量，不用其他振动效果补位。

## 构建与依赖约定

- 插件 ID：`cc.star0.wear.lib.miwearhaptics`；入口：`cc.star0.wear.lib.miwearhapticsplugin`。保留现有小写入口类名，改名须同步注册。
- 插件和运行时当前版本均为 `1.0.0`。运行时坐标 `cc.star0.wear.lib:miwearhaptics:1.0.0` 硬编码在插件入口的 `RUNTIME` 中。
- 修改运行时 group、版本、项目名或 artifactId 时，核对根与 `lib` 构建脚本、`RUNTIME`、两个 settings 中的项目映射和接入文档。
- 宿主 settings 顶层的 `includeBuild("miwearhaptics")` 同时提供项目插件解析与运行时依赖替换，不能只保留插件管理中的引入。
- 插件 Java 目标为 17；共享运行时必须保持 Java 8 源码、字节码和标准库 API 兼容，保留 `options.release.set(8)`。
- 运行时保持纯 Java，不引入 Android / AndroidX 编译依赖或设备 SDK 实现。设备类通过字符串反射访问。
- 根插件 AGP API 为 `compileOnly`，默认 `9.3.2`，由 `wearHapticsAgpVersion` 覆盖；ASM / ASM Tree 为 `9.10.1`，JSpecify 为编译期依赖。根构建独立维护仓库，不读取宿主版本目录。
- Xposed 单独固定 AGP `9.3.2`，不受 `wearHapticsAgpVersion` 影响；compileSdk / targetSdk 36、minSdk 26、Java 8 目标，包名 `cc.star0.wear.xposed.miwearhaptics`。
- Xposed 的 `io.github.libxposed:api:102.0.0` 在正式构建中必须是 `compileOnly`，测试中才使用 `testImplementation`。框架 API 桩不能打包进模块 APK。
- Xposed Release 启用代码和资源压缩，当前未配置签名。保留入口规则，并核对最终 APK 中的入口名称与元数据。
- `wear-sdk` 清单声明和设备 SDK 可见性由宿主应用负责，插件与模块均不自动补充。
- 运行时可用 `gradle :miwearhaptics:publishToMavenLocal` 发布到本机。当前未配置远程 Maven 仓库或插件发布流程，不能将版本坐标描述为已公开发布。
- Xposed 的 versionName 为 `1.0.0`、versionCode 为 `1`；模块名称和描述分别位于 `values/strings.xml` 与 `values-zh/strings.xml`。调整模块定位、API 要求或名称时同步双语资源与 README。
- 根构建不需要 Android SDK；Xposed 与宿主 Android 工程各自配置 SDK。Java 8 编译目标不等于运行时已验证所有 Android 版本。

## 字节码转换约束

- 只将 `com/google/wear/input/WearHapticFeedbackConstants` 上三个受支持的静态 `()I` getter 重定向到兼容类：`getScrollItemFocus`、`getScrollTick`、`getScrollLimit`。
- 保持方法名、描述符、栈行为和控制流；应用模块为 `InstrumentationScope.ALL`，库模块为 `InstrumentationScope.PROJECT`。
- 影响转换结果的参数必须进入工厂的 Gradle `@Input`，保证缓存失效条件完整。
- `variants` 为空表示全部；包含前缀为空表示不限制；排除前缀优先；`methods` 为空不转换 getter，但不等于关闭严格成员检查。
- 始终排除 `cc.star0.wear.lib.miwearhaptics.`、`com.google.wear.input.`、`com.xiaomi.miwear.input.`。
- `enabled=false` 关闭转换，但在 Android 模块注册时仍添加运行时依赖。文档和测试不能把它描述为完全移除插件影响。
- 注册只监听 `com.android.application` / `com.android.library`。仅将插件应用到非 Android 根项目，不会递归配置子模块。
- 前缀使用区分大小写的 `startsWith`，变体使用完整名称匹配，不支持通配符。空列表与包含空字符串的列表不同，排除前缀 `""` 会排除全部类。
- 默认严格检查不支持的成员。宽松模式只保留原访问；已知但未选中的 getter 也保留 Google 调用。
- 不支持的访问包括字段、构造方法、实例方法及非法 getter 签名；普通调用要求 `INVOKESTATIC` 且非接口，方法句柄要求 `H_INVOKESTATIC` 且非接口。关闭严格检查不应放宽 `methods` 的合法名称校验。
- 保留方法句柄、`invokedynamic` bootstrap 及嵌套 `ConstantDynamic` 的递归处理，不能只覆盖普通调用。
- Kotlin owner 修正只针对直接继承 `FunctionReference`、`FunctionReferenceImpl`、`AdaptedFunctionReference` 的生成类，且要求已有适配调用、没有剩余 Google getter 调用。
- 不全局替换业务反射字符串或普通类字面量；新的字节码形式需单独评估。
- 严格检查不能发现已内联成普通常量或隐藏在反射字符串中的访问，也不是针对所有类型描述符的完整 Google 引用扫描。不要承诺转换后 Google 类一定可以从整个宿主中移除。

## 运行时与 Hook 约束

共享运行时：

- 保留五种策略：`GOOGLE_FIRST`、`XIAOMI_FIRST`、`GOOGLE_ONLY`、`XIAOMI_ONLY`、`DISABLED`。带策略参数的调用不修改全局策略。
- 按 SDK 与效果分别缓存成功值及不可用结果，保持线程安全，避免每次反馈重复反射。
- 方法缺失、链接错误和可恢复反射失败视为该 SDK 效果不可用；被调用方法抛出的 `VirtualMachineError`、`ThreadDeath` 必须传播，不吞掉所有 `Throwable`。
- 策略调整不清除解析缓存，不更新调用方已保存的值。文档不能承诺 UI 热切换或系统更新后在旧进程中自动刷新能力。
- `Effect` / `Policy` 参数均通过 `Objects.requireNonNull` 检查，包括 `DISABLED` 调用；可空 API 的 `null` 仅表示不可用，成功返回 `-1` 仍是非空值。
- 公开 API 使用兼容类自身的类加载器；不提供注入类加载器、清空缓存或查询实际命中 SDK 的接口。静态策略和解析器属于该类的加载实例，不是跨进程或设备级设置。
- 解析惰性执行，以 SDK 和效果为键缓存值或不可用状态，没有独立的全局 `Class` / `Method` 查找缓存。保留同步和 `volatile` 策略；`DISABLED` 不触发 SDK 解析。
- 运行时使用 `Class.forName(..., true, loader)` 和 `getMethod`，查找公开方法并可能初始化类；与 Hook 安装阶段的查找语义不同。

Xposed 路径：

- 使用 Modern API 102 的 `onModuleLoaded` / `onPackageReady` 生命周期，在目标最终类加载器中解析 SDK，不用模块自身类加载器替代。
- 查找 Google 类时保持 `Class.forName(..., false, loader)`，避免在安装 Hook 前主动初始化 SDK。
- 使用 `getDeclaredMethod`，仅 Hook Google 类自身声明的公开、静态、非抽象、无参数且返回 `int` 的 getter，不搜索继承方法。Google 类完全不存在时跳过，不宣称可注入缺失 SDK。
- 跳过模块自身、`android` 包和所有 `system_server` 回调；同一进程的共享 `Method` 不重复 Hook。
- 小米侧通过 `Policy.XIAOMI_ONLY` 解析；Google 回退必须使用 `Invoker.Type.ORIGIN`，不能改用反射调用已 Hook 的 Google getter 或继续 Hook 链。
- 保留 `ExceptionMode.PASSTHROUGH`，避免框架保护模式吞掉致命错误。
- 保留同步、按效果缓存及可重入保护；小米 getter 内再次调用 Google 时直接执行原始 getter。异常后必须恢复递归保护状态。
- 重入保护属于 `XiaomiFirstHook`，不能据此声称公开运行时也有相同保护。同步监视器保证其他线程不会把一次正在进行的解析误判为同线程递归。
- 模块不依赖 Root 命令、远程配置或伴随服务；维护时同时考虑兼容 API 102 的 Root 框架与 JingMatrix/LSPatch 内嵌路径。
- `java_init.list` 指向 `cc.star0.wear.lib.miwearhaptics.WearHapticsXposed`；`module.prop` 当前为 min/target API 102、`staticScope=false`、`autoHotReload=false`；`scope.list` 为空。入口调整须同步元数据和 `xposed/proguard-rules.pro`。
- 模块与运行时使用相同 Java 包以复用包内 API，但 Android applicationId 不同；不要只改其中一处而破坏访问或自身过滤。
- 日志通过框架 `log` 接口输出，标签为 `MiWearHaptics`；`Installed N` 只统计本次新增注册，不证明小米 SDK 已解析或触觉有效。共享运行时和 Hook 选择器不记录每次解析/回退日志。
- 缺失或安装失败的 getter 不受模块保护；`ORIGIN` 会绕过同一方法的 Hook 链。与其他模块或本项目 Gradle 转换叠加时需单独验证，不能把各自的优先级文档当作组合行为保证。

新增效果时同步检查 `SDK_GETTERS`、公开 getter、`Effect`、混淆规则、两条路径的测试及 README。Xposed 按 `Effect.values()` 注册，新增枚举会直接影响 Hook 范围。

## 验证流程

仅修改文档时，对照源码核对配置名、默认值、版本、路径、任务、示例及链接，运行 `git diff --check`；无需因此新增测试或执行完整 Android 构建。

代码变更按影响范围选择以下命令，均从仓库根目录执行。本仓库没有 Gradle Wrapper，命令中的 `gradle` 表示满足相应 AGP 要求的本机安装：

```powershell
# 插件与共享运行时
gradle build

# Xposed 及共享运行时相关回归
gradle -p xposed testDebugUnitTest assembleDebug assembleRelease
```

也可从宿主根目录用 `.\gradlew.bat -p miwearhaptics build`，Xposed 使用 `.\gradlew.bat -p miwearhaptics/xposed ...`；macOS / Linux 使用 `./gradlew`。根构建没有 Android `assembleDebug` 任务，`xposed/` 本身就是模块根项目，不使用 `:app:` 前缀。

现有测试与验证边界：

| 范围 | 应验证的行为 |
| --- | --- |
| `XiaomiFirstHookTest` | 三效果优先级、回退、缺失/异常/非法签名、成功与失败缓存、类加载器、并发、递归、致命错误、运行时默认策略 |
| `WearHapticsXposedTest` | Modern API 回调、ORIGIN 调用器、PASSTHROUGH、重复注册、排除进程、Google 类缺失；不模拟 ART Hook |
| 实际框架验证 | 仓库没有独立 ART 集成测试应用；按需在目标应用与框架中验证小米优先、Google 回退、均不可用、部分可用、小米委托 Google |
| 根插件转换 | 直接调用、Java 方法引用、Kotlin 函数引用、动态常量、类/变体/方法筛选、严格与宽松行为 |
| 宿主接入与发布 | included build 插件解析、运行时依赖替换、第三方依赖覆盖、R8 后反射和入口可用性 |

根插件和 `lib/` 当前无独立测试源码；不能将 `gradle build` 描述为完整行为测试。根据代码变更风险补充有针对性的验证，使用宿主构建验证实际接入。

现有 JVM 测试分别为 `XiaomiFirstHookTest` 15 个、`WearHapticsXposedTest` 7 个；修改用例时同步 README 中的数量和覆盖说明。前者用人工 SDK 类及回调测试选择逻辑，后者通过动态代理模拟框架并在测试中调用 `attachFramework`；正式模块不主动调用它。框架适配测试里的反射原始调用不等于真实 ART Hook。

`HapticConstantsResolver` 通过 Xposed 测试被间接覆盖，但公开 API 的全部策略、参数校验和切换场景没有独立完整测试。新增相关行为时补充针对性回归，不能仅依赖现有 Hook 测试。

JUnit HTML 报告位于 `xposed/build/reports/tests/testDebugUnitTest/index.html`，XML 位于 `xposed/build/test-results/testDebugUnitTest/`。可用 `--tests 'cc.star0.wear.lib.miwearhaptics.XiaomiFirstHookTest'` 或对应的 `WearHapticsXposedTest` 筛选用例。报告属于本次执行的证据；已有构建产物或日志不能当作当前修改的验证结果。

人工 Google / 小米 SDK 桩只能留在测试源码中，不能进入正式模块。实际框架验证每个场景使用新进程清除缓存，并记录模块、框架、系统、目标应用和设备号；不将 JVM 测试通过写成 Root 框架或 LSPatch 已验证。

编译、JVM 单元测试、ART Hook 集成测试和真机触觉测试是不同层面的证据。交付时说明实际执行了什么、结果如何，以及尚未验证的部分；不要宣称未经验证的框架版本、机型或固件已兼容。
