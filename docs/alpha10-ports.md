# Alpha.10 ports / 多版本移植

The Forge 1.12.2, 1.16.5, 1.20.1 and NeoForge 26.1.2, 26.2 ports now use the held universal-wrench simulation design. They no longer switch the selected inventory slot to a native tool. NeoForge 1.21.1 remains at alpha.9.

新增五个移植版均为 alpha.10。通用扳手留在原手中，模拟功能借用随身真实工具及其消耗，并在调用后恢复工具原模式。模式菜单只修改通用扳手；其它模组的工具不会互相转换。

## Included behavior

- Shared dismantling without a donor; default ordinary use preserves the block's normal interaction.
- Explicit native functions, remembered per-tool modes, real costs and donor data.
- Configurable G key, adaptive searchable palette, original tool/mode translation components.
- Main/offhand routing, main-hand priority, stale-hand packet rejection and one action per use.
- One-level public item-handler containers; no free inventory slot is required to borrow a tool.
- Native AE2 network/card menus with a real source in a carried container; card changes persist.
- Native server sound/block feedback and render-only Mekanism configurator views where that version's native API is present.
- Two mixed ingots and two rods in the agreed diagonal recipe. Forge 1.12.2 uses Ore Dictionary material ingredients; later ports use their loader's common tags.

The adapters probe optional mods at runtime. An adapter's presence does not imply that every mod or block has been tested on every game version. Forge 1.12.2 uses older wrench interfaces and menu protocols; newer ports use the corresponding modern protocols.

## Verification on 2026-10-24

| Minecraft | Loader baseline | Game Java | Native mods loaded during server checks |
| --- | --- | --- | --- |
| 1.12.2 | Forge 14.23.5.2847; MixinBooter 11.18 | 8 | AE2 rv6-stable-7; Mekanism 9.8.3.390 |
| 1.16.5 | Forge 36.2.42 | 8 | AE2 8.4.7; Mekanism 10.1.2.457 |
| 1.20.1 | Forge 47.4.16 | 17 | AE2 15.4.11; Mekanism 10.4.16.80; GuideME 20.1.15 |
| 26.1.2 | NeoForge 26.1.2.122 | 25 | AE2 26.1.13-beta; GuideME 26.1.15-beta |
| 26.2 | NeoForge 26.2.0.93 | 25 | AE2 26.2.1-alpha; GuideME 26.2.2-alpha |

Each port passed its isolated dedicated-server fixture: shared and simulated actions, correct hand, a test tool's real cost, donor mode preservation, rejection of replacement items, full inventories, container transactions, detached sources, stale selections and protection checks. The 1.12.2 fixture also checked a recipe mixing iron and gold ingots.

The reobfuscated 1.12.2 release JAR and MixinBooter also passed a separate production-server boot and clean shutdown without native tool mods installed.

The three Forge fixtures each passed 16 physical/simulated main/offhand dismantling combinations with real AE2 and Mekanism machines. Forge 1.16.5 and 1.20.1 also checked the real Mekanism rotation mode and restoration of the donor's original mode. The 26.x fixtures each passed six real AE2 shared/simulated main/offhand cases. Native protection checks apply to bridge-dispatched actions; an original tool's own-mod behavior remains its mod's responsibility.

All five exercised native AE2 card menus with both hands, a full player inventory and a real network tool inside a public item-handler container. Card insertion/removal persisted to the source. The older AE2 menus also persisted their facade visibility setting. Container withdrawal and rejected/detached sources are covered independently by the container fixture.

These are automated server checks, not a complete visual or manual client compatibility pass. The physical G menu and Mekanism overlays on each new port still need broader playtesting. The container fixture validates the public capability protocol; it does not claim that a particular backpack mod was manually tested on all five versions. Only the native mods in the table were loaded for these port checks. The larger 1.21.1 test lab has its own [verification record](verification.md).

## Reproduce

Set `JAVA8_HOME`, `JAVA17_HOME`, `JAVA21_HOME` and `JAVA25_HOME` as described in the README. Run Gradle with Java 21; the modules select the appropriate compiler and game runtime. The old Forge sources use Jabel at compile time to produce Java 8 bytecode.

Run shared tests from the repository root:

```sh
bash gradlew :test
```

In a target version directory, build normally with `bash gradlew build`. The optional native fixtures fetch the exact pinned mods and run a separate local test world:

```sh
# Forge 1.12.2
bash gradlew build runServer -PportVerification -PnativeVerification
# Forge 1.16.5 or 1.20.1
bash gradlew build runVerificationServer -PnativeVerification
# NeoForge 26.1.2 or 26.2
bash gradlew build runVerificationServer -PnativeVerification
```

On Windows use `gradlew.bat`. Configure and accept the Minecraft EULA in each module's `run-port-verification/` directory before running a server fixture. Use a loopback-only `server-ip` and an unused port. The fixture shuts its server down after writing `verification-result.txt`; check that this starts with `PASS:` even when Gradle itself reports success. Test classes and third-party mod JARs are excluded from release JARs.

## Install

Choose the exact Minecraft/loader file and install the same build on client and server. **1.12.2 requires MixinBooter 11.18+ as a separate mod.** Later ports bundle or use the loader's MixinExtras support. Native tool mods remain optional. The five alpha.10 JARs are appended to the existing GitHub preview and listed separately on Modrinth; the original 1.21.1 alpha.9 artifacts are retained.
