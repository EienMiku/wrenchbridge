# Wrench Bridge

Share native dismantling protocols between wrench-like tools, or carry a universal wrench that borrows the functions of your real tools.

## What it does

- Sneak and use a supported block to dismantle it through its original mod's protocol.
- Press the configurable **G** key to select a carried native tool or one of its supported modes.
- Keep the universal wrench in your hand. The actual source tool supplies its data, energy, air, and durability.
- Use supported carried containers, including Sophisticated Backpacks, as tool sources.
- Open AE2 network statistics and card storage from a carried container, with card changes saved to the real tool.
- Use either hand, with the normal priority for meaningful main-hand actions.

The default shared mode preserves ordinary block interactions, such as opening a machine. Explicit native modes retain their intended rotation and configuration behavior. Ordinary right-click does not open the mode menu.

Craft the wrench with two ingots and two rods; mixed materials are allowed:

```text
 I 
 SI
S  
```

`I` is an ingot; `S` is a rod. Mixed materials work through the loader's common tags, or Ore Dictionary entries on 1.12.2.

## Installation and compatibility

Choose the file for your Minecraft version and loader. Install the same build on client and server; single-player needs only the client installation.

| Minecraft | Loader | Java | Build |
| --- | --- | --- | --- |
| 1.12.2 | Forge; **MixinBooter 11.18+ required** | 8 | alpha.10 |
| 1.16.5 | Forge | 8 | alpha.10 |
| 1.20.1 | Forge | 17 | alpha.10 |
| 1.21.1 | NeoForge 21.1.200+ | 21 | alpha.9 |
| 26.1.2 | NeoForge 26.1.2.122+ | 25 | alpha.10 |
| 26.2 | NeoForge 26.2.0.93+ | 25 | alpha.10 |

Supported integrations are optional. The 1.21.1 test lab includes AE2, Mekanism, Create, AnvilCraft, PneumaticCraft, Pretty Pipes, Refined Storage, Ender IO, Powah, RFTools, Integrated Dynamics, Modern Dynamics, Pipez, and Supplementaries. Native protocol coverage depends on the game/mod version.

Compatibility depends on each mod's native protocols. It does not make every block dismantleable. Pipez's wrench configures connections; Pretty Pipes' terminals and pressurizers still need normal mining. Unknown or denied targets are not force-destroyed as a fallback.

Alpha.9 passed 71 server feedback comparisons across 15 tools and 53 client scenarios, alongside existing protection, costs, inventory, offhand, and container-menu checks. These tests cover specific mod versions and representative actions. In the automated Refined Storage case, the original wrench also refuses dismantling, so only matching refusal is established there.

The five alpha.10 ports passed isolated server checks for hand routing, simulation, real costs, mode restoration, protection and container transactions. The Forge ports were checked with real AE2/Mekanism machines; 26.x was checked with real AE2. All five passed native AE2 card-menu checks with a full inventory and a real tool in a carried container. Broader client and overlay playtesting is still needed. See the [port verification record](https://github.com/EienMiku/wrenchbridge/blob/main/docs/alpha10-ports.md) for exact versions and scope.

Created by EienMiku, with AI-assisted development. MIT license. Source and issue tracker are linked above.

## 简体中文

通用扳手与原生扳手互通模组。潜行右键共享拆卸，G 键打开可改键的模式菜单；模拟模式借用随身真实工具的功能与消耗，通用扳手始终留在手中。支持主副手和部分随身容器来源。

提供 Forge 1.12.2、1.16.5、1.20.1 和 NeoForge 1.21.1、26.1.2、26.2 的独立构建。1.12.2 另需 MixinBooter 11.18+。新增五个平台为 alpha.10，已经移植完整模拟机制并通过隔离服务端检查；客户端画面与叠层仍需更多试玩。各第三方工具模组均为可选兼容对象，具体方块行为以原工具为准。

作者与原始材质：EienMiku。开发过程使用了 AI 辅助。
