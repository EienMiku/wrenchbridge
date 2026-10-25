Reduce idle tag-lookup work by enabling temporary wrench traits only for the item types participating in a native action. Ordinary recipe and tag checks now bypass wrench-name classification and thread-local lookups. Redundant ItemStack tag hooks are removed where lookups already delegate to the item holder.

- Preserve nested actions, thread isolation, real tool costs and cleanup when a borrowed tool breaks or a callback fails.
- Keep native tag membership and data-pack reload behavior intact; universal mode retains its existing interaction rules.
- Apply this optimization to Forge 1.16.5/1.20.1 and NeoForge 1.21.1/26.1.2/26.2. The Forge 1.12.2 build is included with the same version number; it has no equivalent generic tag hook to optimize.

减少闲置时的标签查询开销：仅在执行原生操作期间，为参与操作的物品类型启用临时扳手识别。普通配方与标签查询会提前跳过扳手名称判断和线程上下文读取；由物品 Holder 处理查询的版本还移除了重复的 ItemStack 钩子。

- 保留嵌套操作、线程隔离和真实消耗；原工具耗尽或操作异常时也会清理临时状态。
- 保留原生标签及数据包重载行为，通用模式的交互规则不变。
- 优化覆盖 Forge 1.16.5/1.20.1 与 NeoForge 1.21.1/26.1.2/26.2。Forge 1.12.2 同步提供构建，其实现没有需要此次优化的通用标签钩子。

Install the matching build on client and server. Forge 1.12.2 additionally requires **MixinBooter 11.18+**. Small fast checks remain; this is not a claim of zero CPU cost or a fixed MSPT reduction. [Verification and measurements](https://github.com/EienMiku/wrenchbridge/blob/main/docs/trait-scope-review.md).
