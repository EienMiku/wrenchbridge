# Per-use trait scopes: local NeoForge 1.21.1 preview

Preview `0.1.0-alpha.15-dev.1` changes the tag compatibility path. The published alpha.14 release remains unchanged.

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
