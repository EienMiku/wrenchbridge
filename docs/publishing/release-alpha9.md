# Wrench Bridge 0.1.0-alpha.9 — Minecraft 1.21.1 / NeoForge

First public alpha preview of Wrench Bridge, a universal wrench and native-tool compatibility bridge.

## Features

- Share supported dismantling protocols between wrench-like tools.
- Use the universal wrench in either hand and press the configurable **G** key to select a simulated native tool or sub-mode.
- Keep the universal wrench in hand while the carried source tool supplies its data, energy, air, and durability.
- Borrow tools from supported carried containers, including Sophisticated Backpacks. AE2 network statistics and card storage support container sources and full inventories.
- Keep normal block interactions in the default shared mode, and retain native rotation/configuration in explicitly selected modes.
- Restore missing native sounds and block effects, avoid duplicate AnvilCraft swings, and let eligible offhand actions pass an empty main hand.

## Install

Download `wrenchbridge-neoforge-1.21.1-0.1.0-alpha.9.jar`. Requires **Minecraft 1.21.1, NeoForge 21.1.200+, and Java 21**; tested with NeoForge 21.1.248. Install on both the client and server. In single-player, install in the client. Supported third-party mods are optional.

This release provides the tested 1.21.1 implementation. Other source ports in the repository retain alpha.1 behavior and have build-only validation.

## Validation and limits

71 server feedback comparisons across 15 tools and 53 client scenarios passed, alongside existing protection, costs, inventory conservation, offhand, and container-menu checks. Coverage is for specific pinned mod versions and representative actions. In the automated Refined Storage case, the original wrench also refuses dismantling; only matching refusal has been verified there.

Use each mod's native behavior as the reference: Pipez's wrench configures connections, and Pretty Pipes' terminals and pressurizers still require normal mining. Unknown blocks are never force-destroyed as a dismantling fallback.

MIT license. Source and source JAR downloads are provided.

中文说明：本次公开的是 1.21.1 / NeoForge 的 alpha.9。G 切换模拟模式，潜行右键共享拆卸；模拟模式需要携带原工具。详细使用、配方和已知限制见仓库中文 README。
