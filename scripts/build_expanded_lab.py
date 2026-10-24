"""Build a separate, larger wrench lab with locked mods and resettable test stations."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZipFile

import build_lab_pack as base
from pcl_memory import slider_for_gib, gib_for_slider

ROOT = Path(__file__).resolve().parents[1]
MOD_VERSION = base.VERSION
base.VERSION += '-expanded'
base.OUT = ROOT.parent / f'pack/wrenchbridge-lab-{base.VERSION}'
base.GAME = base.OUT / 'instance'
base.WORLD = base.GAME / 'saves/WrenchBridge-Lab'
base.DATA = base.WORLD / 'datapacks/WrenchBridgeLab'
base.WORLD_NAME = '通用扳手综合测试场'
base.PACK_TITLE = 'Wrench Bridge Expanded Lab / 通用扳手综合测试包'
base.PACK_SUMMARY = '27 个模组文件、16 个分区、54 个可重置靶机的通用扳手测试场'
base.INSTANCE_NAME = 'Wrench Bridge Expanded Lab'
base.MAX_MEMORY = 6144
EXTRA = json.loads((ROOT / '.toolchains/expanded-lab/mods.lock.json').read_text(encoding='utf-8'))
base.MODS = [(name, path) for name, path in base.MODS if name != 'GuideME']
base.MODS += [(e['name'], f'wrenchbridge/.toolchains/expanded-lab/downloads/{e["file"]}') for e in EXTRA]

# Four columns, four rows. The final two areas exercise vanilla hammer coverage and contents.
STATIONS = [
    ('AE2', 'light_blue', ['石英扳手 / 网络工具', '潜行右键拆卸', '网络工具右键看网络'],
     ['ae2:interface', 'ae2:pattern_provider', 'ae2:energy_acceptor']),
    ('Mekanism', 'lime', ['配置器切到扳手模式', 'G 选择子模式', '测试配置与拆卸隔离'],
     ['mekanism:basic_energy_cube', 'mekanism:enrichment_chamber', 'mekanism:metallurgic_infuser']),
    ('机械动力', 'orange', ['原生旋转 / 共享拆卸', '轴 / 齿轮 / 动力辊压机', '可试用其它扳手'],
     ['create:shaft[axis=x]', 'create:cogwheel[axis=x]', 'create:mechanical_press']),
    ('铁砧工艺', 'yellow', ['铁砧锤与共享拆卸', '皇家钢块 / 溜槽 / 箱', '原版范围见后方分区'],
     ['anvilcraft:royal_steel_block', 'anvilcraft:chute', 'anvilcraft:crate']),
    ('精致存储 RS', 'cyan', ['扳手 / 通用扳手', '控制器 / 驱动器 / 线缆', '测试设置与内容保留'],
     ['refinedstorage:controller', 'refinedstorage:disk_drive', 'refinedstorage:cable']),
    ('Ender IO', 'purple', ['Yeta 扳手 / 共享拆卸', '机器与电容库', '管束零件请另行试验'],
     ['enderio:alloy_smelter', 'enderio:basic_capacitor_bank', 'enderio:stirling_generator']),
    ('气动工艺', 'light_gray', ['已充气的气动扳手', '机器 / 气压管 / 墙', '可与原工具对照'],
     ['pneumaticcraft:air_compressor', 'pneumaticcraft:pressure_tube', 'pneumaticcraft:pressure_chamber_wall']),
    ('Pretty Pipes', 'pink', ['原扳手仅收取管道', '终端与增压器正常挖掘', '覆盖板会优先移除'],
     ['prettypipes:pipe', 'prettypipes:item_terminal', 'prettypipes:pressurizer']),
    ('Powah', 'red', ['潜行右键拆卸', '模式影响普通右键', '能源方块与充能球'],
     ['powah:energy_cell_starter', 'powah:furnator_starter', 'powah:energizing_orb']),
    ('RFTools', 'blue', ['Smart Wrench', '使用普通扳手模式', '选择模式请单独对照'],
     ['rftoolsbase:machine_infuser', 'rftoolsutility:crafter1', 'rftoolsutility:screen_controller']),
    ('集成动力', 'green', ['Menril 扳手', 'DEFAULT 用于拆卸', '偏移与复制保留原意'],
     ['integrateddynamics:cable', 'integrateddynamics:energy_battery', 'integrateddynamics:mechanical_squeezer']),
    ('Modern Dynamics', 'brown', ['扳手与共享拆卸', '物品管 / 流体管 / 线', '测试管道连接与附件'],
     ['moderndynamics:item_pipe', 'moderndynamics:fluid_pipe', 'moderndynamics:lv_cable']),
    ('Pipez 连接对照', 'magenta', ['潜行右键设置连接', '原生操作不会收管', '其扳手可试拆其它区'],
     ['pipez:item_pipe', 'pipez:fluid_pipe', 'pipez:energy_pipe']),
    ('补充包旋转对照', 'white', ['先选补充包模拟模式', '普通右键旋转方块', '原扳手也可使用'],
     ['minecraft:white_glazed_terracotta', 'minecraft:stone_stairs', 'minecraft:barrel']),
    ('铁砧锤原版范围', 'black', ['来自原生可拆标签', '红石 / 漏斗 / 活塞', '试用其它扳手拆卸'],
     ['minecraft:observer', 'minecraft:piston', 'minecraft:hopper', 'minecraft:redstone_lamp', 'minecraft:repeater', 'minecraft:note_block']),
    ('内容物与结构', 'gray', ['箱子内已放测试物', '比较原工具的掉落', '重置会恢复预设内容'],
     ['minecraft:chest', 'minecraft:furnace', 'minecraft:shulker_box', 'minecraft:barrel', 'minecraft:oak_door', 'minecraft:oak_trapdoor']),
]
TOOLS = ['wrenchbridge:universal_wrench', 'mekanism:configurator', 'anvilcraft:anvil_hammer',
         'ae2:certus_quartz_wrench', 'create:wrench', 'refinedstorage:wrench', 'enderio:yeta_wrench',
         'pneumaticcraft:pneumatic_wrench[pneumaticcraft:air=150000]', 'prettypipes:wrench', 'powah:wrench',
         'rftoolsbase:smartwrench', 'integrateddynamics:wrench', 'moderndynamics:wrench',
         'pipez:wrench', 'supplementaries:wrench', 'ae2:network_tool', 'sophisticatedbackpacks:backpack']


def update_jar():
    name = f'wrenchbridge-neoforge-1.21.1-{MOD_VERSION}.jar'
    source = ROOT / 'dist' / name
    target = base.GAME / 'mods' / name
    shutil.copy2(source, target)
    manifest_path = base.OUT / 'mod-list.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    row = next(e for e in manifest if e['name'] == 'Wrench Bridge')
    row.update(file=name, bytes=source.stat().st_size, sha256=hashlib.sha256(source.read_bytes()).hexdigest())
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')


def prepare():
    assert not (base.WORLD / 'region').exists(), 'Use a new output directory for a played world'
    base.main(package=False)
    (base.GAME / 'PCL').mkdir(exist_ok=True)
    memory_slider = slider_for_gib(6)
    assert gib_for_slider(memory_slider) * 1024 == 6144
    base.write('PCL/Setup.ini', f'VersionArgumentIndie:1\nVersionArgumentIndieV2:True\nVersionRamType:1\nVersionRamCustom:{memory_slider}\n')
    build = ['forceload add -32 -32 31 47',
             'fill -24 63 -20 24 63 44 minecraft:sea_lantern',
             'fill -24 64 -20 24 64 44 minecraft:polished_deepslate',
             'setworldspawn 0 65 40', 'time set noon', 'weather clear',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule keepInventory true',
             'gamerule sendCommandFeedback false']
    all_reset = []
    layout = []
    for index, (title, color, lines, blocks) in enumerate(STATIONS):
        x = [-18, -6, 6, 18][index % 4]
        z = [-12, 0, 12, 24][index // 4]
        build.append(f'fill {x-4} 64 {z-3} {x+4} 64 {z+3} minecraft:{color}_concrete')
        build.append(base.sign(x, z+3, [title] + lines, f'/function wrenchbridge_lab:reset_{index+1:02}'))
        reset = []
        targets = []
        for i, block in enumerate(blocks):
            tx, tz = x + [-3,0,3][i % 3], z + (i//3)*2 - (1 if len(blocks)>3 else 0)
            reset += [f'setblock {tx} 65 {tz} minecraft:air', f'setblock {tx} 65 {tz} {block}']
            if block == 'minecraft:oak_door':
                reset.append(f'setblock {tx} 66 {tz} minecraft:oak_door[half=upper]')
            if index == 15 and block in ('minecraft:chest','minecraft:furnace','minecraft:shulker_box','minecraft:barrel'):
                item = {'minecraft:chest':'minecraft:diamond 3', 'minecraft:furnace':'minecraft:iron_ore 5',
                        'minecraft:shulker_box':'minecraft:emerald 7','minecraft:barrel':'minecraft:copper_ingot 9'}[block]
                reset.append(f'item replace block {tx} 65 {tz} container.0 with {item}')
            targets.append({'block':block,'x':tx,'y':65,'z':tz})
        if index == 12:
            reset.append(f'setblock {x+3} 65 {z+1} minecraft:chest')
        base.function(f'reset_{index+1:02}', reset)
        all_reset.append(f'function wrenchbridge_lab:reset_{index+1:02}')
        layout.append({'area':index+1,'name':title,'center':[x,z],'targets':targets})
    all_reset.append('tellraw @a ' + base.json_text({'text':'所有分区已恢复测试预设。点击各区牌子只重置该区。','color':'green'}))
    base.function('reset',all_reset)
    for x, name, command in [(-18,'再领测试工具','tools'),(-6,'重置全部靶机','reset'),(6,'工具与模式说明','help'),(18,'补领充气扳手','air_wrench')]:
        build.append(base.sign(x,35,[name,'点击牌子执行','G 打开模式菜单','原生模式消耗真实工具'], '/function wrenchbridge_lab:'+command))
    for chest, x in enumerate((-18,-6)):
        build.append(f'setblock {x} 65 38 minecraft:chest[facing=south]')
        for slot, tool in enumerate(TOOLS[chest*9:(chest+1)*9]):
            build.append(f'item replace block {x} 65 38 container.{slot} with {tool}')
    build += ['function wrenchbridge_lab:reset','data modify storage wrenchbridge_lab:state initialized set value 1b']
    base.function('build',build)
    base.function('tools',['give @s '+tool for tool in TOOLS])
    base.function('air_wrench',['give @s pneumaticcraft:pneumatic_wrench[pneumaticcraft:air=150000]'])
    base.function('help', ['tellraw @s '+base.json_text({'text':text,'color':'aqua'}) for text in [
        '共享拆卸：潜行右键；模式菜单：G，可在控制设置中修改。',
        '默认通用模式普通右键保留方块交互；选择原生扳手模拟模式后才提供对应的普通右键功能。',
        '支持主手与副手；主手动作优先。双持通用扳手时 G 编辑主手，测试副手可让主手空着或拿木棍。',
        'Mekanism 请选择 WRENCH；集成动力请选择 DEFAULT；Powah 的模式影响普通右键。',
        'Pipez 区用于连接设置；补充包区用于普通右键旋转。它们的扳手也可去其它区试拆。',
        '本版补全模拟与共享操作的原生音效和破坏反馈，可与各区原工具对照次数及效果。',
        'Pretty Pipes 原扳手仅收取管道；Item Terminal 和 Pipe Pressurizer 请正常挖掘。',
        '模拟 AE2 网络工具：普通右键网络设备看统计，对空气使用打开卡片收纳；原工具可留在精妙背包里。',
        '旋转带釉陶瓦：使用补充包原扳手，或携带它并按 G 选择补充包模拟模式。默认通用模式不旋转陶瓦。',
        '原版区来自铁砧锤原生可拆标签；内容物区请比较原工具和通用扳手的实际掉落。',
        '点击分区牌子重置该区，入口可重置全部。补领会添加工具，请留出物品栏空间。']])
    base.function('intro',['tag @s add wrenchbridge_lab_ready','gamemode creative @s','tp @s 0.5 65 40.5 180 10',
                           'function wrenchbridge_lab:tools','function wrenchbridge_lab:help'])
    resource = ROOT/'dist/WrenchBridge-Handdrawn-16px-1.21.1.zip'
    if resource.exists():
        (base.GAME/'resourcepacks').mkdir(exist_ok=True)
        shutil.copy2(resource,base.GAME/'resourcepacks'/resource.name)
        with (base.GAME/'options.txt').open('a',encoding='utf-8') as f:
            f.write('resourcePacks:'+json.dumps(['vanilla','mod_resources','file/'+resource.name])+'\n')
    lock = [{k:v for k,v in e.items() if k != 'dependencies'} for e in EXTRA]
    (base.OUT/'additional-mods.lock.json').write_text(json.dumps(lock,ensure_ascii=False,indent=2),encoding='utf-8')
    base.write('WrenchBridge-模组来源.json',json.dumps(lock,ensure_ascii=False,indent=2))
    (base.OUT/'lab-layout.json').write_text(json.dumps(layout,ensure_ascii=False,indent=2),encoding='utf-8')
    base.write('WrenchBridge-分区布局.json',json.dumps(layout,ensure_ascii=False,indent=2))
    count = sum(len(s['targets']) for s in layout)
    readme = f'''# 通用扳手扩充测试包

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。建议分配 6–8 GB 内存。
使用 Wrench Bridge {MOD_VERSION}，保留确认的 16px 材质和可配置的 G 菜单。

共 {len(base.MODS)} 个外部 JAR（含前置依赖），16 个分区、{count} 个测试靶机。
新增气动工艺、Pretty Pipes、Refined Storage、Ender IO、Powah、RFTools Base / Utility、集成动力、Modern Dynamics、Pipez、Supplementaries。
所用版本及校验值见 mod-list.json；新增来源见 additional-mods.lock.json。

## 开始测试

导入 MRPack 或 Prism ZIP，进入“通用扳手综合测试场”。这是独立的新测试世界。
进入时发放工具，入口另有两箱补给。主手或副手拿通用扳手按 G 选择模式，潜行右键测试共享拆卸。
双持通用扳手时 G 优先编辑主手；主手动作已生效或拒绝时不会再执行副手。测试副手时可先让主手空着或拿木棍。
原生模式使用携带的真实工具与消耗；普通右键不会打开模式菜单。
默认“通用（拆卸）”普通右键可打开机器界面；模拟配置工具的扳手模式则保留旋转 Create 传动杆等原生功能。

每区的牌子可重置该区；入口可重置全部。也可运行 /function wrenchbridge_lab:reset。
/function wrenchbridge_lab:tools 补领工具；/function wrenchbridge_lab:air_wrench 补领充气扳手。
Mekanism 请选择扳手模式，集成动力请选择 DEFAULT，Smart Wrench 使用 MODE_WRENCH。Powah 的模式影响普通右键，潜行拆卸独立于这些模式。
Pipez 是连接操作对照，原生潜行右键不等于拆管；Supplementaries 区用普通右键对照旋转。
Pretty Pipes 原扳手只收取管道；Item Terminal 与 Pipe Pressurizer 需要正常挖掘。
模拟 Mekanism 配置模式时，主手和副手均显示原生方块面叠层，仍需携带真实配置工具。
模拟 AE2 网络工具普通右键网络设备可查看网络统计；对空气或普通方块使用打开卡片收纳页。真实网络工具可放在精妙背包等可识别的随身容器内，玩家物品栏全满也可使用；卡片存取保存回原工具。
本版对照各区原工具，补全模拟和共享操作遗漏的原生音效与破坏反馈；详细记录见更新说明。
通用扳手的新配方为 2 个任意锭和 2 根任意棍，可混用材料；形状见更新说明。

最后两区展示铁砧锤覆盖的原版方块，并提供已放入物品的容器。
重置会恢复测试预设内容；请比较原工具与通用扳手的掉落和数据是否一致。
需要生存掉落测试时使用 /gamemode survival，完成后 /gamemode creative。

机器拆卸验证不代表每个模组的全部网络、覆盖板、多方块及专用按键都已覆盖。
该包保留这些原生工具，便于做逐项对照与发现遗漏。AE2 原生界面已支持随身容器来源；铁砧锤的便携铁砧界面仍需普通物品栏中的原工具。
RS 新控制器在自动化测试中连原生扳手也拒绝拆卸，该项只验证了拒绝结果一致，尚需真实玩家确认允许路径。

PCL 内存滑块刻度 21 对应 6 GiB；Prism 设置为 6144 MiB。旧版本地安装中已修复过滑块刻度换算错误。
PCL 如一直开着，请重新打开，再确认该版本分配 6 GiB；不要沿用此前错误的 70 GiB 设置。
'''
    (base.OUT/'README.md').write_text(readme,encoding='utf-8')
    base.write('WrenchBridge-体验说明.txt',readme)
    print(f'Prepared {len(base.MODS)} JARs, 16 areas, {count} targets: {base.OUT}')


def package():
    update_jar()
    reports = ['offhand-verification.json', 'expanded-verification.json', 'anvil-hammer-coverage.json',
               'integrated-machine-verification.json', 'pretty-pipes-limits.json', 'client-render-verification.json',
               'shared-intent-verification.json', 'ae2-network-menu-verification.json', 'container-menu-verification.json', 'feedback-verification.json']
    for name in reports:
        shutil.copy2(ROOT / 'docs' / name, base.OUT / name)
    shutil.copy2(ROOT / 'docs/alpha9-verification-result.txt', base.OUT / 'verification-result.txt')
    shutil.copy2(ROOT / 'docs/alpha9-client-verification-result.txt', base.OUT / 'client-verification-result.txt')
    report_dir = base.GAME / 'WrenchBridge-验证'
    report_dir.mkdir(exist_ok=True)
    for name in reports + ['verification-result.txt', 'client-verification-result.txt']:
        shutil.copy2(base.OUT / name, report_dir / name)
    shutil.copy2(ROOT / 'docs/alpha9-native-feedback.md', base.OUT / '更新说明.md')
    shutil.copy2(ROOT / 'docs/alpha9-native-feedback.md', base.GAME / 'WrenchBridge-alpha.9-更新说明.md')
    shutil.copy2(ROOT / 'scripts/update_lab_profile.ps1', base.OUT / 'Update-WrenchBridge.ps1')
    (base.OUT / '更新现有测试实例.cmd').write_text('@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Update-WrenchBridge.ps1"\r\npause\r\n', encoding='ascii', newline='')
    manifest = json.loads((base.OUT/'mod-list.json').read_text(encoding='utf-8'))
    readme = (base.OUT/'README.md').read_text(encoding='utf-8')
    base.package_instance(manifest,readme)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only',action='store_true')
    parser.add_argument('--package-only',action='store_true')
    args=parser.parse_args()
    if not args.package_only: prepare()
    if not args.prepare_only: package()
