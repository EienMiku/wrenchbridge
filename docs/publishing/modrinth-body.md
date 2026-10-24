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

`I` is any `c:ingots` ingredient; `S` is any `c:rods` ingredient.

## Installation and compatibility

This alpha release targets **Minecraft 1.21.1, NeoForge 21.1.200+, and Java 21**. It was tested with NeoForge 21.1.248. Install the same version on the client and server; single-player needs only the client installation.

Supported integrations are optional. The current test lab includes AE2, Mekanism, Create, AnvilCraft, PneumaticCraft, Pretty Pipes, Refined Storage, Ender IO, Powah, RFTools, Integrated Dynamics, Modern Dynamics, Pipez, and Supplementaries.

Compatibility depends on each mod's native protocols. It does not make every block dismantleable. Pipez's wrench configures connections; Pretty Pipes' terminals and pressurizers still need normal mining. Unknown or denied targets are not force-destroyed as a fallback.

Alpha.9 passed 71 server feedback comparisons across 15 tools and 53 client scenarios, alongside existing protection, costs, inventory, offhand, and container-menu checks. These tests cover specific mod versions and representative actions. In the automated Refined Storage case, the original wrench also refuses dismantling, so only matching refusal is established there.

## Development disclosure

EienMiku originated the project, directed the interaction and feature decisions, painted the original pixel-art texture, and repeatedly tested builds in-game and supplied compatibility feedback. Most implementation code, automated verification, and documentation were generated with OpenAI Codex. The current texture is based on the author's artwork with small assisted pixel refinements. The mod does not call AI services at runtime.

MIT license. Source, issue tracker, and detailed compatibility notes are available in the linked GitHub repository.

## 简体中文

通用扳手与原生扳手互通模组。潜行右键共享拆卸，G 键打开可改键的模式菜单；模拟模式借用随身真实工具的功能与消耗，通用扳手始终留在手中。支持主副手和部分随身容器来源。

当前公开版本为 1.21.1 / NeoForge alpha；各第三方模组均为可选兼容对象。具体方块行为以原工具为准，未知或拒绝的目标不会被强制删除。

项目创意、功能取舍、原始材质与持续游戏实测由 EienMiku 主导，主要代码与文档使用 Codex 编写。模组运行时不使用 AI 服务。
