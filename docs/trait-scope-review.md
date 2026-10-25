# Per-use trait scopes: alpha.15

Alpha.15 changes the tag compatibility path on Forge 1.16.5/1.20.1 and NeoForge 1.21.1/26.1.2/26.2. Forge 1.12.2 has no equivalent generic tag hook; its runtime code is unchanged apart from release metadata. The evidence below begins with the tested NeoForge 1.21.1 preview `0.1.0-alpha.15-dev.1`. Earlier release files remain unchanged.

## Behavior

`WrenchContext.run` marks only the held and used item types, captures those identities before a callback can consume either stack, and releases them in `finally`. Each item has one volatile integer: a permanent universal-wrench bit and a synchronized count of live scopes. Nested and overlapping scopes retain their counts; the existing thread-local view determines which thread receives temporary traits.

The global `ItemStack` tag injections are removed. Its normal methods already delegate to the item's holder. The holder hook checks the per-item state before looking at wrench names or a thread local, so ordinary recipe checks use the original tag lookup. The universal wrench retains its inactive tag suppression, including after tag rebinding. Temporary views preserve canonical holder and held-stack identities and leave registry tag bindings untouched.

The holder hook still performs a few fast checks. This is not a claim of zero steady-state CPU cost. Live simulated source status also retains its existing on-demand synchronization.

For the remaining active lookups, wrench names are grouped by path and matched by namespace using value equality. This avoids duplicate `TagKey`/`ResourceLocation` hashing and accepts equal keys constructed outside the weak interner. Actual tag membership remains the registry's responsibility across data-pack reloads.

## Evidence

- Full existing NeoForge 21.1.248 native-mod server regression passed, including native costs, both hands, carried containers, depletion, menu transactions and 71 feedback comparisons.
- Added checks passed for non-interned keys, namespace/path collisions, same-name block tags, unbound references, registry tag reset/rebinding/restoration, nested scopes, a reader on another thread, consumed source cleanup, exceptions, and cleared gates across all registered items.
- The existing 53 client scenarios passed, including native Mekanism overlay frames and AE2 menus.
- NeoForge 21.1.244 with GriefLogger 1.2.9 and zxcopt 1.8.0 passed its 23 audit cases, ten borrowing fault cases, 18 native GUI server cases and 12 mode-message cases.
- The preview JAR passed the separate local 92-mod FTB fixture: 96 cases using a non-OP, non-member ordinary `ServerPlayer`; 24 allowed removals and 72 denials. Read-only SQLite checks found exactly one break row per removal and none per denial. The fixture discards outgoing packets; it does not establish a real client join or production-load result.

Reports are in [trait-scope-review](trait-scope-review/). The full local JFR and pack/world files are kept outside source control.

## Release ports

All six alpha.15 runtime/source pairs built successfully, alongside the shared unit tests. The final NeoForge 1.21.1 release JAR's 103 runtime classes are byte-identical to the tested preview. Forge 1.12.2 changes only its version annotation and release metadata; its other runtime classes match alpha.14.

The five additional ports passed fresh native-mod server regressions. Forge exercised AE2 and Mekanism; Forge 1.20.1 also exercised Create 6.0.8's 18 native GUI server cases, while 26.x exercised AE2. Existing suites cover both hands, protection denials, real costs, source restoration, carried containers, full inventory, native AE2 card menus, source snapshots and bounded UI requests.

Four new port fixtures verify idle and participating item states, equal tag names, namespace/registry separation where supported, same-item deduplication, nested actions, a reader on another thread, consumed-source exceptions, hand restoration and cleared states. Forge 1.16.5 gates its existing `Item` hooks; the newer ports use an `Item` scope marker and a holder hook. Its extra Mekanism configurator tag is retained. Forge 1.12.2 has no generic tag hook and therefore no new scope fixture. [Port reports](trait-scope-review/alpha15-ports/). Forge 1.20.1 also passed eight fresh integrated-client Create menu scenarios, including both hands, carried sources, source removal, profile changes, the physical native wrench and key-release submission.

An authorized separate production trial confirmed an actual client join, mode menu and dismantling. A 60-second JFR had zero classifier samples among 974 busy server-thread samples, with one Wrench Bridge-inclusive stack in an active AE2 menu source check. Its 18 unprofiled 100-tick windows averaged 33.74 MSPT and ranged from 31.0 to 38.3. The workload was not controlled against alpha.14, and the windows drifted within the run; these observations do not establish a fixed time saving or zero overhead. [Sanitized trial summary](trait-scope-review/trial-summary.json).

## Focused benchmark

JMH 1.37, JDK 21, two forks, three 300 ms warmup iterations and five 300 ms measurement iterations per fork; 256 queries per invocation. The synthetic workloads compare alpha.14's tag-key set with the new name classifier. They measure the helper only, not the holder hook or server ticks.

| Workload | Alpha.14 set | New classifier |
| --- | ---: | ---: |
| Ordinary item tags | 7.705 ns/op | 4.349 ns/op |
| One wrench match per 64 queries | 8.039 ns/op | 4.636 ns/op |
| Wrench tags | 7.745 ns/op | 5.223 ns/op |
| Other registry | 0.306 ns/op | 0.293 ns/op |

These helper results preceded the per-item gate. Ordinary idle lookups now bypass the classifier. The older in-server string-predicate smoke timing in the verification report is a different comparison and is not an alpha.14-versus-preview timing result.

To reproduce the classifier benchmark, use JDK 21 from `versions/neoforge-1.21.1` and run `gradlew -I ../../benchmarks/tag-lookup/benchmark.init.gradle tagBenchmark`. Its source set is opt-in and excluded from release JARs; output goes to `.toolchains/tag-key-review/jmh-reproduced.json` at the repository root.

The supplied production trace contained nine Wrench Bridge-inclusive stacks among 1,297 busy server-thread samples. It identifies a place to investigate; it does not establish that this mod caused the reported MSPT difference, nor can these microbenchmarks be converted into saved server milliseconds. A controlled production-load comparison remains separate work.
