# 通用扳手适配调查

调查日期：2026-10-24。主要目标：Minecraft 1.21.1 / NeoForge。本文保存实现前的源码调查和建议；当前实现与实际验证范围以 README 和 verification.md 为准。

选取常见科技模组及较新科技模组，按工具识别、当前模式、原生交互和目标拆卸入口分别检查。这里不按下载量排名；“可以适配”表示找到可复用的实现路径，不表示已经验证所有方块、附属模组和版本。

必须区分两件事：一把工具可以被赋予共享拆卸能力；某个模组自己的设备是否本来就有可复用的拆卸能力。两者不等价。

**适合优先实现的目标协议**

| 模组与工具 | 源码依据与原生行为 | 适配结论 | 需要验证的重点 |
| --- | --- | --- | --- |
| AE2：石英扳手、网络工具 | `InteractionUtil` 通过 `c:tools/wrench` 判断；`WrenchHook` 处理潜行拆卸和权限；网络工具有旋转例外 | 可接入统一的工具判定，并保留原有块实体拆卸分派 | 线缆、总线、面板和覆盖板的精确命中；升级和存储；网络权限 |
| Mekanism：配置器 | `ItemConfigurator.canPerformAction` 在 WRENCH 模式提供 `wrench_dismantle`；设备通过 `tryWrench` 拆卸或旋转 | 能力识别可直接复用；其他扳手补足认可路径后使用原流程 | 配置、清空、旋转模式的隔离；安全权限；放射性限制；不同机器与传输器 |
| Create：扳手及采用相同接口的附属工具 | `IWrenchable.onSneakWrenched`；`WrenchEventHandler` 已接受通用扳手标签 | 一条接口适配可覆盖实现该接口的许多方块；仍须处理特例 | 原生工具与第三方标签工具的事件路径不同；皮带、轨道、覆盖块和附属模组重写行为 |
| AnvilCraft：铁砧锤及变体 | 独立的 `AnvilHammerItem` 流程；可拆块由 `HAMMER_REMOVABLE` 或 `IHammerRemovable` 决定 | 需要专用桥接；可通过小范围访问桥调用原拆卸入口 | 多部件主方块定位；容器和流体数据；禁止拆卸规则；不要代理整段锤子使用逻辑而触发其他技能 |
| Refined Storage 2：扳手 | v2.0.9 的 `AbstractBaseBlock.tryUseWrench` 接受 `c:tools/wrench`，并分派到拆卸或旋转 | 通用标签与动态模式判定桥即可覆盖基础路径 | 网络构建权限；设备配置、物品及存储数据；客户端与服务端一致 |
| Ender IO：Yeta Wrench | `WrenchCompat` / `WrenchableBlockHandler` 通过通用标签将交互交给块实体的 `Wrenchable` | 已有第三方扳手兼容基础，可复用事件和接口 | 管束中按命中位置只移除一根管道；连接切换与移除的区别 |
| PneumaticCraft：气动扳手 | `IWrenchRegistry` 可注册工具；`IPneumaticWrenchable` 提供行为；已有第三方扳手事件及网络包；原生扳手还有压力消耗 | 有公开兼容入口，不必猜类名；需要同时验证事件及网络路径 | 原工具的压力消耗；第三方工具的原生规则；状态保留；客户端网络包；多方块及实体行为 |
| RFTools / McJtyLib 系：Smart Wrench | `WrenchChecker` 识别 `c:tools/wrench` 和自身工具；`BaseBlock` 将正常、潜行、选择模式分开处理 | 可覆盖采用该基础类的设备；Smart Wrench 作为来源时检查模式 | NORMAL / SELECT；某些来源标签仍使用历史 `forge` 命名；目标子类可重写拆卸 |
| Powah：扳手 | `Wrench.removeWithWrench` 接受扳手标签，对能源方块与充能球提供潜行拆卸；原工具还有其他模式 | 可复用标签判断和现成拆卸路径 | 原工具模式；方块实体数据和掉落；权限与事件 |
| Integrated Dynamics：Menril Wrench | `WrenchHelpers` 使用 `c:tools/wrench`；线缆按命中部件交互；工具现在还有偏移和配置模式 | 通用标签有基础覆盖；来源侧必须识别 DEFAULT 与其他模式 | 线缆、部件、伪装；复制配置和保存偏移不能被拆卸拦截 |
| Modern Dynamics：扳手 | `WrenchHelper.handleEvent` 在使用前阶段处理带标签工具的潜行拆管 | 已有通用路径，适合首版覆盖 | 管道连接、附件、过滤设置与掉落；交互事件阶段 |
| Pretty Pipes：扳手 | `WrenchItem.useOn` 已包含完整行为：潜行时优先移除覆盖板，否则移除管道 | 可对已确认目标使用原生工具行为或专用桥；适合验证受控代理 | 覆盖板优先级；副手方块放置；附件和内容物；当前工具与交互上下文的一致性 |

以上分组按源码中的基础入口归纳。不能据此宣称所有 Create 附属模组或所有 McJty 模组的每一种设备都已支持。

**需要保留原有操作的扳手类似物**

| 模组与工具 | 核对到的行为 | 实现处理 |
| --- | --- | --- |
| Pipez：扳手 | 承认 `c:tools/wrench` 与 `c:wrenches`；`PipeBlock.onWrenchClicked` 的潜行操作主要是连接、断开与抽取设置 | 能作为共享拆卸工具来源；不把其原生潜行操作解释为移除管道。增加一键拆 Pipez 是额外功能 |
| GTCEu Modern：普通、电动扳手 | 使用 NeoForge ItemAbility，包含 `wrench_dismantle`、`wrench_rotate` 等；基础机器的潜行扳手交互用于调整正面；工具、覆盖板和管道还有独立协议 | 来源工具可以按能力自动识别；目标侧分别处理机器、管道和覆盖板，保留工具消耗与原规则。能力名字不能独自证明右键会整机收取 |
| Modern Industrialization：扳手 | 普通机器的 `OrientationComponent.useWrench` 在潜行时设置输出方向，非潜行时设置机器朝向 | 能作为共享拆卸工具来源；普通机器的现成扳手入口是配置入口。若要潜行一键收机器，需要另加明确的拆卸功能 |
| Immersive Engineering：工程师锤 | 工具通过 `onItemUseFirst` 处理多方块成型、锤交互和旋转；有自身的锤标签与接口 | 可明确识别该工具；多方块成型、解体及普通拆方块分别建规则，不推断为潜行收取整座机器 |
| Supplementaries：扳手 | 主体行为是旋转；潜行影响旋转方向；会消耗耐久 | 可以赋予跨模组拆卸能力，但同一手势已有旋转含义，需要原生目标优先或明确的通用拆卸模式 |
| Just Dire Things：Ferricore Wrench | 工具旋转方块，还用于连接 Block Swapper；潜行对空气清除绑定 | 有通用标签，可作为来源；保留旋转、绑定和清除绑定。核对的基础机器与工具入口未提供通用的潜行收机流程 |
| LaserIO：Laser Wrench | 带通用扳手标签；使用物品的 `use` 做远距选点和节点连接，潜行用于保存或清除连接位置 | 可赋予跨模组拆卸能力；对 LaserIO 本身优先保留连接操作。不能只调用 `useOn` 就认为复现了原工具 |
| Actually Additions：Laser Wrench | 当前 1.21.1 主线的 `ItemLaserWrench.useOn` 用于记录和连接激光中继 | 名字可产生候选；默认不把连接行为代理成拆卸。可通过配置或专用规则加入共享工具来源 |
| Flux Networks：Flux Configurator | 潜行复制设备配置，普通使用粘贴配置或打开菜单；已有访问权限检查 | 默认为配置工具；不按 `configurator` 关键词自动授予拆卸优先级。允许用户明确加入来源 |
| Industrial Foregoing：Infinity Hammer | 该 Hammer 是战斗工具，右键会产生攻击技能；并非由名称可确认的设备扳手 | 排除仅因 `hammer` 命中而产生的自动兼容。不能借用其原始右键行为来试拆设备 |

“原功能不是拆卸”不意味着这些物品永远不能使用本模组的共享能力，而是要求先明确它们的模式与交互优先级。

**历史版本的著名工具**

Thermal 的 Crescent Hammer / Wrench 本轮核对的是 1.20.1 的 ThermalCore。它采用 CoFH 的 `IWrenchable` 及工具模式。可以为 1.20.1 分支做适配；这份旧版源码不能作为 1.21.1 已适配的证据。BuildCraft、IC2 等历史工具不纳入本轮 1.21.1 的支持承诺。

**对实现方案的影响**

1. 使用注册表中的物品标签作为自动发现依据，兼顾 `c:tools/wrench`、`c:wrenches` 及已知历史标签。发现结果是工具候选集合，不等价于当前模式允许拆卸。
2. 实时询问物品栈的 ItemAbility。Mekanism 与 GTCEu 都使用 `ItemAbility.get("wrench_dismantle")` 等共同名字；可覆盖不同材质和变体，而无需维护完整物品 ID 列表。不要长期缓存某个物品栈的模式判定。
3. 对已知多模式工具提供模式解析器，例如 Mekanism WRENCH、Smart Wrench NORMAL、Integrated Dynamics DEFAULT。配置、复制、选择等模式保留原行为。
4. 按目标协议选择一个处理器。优先让目标原生的拆卸流程执行；仅使用已经确认的接口、事件路径或受控工具代理。未知类型不在真实设备上逐个试工具。
5. 给原生手势有冲突的工具提供明确策略：在其自身功能作用范围内优先保留原交互；跨模组补共享拆卸；如需覆盖原交互，使用可见的通用拆卸模式。
6. 不全局修改所有扳手相关标签查询以假装所有模式都能拆卸；在适配的交互入口做动态判定。标签仍可用于静态的自动发现。
7. 区分“无此协议”“已处理”“明确拒绝”“异常”。处理成功或明确拒绝后不继续其他处理器。原生 SUCCESS / PASS 并不总能证明设备被移除或世界未变化。
8. 复用原生流程也要检查权限事件。部分实现直接调用 `setBlockAndUpdate(AIR)` 或 `removeBlock`，不能仅凭复用了原代码就保证经过标准破坏事件。涉及主部件、局部元件或多部件时，分别验证实际受影响位置。
9. 为启发式生成诊断记录：工具为何被识别、模式判定、命中的目标协议及未覆盖原因。未知名称只作为候选提示；避免把每次右键都变成全量扫描。静态候选随标签重载更新，模式按当前栈读取。

从源码调查推断，最有价值的首版是“通用标签和能力发现 + 一批可复用目标协议 + 少量模式适配”。陌生工具代理可作为实验层，不能预先宣称覆盖任何模组。

**首版验证范围**

先完成 AE2、Mekanism、Create、AnvilCraft 之间互通，并为已有通用路径的 Refined Storage、Ender IO、Powah、RFTools、Integrated Dynamics、Modern Dynamics 增加验证场景。随后处理气动工艺的网络/压力路径和 Pretty Pipes 的覆盖板优先级。

测试按协议设计，不穷举所有工具与所有方块的笛卡尔积：

- 各类工具的有效拆卸模式与配置模式分别取样；包含同一工具切换模式后立即再次使用。
- 生存与创造模式；主副手；客户端与独立服务端；每次交互只执行一次。
- 有库存、流体、能量、升级和自定义配置的设备；完整清点实际掉落，不要求目标模组本来不保留的数据突然被保留。
- AE2/Ender IO/Integrated Dynamics 的局部元件，Create/AnvilCraft 的特殊结构，Pretty Pipes 覆盖板。
- 禁止拆卸、无访问权限、受保护区域、放射性设备；原处理拒绝后不得改走另一条移除路径。
- 原生旋转、连接、复制和多方块操作没有被静默变成拆卸；未知工具没有真实世界试探行为。

**主要源码索引**

下列链接对应具体的源码入口；本地缓存的 GitHub 仓库文件另外记录了调查时的提交 SHA。分支名相似不代表 Minecraft 版本相同：Integrated Dynamics 使用 `master-1.21-lts`；Powah 使用 `1.21.1`；Refined Storage 使用 v2.0.9，未将其当前主线混入本轮结论。

| 模组 | 直接依据 |
| --- | --- |
| AE2 | [InteractionUtil](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.18/src/main/java/appeng/util/InteractionUtil.java)、[WrenchHook](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.18/src/main/java/appeng/hooks/WrenchHook.java) |
| Mekanism | [配置器](https://github.com/mekanism/Mekanism/blob/v1.21.1-10.7.19.85/src/main/java/mekanism/common/item/ItemConfigurator.java)、[设备扳手入口](https://github.com/mekanism/Mekanism/blob/v1.21.1-10.7.19.85/src/main/java/mekanism/common/tile/base/TileEntityMekanism.java) |
| Create | [WrenchEventHandler](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/content/equipment/wrench/WrenchEventHandler.java)、[IWrenchable](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/content/equipment/wrench/IWrenchable.java) |
| AnvilCraft | [AnvilHammerItem](https://github.com/Anvil-Dev/AnvilCraft/blob/dev/1.21/1.6/src/main/java/dev/dubhe/anvilcraft/item/AnvilHammerItem.java)；本地对照 1.6.0 pre-release.11 反编译 |
| Refined Storage | [AbstractBaseBlock v2.0.9](https://github.com/refinedmods/refinedstorage2/blob/v2.0.9/refinedstorage-common/src/main/java/com/refinedmods/refinedstorage/common/support/AbstractBaseBlock.java) |
| Ender IO | [WrenchableBlockHandler](https://github.com/Team-EnderIO/EnderIO/blob/1.21.1/enderio/src/main/java/com/enderio/enderio/foundation/block/WrenchableBlockHandler.java)、[WrenchCompat](https://github.com/Team-EnderIO/EnderIO/blob/1.21.1/enderio/src/main/java/com/enderio/enderio/foundation/integrations/WrenchCompat.java) |
| PneumaticCraft | [IWrenchRegistry](https://github.com/TeamPneumatic/pnc-repressurized/blob/1.21/src/api/java/me/desht/pneumaticcraft/api/wrench/IWrenchRegistry.java)、[AbstractPneumaticCraftBlock](https://github.com/TeamPneumatic/pnc-repressurized/blob/1.21/src/main/java/me/desht/pneumaticcraft/common/block/AbstractPneumaticCraftBlock.java)、[PneumaticWrenchItem](https://github.com/TeamPneumatic/pnc-repressurized/blob/1.21/src/main/java/me/desht/pneumaticcraft/common/item/PneumaticWrenchItem.java) |
| McJtyLib / RFTools | [WrenchChecker](https://github.com/McJtyMods/McJtyLib/blob/1.21_neo/src/main/java/mcjty/lib/varia/WrenchChecker.java)、[BaseBlock](https://github.com/McJtyMods/McJtyLib/blob/1.21_neo/src/main/java/mcjty/lib/blocks/BaseBlock.java)、[SmartWrenchItem](https://github.com/McJtyMods/RFToolsBase/blob/1.21_neo/src/main/java/mcjty/rftoolsbase/modules/various/items/SmartWrenchItem.java) |
| Powah | [拆卸工具帮助类](https://github.com/Technici4n/Powah/blob/1.21.1/src/main/java/owmii/powah/util/Wrench.java)、[WrenchItem](https://github.com/Technici4n/Powah/blob/1.21.1/src/main/java/owmii/powah/item/WrenchItem.java) |
| Integrated Dynamics | [WrenchHelpers](https://github.com/CyclopsMC/IntegratedDynamics/blob/master-1.21-lts/src/main/java/org/cyclops/integrateddynamics/core/helper/WrenchHelpers.java)、[ItemWrench](https://github.com/CyclopsMC/IntegratedDynamics/blob/master-1.21-lts/src/main/java/org/cyclops/integrateddynamics/item/ItemWrench.java)、[BlockCable](https://github.com/CyclopsMC/IntegratedDynamics/blob/master-1.21-lts/src/main/java/org/cyclops/integrateddynamics/block/BlockCable.java) |
| Modern Dynamics | [WrenchHelper](https://github.com/Technici4n/Modern-Dynamics/blob/master/src/main/java/dev/technici4n/moderndynamics/util/WrenchHelper.java) |
| Pretty Pipes | [WrenchItem](https://github.com/Ellpeck/PrettyPipes/blob/1.21/src/main/java/de/ellpeck/prettypipes/items/WrenchItem.java) |
| Pipez | [项目的配置操作说明](https://github.com/henkelmax/pipez#configuring-pipes)；本地对照 WrenchItem 与 PipeBlock 反编译 |
| GTCEu | [GTItemAbilities](https://github.com/GregTechCEu/GregTech-Modern/blob/1.21/src/main/java/com/gregtechceu/gtceu/common/data/item/GTItemAbilities.java)、[GTToolType](https://github.com/GregTechCEu/GregTech-Modern/blob/1.21/src/main/java/com/gregtechceu/gtceu/api/item/tool/GTToolType.java)、[MetaMachine](https://github.com/GregTechCEu/GregTech-Modern/blob/1.21/src/main/java/com/gregtechceu/gtceu/api/machine/MetaMachine.java) |
| Modern Industrialization | [OrientationComponent](https://github.com/AztechMC/Modern-Industrialization/blob/1.21.x/src/main/java/aztech/modern_industrialization/machines/components/OrientationComponent.java)、[机器入口](https://github.com/AztechMC/Modern-Industrialization/blob/1.21.x/src/main/java/aztech/modern_industrialization/machines/MachineBlockEntity.java) |
| Immersive Engineering | [HammerItem](https://github.com/BluSunrize/ImmersiveEngineering/blob/1.21.1/src/main/java/blusunrize/immersiveengineering/common/items/HammerItem.java) |
| Supplementaries | [WrenchItem](https://github.com/MehVahdJukaar/Supplementaries/blob/1.21.1/common/src/main/java/net/mehvahdjukaar/supplementaries/common/items/WrenchItem.java) |
| Just Dire Things | [FerricoreWrench](https://github.com/Direwolf20-MC/JustDireThings/blob/1.21.1/src/main/java/com/direwolf20/justdirethings/common/items/FerricoreWrench.java)、[BaseMachineBlock](https://github.com/Direwolf20-MC/JustDireThings/blob/1.21.1/src/main/java/com/direwolf20/justdirethings/common/blocks/baseblocks/BaseMachineBlock.java) |
| LaserIO | [LaserWrench](https://github.com/Direwolf20-MC/LaserIO/blob/main/src/main/java/com/direwolf20/laserio/common/items/LaserWrench.java)、[LaserNode](https://github.com/Direwolf20-MC/LaserIO/blob/main/src/main/java/com/direwolf20/laserio/common/blocks/LaserNode.java) |
| Actually Additions | [ItemLaserWrench](https://github.com/Ellpeck/ActuallyAdditions/blob/main/src/main/java/de/ellpeck/actuallyadditions/mod/items/ItemLaserWrench.java) |
| Flux Networks | [ItemFluxConfigurator](https://github.com/SonarSonic/Flux-Networks/blob/1.21/src/main/java/sonar/fluxnetworks/common/item/ItemFluxConfigurator.java) |
| Industrial Foregoing | [ItemInfinityHammer](https://github.com/InnovativeOnlineIndustries/Industrial-Foregoing/blob/1.21/src/main/java/com/buuz135/industrial/item/infinity/item/ItemInfinityHammer.java) |
| Thermal 1.20.1 | [WrenchItem](https://github.com/CoFH/ThermalCore/blob/1.20.1/src/main/java/cofh/thermal/core/common/item/WrenchItem.java) |

本地可追溯索引：`.tools-wrench-survey/source-index.json`。该索引包含下载源码的仓库、ref、固定提交、源文件地址与本地缓存路径；包含为排除版本误判而检查的其他分支。最终结论以本文指定的目标版本为准。
