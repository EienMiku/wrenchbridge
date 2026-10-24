# Alpha 验证记录

验证日期：2026-10-24。所有游戏测试使用独立的本地开发服务端和测试世界，绑定回环地址。

当前 1.21.1 模拟版本的验证范围见 [alpha.2](alpha2-simulation.md)、[alpha.3](alpha3-interface.md)、[alpha.4](alpha4-expanded-lab.md)、[alpha.5 副手支持](alpha5-offhand.md)、[alpha.6 机器与叠层](alpha6-machines-overlay-recipe.md)、[alpha.7 模式意图](alpha7-shared-intent.md)、[alpha.8 容器原生菜单](alpha8-container-menus.md) 和 [alpha.9 原生反馈](alpha9-native-feedback.md)。下方涉及“交换”“借出”的描述保留早期 alpha.1 的测试记录；当前版本始终保留手中的通用扳手。验证服最大堆限制为 2 GiB，实际渲染验证使用独立离线客户端，最大堆为 4 GiB。

## 共同逻辑

JUnit 覆盖源工具筛选、危险非扳手工具排除、当前模式判断、枚举模式探测、未知枚举拒绝、旧版 `setState` 以及泛型擦除后的 `setMode(..., Object)`。探测过程不会试调用工具的使用动作。

## NeoForge 1.21.1 隔离测试

`src/verification` 是独立测试模组，不会包含在发行 JAR 中。测试在真实服务端线程和 `ServerPlayerGameMode.useItemOn` 流水线中执行。

- 无可选模组时可启动并完成注册、加载数据及测试。
- 跨模组扳手只进入一次目标方块的原生处理，原手持 ItemStack 在代理操作后恢复。
- BreakEvent 取消后不调用拆卸；配置模式阻止跨模组拆卸。
- 其他监听器显式禁用方块交互后，桥接入口不会继续代理使用。
- 菜单选择交换真实物品；伪造物品 ID、无效枚举不改变物品栏。
- 实际互通矩阵包含 20 个工具与目标组合：普通外来扳手、通用扳手，以及 AE2/Mekanism/Create/AnvilCraft 的原工具，作用于另外模组的代表性方块。每个组合都检查成功拆卸及 BreakEvent 拒绝后保留方块。
- 模式和保护检查在原生标签监听器之前执行，覆盖“原生模组已识别此工具并提前处理”的情况。同模组原工具保留原流程；AnvilCraft 原生锤子还依赖客户端自定义包，同模组流程不属于这组服务端模拟测试。

实际模组测试环境：

| 模组 | 版本 | 本次覆盖 |
| --- | --- | --- |
| AE2 | 19.2.18 | 无原生标签的外来扳手拆 `ae2:interface`；Mekanism 配置模式不拆它 |
| Mekanism | 10.7.19.85 | 外来扳手拆基础能量立方；探测并切换 WRENCH / CONFIGURATE_ITEMS |
| Create | 6.0.10 | 外来扳手通过 IWrenchable 拆传动杆 |
| AnvilCraft | 1.6.0+pre-release.11 | 外来扳手及 Mekanism WRENCH 模式通过原生锤子入口拆皇家钢块 |
| Sophisticated Backpacks | 3.26.9.2195 | 服务端发现内部工具，借出、子模式修改、归还、自定义组件保留，物品栏满时拒绝取出，原槽位被占用时保留工具 |
| Sophisticated Core | 1.5.7.2381 | 随精妙背包运行 |
| Curios | 9.5.1+1.21.1 | 同环境加载；装备栏容器路径的专门测试尚未完成 |
| GuideME | 21.1.19 | AE2 的依赖 |

这些测试验证上述具体行为。机器内部物品/能量的掉落保留、多方块、AE2 线缆的分部件拆除、各类领地插件、所有原生工具消耗、全部 GUI 操作和其余版本的游戏行为尚未逐项测试。

## 重现

1. 使用 JDK 21，在项目根运行 `gradlew :test :neoforge-1.21.1:compileVerificationJava`。
2. 在 `versions/neoforge-1.21.1/run-verification` 中自行准备接受过 EULA 的测试服配置。绑定 `127.0.0.1`，关闭公网访问，使用独立世界。
3. 基础测试不需要第三方模组。要重现实际兼容测试，将表中的模组及依赖放入该测试目录的 `mods/`。当前测试在探测到 Mekanism 时要求表中的 AE2/Create/AnvilCraft 同时存在；背包测试单独根据 Sophisticated Backpacks 是否存在启用。
4. 运行 `gradlew :neoforge-1.21.1:runVerificationServer`。测试结束会停止服务端。
5. 检查运行目录的 `verification-result.txt` 必须以 `PASS:` 开头，并检查此次日志时间。启动器可能在加载失败后返回退出码 0，单看 Gradle 成功不足以证明测试成功。
