# 0.1.0-alpha.4：扩充测试包

面向 1.21.1 / NeoForge 21.1.248，沿用 alpha.3 的自适应菜单、可配置快捷键、原模组翻译组件和已确认材质。

扩充包包含 27 个外部 JAR（含依赖），16 个分区、54 个靶机，新增气动工艺、Pretty Pipes、Refined Storage、Ender IO、Powah、RFTools Base / Utility、Integrated Dynamics、Modern Dynamics、Pipez、Supplementaries。使用单独的全新测试世界，提供分区重置、全部重置、工具补给与带内容物的容器。Pipez 和 Supplementaries 另作连接、旋转对照。

新增文件的项目来源、版本、依赖与 SHA-512 / SHA-256 锁定在 `expanded-mods.lock.json`。GuideME 升到新增依赖要求的 21.1.20，保持单份 JAR。

适配修正：

- 受控拆卸上下文同时覆盖实际主手、代理参数及同组件的物品栈副本；Item 注册表 Holder 的标签查询也获得相同的临时视图。结束后立即清除，没有修改物品 ID 或注册表标签。
- Ender IO 的 `useBlock=TRUE, useItem=FALSE` 表示由方块处理动作，按该路由执行；禁用方块交互仍停止。
- 气动工艺的第三方扳手自定义发包，与桥接的普通交互包在同一手势只走一条路径。原生气动扳手保留自身行为；模拟模式使用真实空气，低压拒绝时不会退回免费拆卸。
- Supplementaries 的普通右键保留旋转；在已知外来目标上的潜行共享拆卸避免被原旋转事件提前消费。
- Powah 的 CONFIG / LINK / ROTATE 只影响普通右键；其原生潜行拆卸不按这些模式屏蔽。

验证口径：扩充矩阵含 64 个来源／目标用例，其中 RS 新控制器的原生扳手也因自身 `canBuild` 路径拒绝，本轮只验证拒绝结果一致，不能当作允许拆卸的正向通过。其余正向项验证了拆卸与主手身份；保护拒绝针对桥接接管的跨模组路径，不重写原模组在自己功能范围内的保护规则。另验证气动扳手模拟真实扣气、空压拒绝，以及 16 个重置分区及充气工具命令成功解析。详细记录见随包的验证 JSON。

铁砧锤范围以安装版本的 `anvilcraft:hammer_removable` 标签及 `IHammerRemovable` 接口为准。它原本就覆盖许多原版红石方块、容器、门和交互方块；共享拆卸调用原流程。原生权限、特殊保护和多部件规则仍然有效。当前 27 JAR 包中共有 392 种注册方块符合这些标记或接口（含变体），不表示绕过原生限制后全部可拆。清单由验证进程导出为 `anvil-hammer-coverage.json`。

构建扩充包：`python scripts/fetch_lab_mods.py` 下载已锁定依赖，完成 NeoForge 模组构建后运行 `python scripts/build_expanded_lab.py`。该脚本只写新的分发实例；不要对已经游玩的实例重新创建世界。
