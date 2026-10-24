# Alpha.11: optional sharing / 可选扳手互通

All six platforms add a client-owned **Other wrenches** preference, enabled by default. Open the mod-list configuration page, or hold the universal wrench, press G and choose **Sharing settings**.

六个平台均新增「其他扳手互通」客户端开关，默认开启。可以从模组列表的配置页进入，也可手持通用扳手，按 G，在底部打开「互通设置」。

The setting controls Wrench Bridge's added shared dismantling for other mods' physical tools, in either hand. Turning it off leaves each tool's native behavior and native cross-mod compatibility in place. The universal wrench's shared and simulated modes remain available. It does not affect another player's choice.

关闭后，仅停止本模组为其它扳手额外提供的共享拆卸。工具本身已有的功能及原生跨模组兼容继续有效；通用扳手的通用拆卸和模拟功能也继续有效。设置只影响自己。

The client saves `shareOtherWrenches=true` or `false` in `config/wrenchbridge-client.properties`. It synchronizes the value when joining a server and immediately after a UI change. The server binds each update to the authenticated sender, discards it on logout/shutdown, and keeps no global world setting. Missing configuration defaults to ON. A failed save leaves the prior value active and reports the error in the screen.

客户端把选择保存到 `config/wrenchbridge-client.properties`，进入服务器和修改时自动同步。服务端按玩家分别记录，退出后清理。**客户端和服务端都需升级至 alpha.11**；网络协议已更新，旧构建不能混用。

## Verification

- Shared Java tests cover defaults, save/reload, client/server separation, player isolation, universal bypass, session cleanup and failed saves.
- All six dedicated-server fixtures cover ON → OFF → ON using a foreign wrench, both hands, a second player who retains the default, the universal wrench while OFF, and the actual platform packet codec.
- Existing server checks also pass on all six platforms, including optional native AE2/Mekanism integrations where available, real tool costs, mode restoration, offhand routing, protection and container menus. Exact native-mod versions remain those recorded in [the port record](alpha10-ports.md) and [the 1.21.1 record](verification.md).
- The 1.21.1 client fixture exercises the registered mod-list configuration factory, both settings buttons and return to the palette, actual client-to-server synchronization, disk persistence, and restoring OFF on a second client launch. It runs in a separate test world with a 2 GiB heap limit.

Port client screens compile against their respective game APIs. The full client interaction and reconnect check is performed on 1.21.1; this is not a claim of manual visual testing on all platforms.

To reproduce, run `gradlew :test` at the root, then the existing server verification command for each platform in [the port record](alpha10-ports.md). For 1.21.1, use `gradlew build runVerificationServer`. For the client check, prepare an isolated `run-sharing-client/saves/Sharing-Check` test world, then run `gradlew runSharingClient` followed by `gradlew runSharingClient -PverifySharingResume=true`. The first launch saves OFF; the second must load it and synchronize it to its integrated server before restoring ON. Check both `sharing-client-result.txt` and `sharing-client-resume-result.txt` for `PASS:`.
