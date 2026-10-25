# Alpha.13 source tool display

When a universal wrench uses a native profile, its tooltip includes the selected source tool's **complete native tooltip**, including the original colors and operation instructions. Standard native durability, energy or air bars are mirrored on the universal wrench. Forge 1.20.1 and NeoForge builds also forward decorations registered through the loader's item-decoration API. A mod's custom item model, separate HUD or arbitrary replacement inventory renderer is outside that API.

The source has the same priority as actual simulation: first matching hotbar slot, then main inventory, offhand, then supported carried containers. Duplicate sources are not ranked by charge or durability. The status shows that real tool's own settings; selecting a simulated mode does not change the real tool's settings.

Native actions consume the real source's durability. If the native action destroys that stack, it is not restored by container return or mode restoration; the universal wrench remains in hand. The next action chooses the next eligible source of the same type. There is no retry with a backup during the current action. If a native tool remains as a damaged stack, it retains its priority and the native implementation decides whether it can still act. Without a source, the selected native profile stays selected and cannot perform a free action.

模拟模式会附上来源工具的完整原生提示，保留颜色和操作说明，并同步原生状态条。来源顺序与实际调用一致，包括随身容器。显示的是实际来源的数据，原工具不会因显示而被移动、修改或消耗。

The **Source tool status / 原工具状态** option is available from the existing client configuration screen and defaults to ON. It controls both native tooltip details and slot indicators. All five options can be reset without changing key bindings. Existing configurations retain their values and default the new option to ON.

## Synchronization

Client-only copies of ordinary inventory tools provide an initial display. The server supplies bounded read-only snapshots, including tools inside supported carried containers whose full contents are not available to the client. Requests are limited to eight tool types, 64 KiB per tool and at most twice per second. A successful source action also refreshes its status. Stale connection replies are rejected and old snapshots expire. An unavailable source does not get a fabricated zero-charge stack.

Snapshots use the authenticated player's own sources and do not accept another player's identity or a client-provided slot. The display does not equip, extract or invoke an action on the original tool. Matching client and server builds are required because alpha.13 adds a status payload to the existing protocol.

## Verification

- Shared configuration tests cover persistence, defaults, migration, independent settings and reset.
- The actual 1.21.1 mod-list configuration entry is checked without a loaded world, including all five switches and reset at the minimum 320 × 240 layout.
- All six platform server fixtures check first-source priority, ordinary and container snapshots, copy isolation, unchanged native settings, player isolation, packet round trips, rate limiting and size limits. Existing native-action, cost, offhand, protection and container-menu regressions also run.
- An isolated 1.21.1 client checks complete styled native tooltips, native bars, a registered native decoration, ordinary-inventory priority, real Sophisticated Backpack sources, offhand and unheld inventory wrenches, status refresh after a real source update, source removal, and the option's OFF/reset behavior. Representative native tools are Supplementaries' wrench, Mekanism's configurator and PneumaticCraft's pneumatic wrench. A test-only wrench tag on Mekanism's energy tablet additionally checks charged native energy data and its bar; this tag is excluded from releases and does not add energy-tablet tool compatibility.
- The actual wrench tooltip was separately rendered with both the default G key and a remapped H key to verify the colored key hint.
- Four real Supplementaries last-use cases cover both hands and ordinary-inventory/Sophisticated Backpack sources. The final rotation consumes the native tool, keeps the universal wrench, leaves the backup untouched until the next action, advances the source snapshot, and rejects further actions when all sources are removed.

The client checks cover the pinned mods in the local test pack. Other mods' unusual renderers or tooltip hooks may need individual adaptations; six successful builds do not imply that every third-party client renderer was playtested on every game version.
