# Wrench Bridge

[简体中文](README.md) · [Download](https://github.com/EienMiku/wrenchbridge/releases) · [Report an issue](https://github.com/EienMiku/wrenchbridge/issues)

A universal wrench and a compatibility bridge for modded Minecraft. Share supported dismantling protocols between wrench-like tools, and borrow the native functions of tools you carry through a configurable mode palette.

Downloads cover **Forge 1.12.2, 1.16.5, 1.20.1 and NeoForge 1.21.1, 26.1.2, 26.2**, all at **0.1.0-alpha.12**. Install the matching game/loader file on both client and server. Single-player requires only the client installation. **Forge 1.12.2 also requires MixinBooter 11.18 or newer.**

## Use

- Hold the universal wrench in either hand. Sneak and use a supported block to dismantle it through its native mod protocol. Shared dismantling does not require carrying the original wrench.
- Press **G** to open the searchable mode palette. Change this binding in Minecraft's controls. Ordinary right-click does not open the palette.
- Open **Mods → Wrench Bridge → Config** without holding a tool or loading a world. A separate **Open Wrench Bridge settings** key can be assigned in Controls; it is unbound by default and works empty-handed in game.
- **Other wrenches** sharing is enabled by default. Toggle it in the mod-list configuration screen or **Sharing settings** at the bottom of the G palette. It controls Wrench Bridge's extra sharing for other tools; native compatibility and the universal wrench's abilities remain available. The choice is saved locally and synchronized per player on login.
- Simulated configuration overlays, mode menu icons and mode instructions have separate switches, all ON by default. Reset restores the four options without changing key bindings.
- Select a carried native tool to simulate its functions. The universal wrench stays in your hand; the real source tool supplies its data, energy, air, and durability. Its selected mode is restored after the action.
- In the default shared mode, ordinary use keeps the block's normal interaction, such as opening a machine. Explicit native modes retain their intended rotation or configuration behavior.
- Public item-handler containers, including supported Sophisticated Backpacks, can supply tools. One container level is searched; remote storage and nested container chains are not scanned.
- Simulated AE2 network tools can open native network statistics and card storage from carried containers, even with a full player inventory. Card changes persist to the real tool.

Craft the wrench with two ingots and two rods. Different materials may be mixed. NeoForge uses `c:ingots` / `c:rods`, modern Forge uses `forge:ingots` / `forge:rods`, and 1.12.2 collects `ingot*`, `stick*` and `rod*` Ore Dictionary entries:

```text
 I 
 SI
S  
```

`I` = ingot; `S` = rod. The item is also available in the creative tools tab as `wrenchbridge:universal_wrench`.

## Compatibility

The **1.21.1** lab covers representative tools and blocks from AE2, Mekanism, Create, AnvilCraft, PneumaticCraft, Pretty Pipes, Refined Storage, Ender IO, Powah, RFTools, Integrated Dynamics, Modern Dynamics, Pipez, and Supplementaries. These mods and container integrations are optional. Coverage differs between game versions; see the [port verification record](docs/alpha10-ports.md).

Compatibility is determined from tool tags, capabilities, supported mode APIs, and known native block protocols. It does not make every block dismantleable. Denied or failed native actions do not fall back to forcibly destroying a machine. Pipez's native wrench interaction configures connections; Pretty Pipes' wrench retrieves pipes, while its terminals and pressurizers retain their native mining behavior.

Alpha.12 expands the client configuration page and adds an independent settings key on all six platforms. See the [settings and verification record](docs/alpha12-client-settings.md). Original feedback, protection rules, and real tool costs are retained.

Validation includes **71 server feedback comparisons across 15 tools and 53 client scenarios**, alongside the existing protection, offhand, real-cost, inventory, and container-menu checks. These cover specific pinned mod versions and representative actions, rather than every block, network, multipart attachment, or protection plugin. In the automated Refined Storage scenario, the original wrench also refuses the action; only refusal parity is established there.

## Other Minecraft versions

The alpha.10 ports include held-tool simulation, native mode preservation, carried-container sources and AE2 menus, offhand routing, the adaptive key-bound palette, native server feedback, and the mixed-material recipe.

All five passed isolated server verification. The Forge ports also passed real AE2/Mekanism dismantling tests; the 26.x ports passed real AE2 tests. Native AE2 card menus were checked in both hands with a full inventory and a real source tool in a capability container. See [pinned versions and reproduction steps](docs/alpha10-ports.md). The new ports have not yet received the complete manual client/overlay testing performed on 1.21.1.

## Build

With JDK 21 installed:

```sh
bash gradlew :test
cd versions/neoforge-1.21.1
bash gradlew build
```

On Windows use `gradlew.bat`. Outputs are in the module's `build/libs/`. Each version directory has its own Gradle wrapper; other ports require their corresponding Java toolchain. See the Chinese README for the full build matrix.

The optional integration verification mod is in `src/verification` and is excluded from release JARs. Its complete lab needs the pinned third-party mods; ordinary builds and shared unit tests do not.

## Development and license

Created by EienMiku. See [credits](CREDITS.md).

MIT license. This is an independent project, not an official release of the supported mods, Mojang, or Microsoft.
