# Alpha.14 dev.1 server review

Local preview for Minecraft 1.21.1, targeting NeoForge 21.1.244. This review does not deploy to a server, change the formal client, or publish a release. The other five platform artifacts remain unchanged.

Artifact: `wrenchbridge-neoforge-1.21.1-0.1.0-alpha.14-dev.1.jar`, SHA256 `c7fffaed572193ceef00df6c00416e077a512105d0acf50af21b7a89a317d169`. The [packaging record](alpha14-server-review/jar-verification.json) verifies the JAR against the tested compiled classes, excludes the verification fixture, and checks the unchanged author texture. Server fixtures run from the development source-set outputs; the exported JAR is ready for the separate full-pack validation.

## Changes

- Item-stack and holder wrench-tag hooks use five prebuilt item `TagKey` identities. Normal lookups no longer stringify locations; non-item tags are rejected before accessing holder values. Tag enumeration also preserves unbound intrusive holders during registration.
- Native mode getter/setter metadata is cached with `ClassValue`, including unsupported types. Curios API method discovery is cached separately. Current modes, inventories, costs, and tool availability remain live reads.
- Catalog requests coalesce to one scan per ten ticks, with a pending refresh for the latest request. Mode selection permits eight immediate distinct actions, refills one allowance per two ticks, and ignores identical successful requests while the held profile data is unchanged. These guards apply at network ingress, not to native actions.
- Source snapshot requests and post-action changes share a ten-tick capture cadence. A pending refresh uses the latest token and live contents; logout, shutdown, and expired pending sessions clear their work. Existing eight-source / 64 KiB-per-source bounds remain. At 20 TPS, an action-triggered display refresh can take up to half a second.
- Ordinary and menu borrowing share an exception-safe return path. The post-action stack is copied before insertion. A rejected return falls back to the player's inventory or a drop; an observable acceptance followed by an exception is subtracted from that fallback. Inventory notification runs even after a failed return. Consumed tools are not recreated.
- A synchronous server-use scope delays Create's shared wrench listener until the bridge's `LOWEST` handler, after other `HIGH` permission listeners. Cancellation and block/item denial remain effective. Scope ownership is limited to the initiating player, hand, and position.
- Virtual sneak-use permission checks run after a real tool has been found, including configuration modes that can incur costs. The unoverridden native `IWrenchable.onSneakWrenched` default supplies its own break check; custom overrides retain the bridge preflight. Bridge preflights within one action are memoized.
- Optional GriefLogger hooks journal only the native break, container-removal, and interaction-cleanup writes. Inspector and permission callbacks still run immediately. On exit, writes commit once per position/kind/actor if the original block type was removed or replaced. Refusal, missing sources, and rotation/configuration do not commit break writes. A callback that throws after actually removing the block still commits its audit.

No periodic machine/world scan was added. Pending UI work is processed only while pending sets are nonempty. The pre-existing per-player menu-binding check is unchanged.

## Verification

The fixture is a separate source set and is excluded from runtime and source release JARs. Reports are in [alpha14-server-review](alpha14-server-review/).

| Environment | Scope |
| --- | --- |
| Common Java 8-compatible code, JDK 21 | 17 unit tests, including live per-stack modes, failed getter fallback, ambiguous/unsupported APIs, burst/refill and server tick wrap |
| NeoForge 21.1.244; GriefLogger 1.2.9; Create 6.0.10 | Native audit and permission-order tests; durable SQLite rows checked after shutdown |
| Same, plus zxcopt 1.8.0 with deferred SQLite writes enabled | The same native audit, borrowing, request, and durable-row checks |
| NeoForge 21.1.248; existing native-mod lab | Full server regression suite, including Sophisticated Backpacks, both hands, real costs, mode restoration, protection, AE2 stored menus, feedback, and native Supplementaries depletion |

The new review fixture checks ten return scenarios: ordinary acceptance, refusal, throw-before-acceptance, input mutation then throw, acceptance then throw, action failure, real consumption, and the three menu exception paths. Counts across the handler, player inventory, and world drops must balance, with post-action durability preserved.

It sends 1,000 duplicate mode requests, 1,000 over-budget mode requests, 1,000 catalog requests, and 1,000 snapshot request/change pairs. Container read counters must remain unchanged during the blocked/coalesced portion. It then advances the guard timestamps and verifies the latest nonce, live durability, removed-source discovery, and expired pending-session cleanup. This is a deterministic main-thread ingress test, not a network throughput benchmark.

The 23 native GriefLogger cases cover no removal, state-only rotation, two preflights followed by one real removal, an exception after removal, a missing virtual source, and 18 Create combinations: universal/foreign/virtual tools, both hands, edit denial/interaction denial/success. Permission sentinels are deliberately registered after Create at the same `HIGH` priority. Failed cases have no break/container-removal rows; successful duplicate preflights produce one break row and one 16-item container record. Seeded interaction history survives non-removals and is cleaned up by actual removals.

The full regression caught and corrected an intermediate configuration-mode regression: skipping break preflight for all inactive wrench modes could charge durability on a denied sneak use. The final code retains that check after source validation.

Supplementaries 4.0.2 requires NeoForge 21.1.247 or newer, so it is excluded from the 21.1.244 audit lab and checked in the 21.1.248 lab. Client UI/overlay rendering and a multiplayer client/server connection were not rerun in this review.

## Performance evidence and limits

The warmed membership-only microbenchmark uses 300,000 checks over mixed item/block tags. On this machine, the old string-based predicate allocated about 61.9 bytes/check; the new predicate's measured allocation was approximately zero. Reported nanosecond timings are local microbenchmark observations, not whole-server timing results.

There is no measured claim of an MSPT reduction for the roughly 29-MSPT survival server. Real FTB Chunks permission combinations, the complete pack's event order, multiplayer behavior, and representative server load remain pack-validation work. The isolated `HIGH` sentinels establish the exercised ordering and denial behavior; they are not a substitute for running FTB itself.

GriefLogger 1.2.9 was exercised with and without zxcopt's deferred writer. The audit here concerns lookup/history and manual recovery evidence; it does not test or claim a GriefLogger rollback feature. Sharing disabled leaves foreign tools on their native path.

The journal recognizes removal/replacement by the final block type. A transient break followed by replacement with the same block type inside one callback is outside this detection boundary. Likewise, an item handler that commits into an unobservable location and then throws while making its original slot unreadable cannot provide enough information for a universal loss-and-duplication guarantee. The tested return cases use observable slot state and the ordinary item-handler contract; no normal Sophisticated Backpacks return failure has been reproduced.

## Reproduction

Use JDK 21. From the repository root, run `gradlew.bat :test`. From `versions/neoforge-1.21.1`, build with `gradlew.bat "-PneoforgeVersion=21.1.244" build`.

The opt-in `runReviewServer` uses `-Dwrenchbridge.verify=true` and an isolated `run-review` directory. Override that directory with `"-PreviewDir=run-review-custom"`. Install the pinned lab mods from the corresponding report, use a fresh test world/database, accept the Minecraft EULA, and run:

```powershell
.\gradlew.bat "-PneoforgeVersion=21.1.244" "-PreviewDir=run-review-custom" runReviewServer -x downloadAssets
```

Server verification does not require client sound/texture assets; `-x downloadAssets` avoids an unrelated failed client-asset download. The fixture writes both `verification-result.txt` and `review-verification.json`; check those files, since a normal game exit can still make Gradle report success after a failed fixture.

After shutdown, from the repository root:

```powershell
python scripts/verify_audit_sql.py versions/neoforge-1.21.1/run-review-custom
```

This opens SQLite read-only and writes `sql-audit-verification.json` beside the run's report. The full existing lab is exercised with `runVerificationServer` and NeoForge 21.1.248. Each verification game JVM is capped at 2 GiB.
