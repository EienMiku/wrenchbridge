"""Build an isolated, importable 1.21.1 client pack and a fresh wrench playground."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import gzip
import hashlib
import json
import re
import shutil
import struct
import time

REPO = Path(__file__).resolve().parents[2]
VERSION = re.search(r"version\s*=\s*'([^']+)'", (REPO / 'wrenchbridge/build.gradle').read_text(encoding='utf-8')).group(1)
OUT = REPO / f'pack/wrenchbridge-lab-{VERSION}'
GAME = OUT / 'instance'
WORLD = GAME / 'saves/WrenchBridge-Lab'
DATA = WORLD / 'datapacks/WrenchBridgeLab'
PACK_NAME = 'WrenchBridge-Lab-1.21.1-sim'
WORLD_NAME = '通用扳手体验场 · 模拟版'
PACK_TITLE = 'Wrench Bridge Lab / 通用扳手体验包'
PACK_SUMMARY = '通用扳手、四个科技模组与精妙背包的独立体验场'
INSTANCE_NAME = 'Wrench Bridge Lab'
MAX_MEMORY = 5120
MODS = [
    ('Wrench Bridge', f'wrenchbridge/dist/wrenchbridge-neoforge-1.21.1-{VERSION}.jar'),
    ('Mekanism', 'server/mods/Mekanism-1.21.1-10.7.19.85.jar'),
    ('Applied Energistics 2', 'server/mods/appliedenergistics2-19.2.18.jar'),
    ('Create', 'server/mods/create-1.21.1-6.0.10.jar'),
    ('AnvilCraft', 'pack/update-1.7.3/incoming/anvilcraft-neoforge-1.21.1-1.6.0+pre-release.11.jar'),
    ('Sophisticated Backpacks', 'server/mods/sophisticatedbackpacks-1.21.1-3.26.9.2195.jar'),
    ('Sophisticated Core', 'server/mods/sophisticatedcore-1.21.1-1.5.7.2381.jar'),
    ('Curios', 'server/mods/curios-neoforge-9.5.1+1.21.1.jar'),
    ('GuideME', 'server/mods/guideme-21.1.19.jar'),
    ('JEI', 'pack/update-1.7.3/incoming/jei-1.21.1-neoforge-19.64.0.455.jar'),
    ('Jade', 'server/mods/Jade-1.21.1-NeoForge-15.10.6.jar'),
]


def write(relative, value):
    path = GAME / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding='utf-8')


def json_text(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def function(name, commands):
    write(f'saves/WrenchBridge-Lab/datapacks/WrenchBridgeLab/data/wrenchbridge_lab/function/{name}.mcfunction', '\n'.join(commands) + '\n')


def sign(x, z, lines, click=None):
    messages = []
    for index, text in enumerate(lines):
        entry = {'text': text, 'color': 'dark_blue' if index == 0 else 'black'}
        if click:
            entry['clickEvent'] = {'action': 'run_command', 'value': click}
        messages.append("'" + json_text(entry).replace("'", "\\'") + "'")
    return f'setblock {x} 65 {z} minecraft:oak_sign[rotation=0]{{front_text:{{messages:[{",".join(messages)}]}}}} replace'


def string(value):
    encoded = value.encode('utf-8')
    return struct.pack('>H', len(encoded)) + encoded


def payload(tag, value):
    if tag in {1, 3, 4, 5, 6}:
        return struct.pack({1: '>b', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}[tag], value)
    if tag == 8:
        return string(value)
    if tag == 9:
        child, entries = value
        return bytes([child]) + struct.pack('>i', len(entries)) + b''.join(payload(child, entry) for entry in entries)
    if tag == 10:
        return b''.join(bytes([kind]) + string(name) + payload(kind, entry) for name, (kind, entry) in value.items()) + b'\0'
    raise ValueError(tag)


def typed(value):
    if isinstance(value, bool):
        return 1, int(value)
    if isinstance(value, int):
        return 3, value
    if isinstance(value, float):
        return 6, value
    if isinstance(value, str):
        return 8, value
    if isinstance(value, dict):
        return 10, {key: typed(entry) for key, entry in value.items()}
    if isinstance(value, list):
        entries = [typed(entry) for entry in value]
        child = entries[0][0] if entries else 8
        assert all(entry[0] == child for entry in entries)
        return 9, (child, [entry[1] for entry in entries])
    raise TypeError(type(value))


def create_world():
    # Fresh vanilla metadata only: no existing player's data, chunks, registries or server settings.
    dimensions = {
        'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {'type': 'minecraft:flat', 'settings': {
            'biome': 'minecraft:plains', 'features': False, 'lakes': False, 'structure_overrides': [],
            'layers': [{'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:dirt', 'height': 2}, {'block': 'minecraft:grass_block', 'height': 1}]}}},
        'minecraft:the_nether': {'type': 'minecraft:the_nether', 'generator': {'type': 'minecraft:noise', 'settings': 'minecraft:nether',
            'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}}},
        'minecraft:the_end': {'type': 'minecraft:the_end', 'generator': {'type': 'minecraft:noise', 'settings': 'minecraft:end',
            'biome_source': {'type': 'minecraft:the_end'}}},
    }
    level = typed({
        'DataVersion': 3955, 'version': 19133, 'LevelName': WORLD_NAME,
        'Version': {'Id': 3955, 'Name': '1.21.1', 'Snapshot': False, 'Series': 'main'},
        'GameType': 1, 'allowCommands': True, 'hardcore': False, 'initialized': True,
        'WasModded': True, 'confirmedExperimentalSettings': True, 'forgeLifecycle': 'stable',
        'SpawnX': 0, 'SpawnY': 65, 'SpawnZ': 10, 'SpawnAngle': 180.0,
        'GameRules': {'doDaylightCycle': 'false', 'doWeatherCycle': 'false', 'doMobSpawning': 'false',
                      'keepInventory': 'true', 'sendCommandFeedback': 'false', 'spawnRadius': '0'},
        'DataPacks': {'Enabled': ['create:dynamic_data', 'vanilla', 'mod_data', 'file/WrenchBridgeLab'], 'Disabled': ['bundle', 'trade_rebalance']},
        'enabled_features': ['minecraft:vanilla'],
        'WorldGenSettings': {'generate_features': False, 'bonus_chest': False, 'dimensions': dimensions},
    })[1]
    level.update({'Time': (4, 0), 'DayTime': (4, 6000), 'LastPlayed': (4, int(time.time() * 1000)),
                  'Difficulty': (1, 0), 'SpawnAngle': (5, 180.0), 'BorderSize': (6, 59999968.0)})
    level['WorldGenSettings'][1]['seed'] = (4, 20261024)
    WORLD.mkdir(parents=True, exist_ok=True)
    (WORLD / 'level.dat').write_bytes(gzip.compress(bytes([10]) + string('') + payload(10, {'Data': (10, level)})))


def main(package=True):
    (GAME / 'mods').mkdir(parents=True, exist_ok=True)
    manifest = []
    for label, relative in MODS:
        source = REPO / relative
        with ZipFile(source) as jar:
            assert jar.testzip() is None, source
        shutil.copy2(source, GAME / 'mods' / source.name)
        manifest.append({'name': label, 'file': source.name, 'bytes': source.stat().st_size,
                         'sha256': hashlib.sha256(source.read_bytes()).hexdigest()})
    write('options.txt', '\n'.join([
        'version:3955', 'lang:zh_cn', 'guiScale:3', 'renderDistance:10', 'simulationDistance:5',
        'fullscreen:false', 'maxFps:120', 'narrator:0', 'onboardAccessibility:false', 'tutorialStep:none',
        'autoJump:false', 'pauseOnLostFocus:true', 'soundCategory_music:0.15',
        'key_key.wrenchbridge.modes:key.keyboard.g', 'key_key.curios.open.desc:key.keyboard.u',
        'key_key.mekanism.chest_mode:key.keyboard.k', 'key_key.mekanism.mode:key.keyboard.i',
    ]) + '\n')
    create_world()
    write('saves/WrenchBridge-Lab/datapacks/WrenchBridgeLab/pack.mcmeta', json_text({'pack': {'pack_format': 48, 'description': 'Wrench Bridge 可重置体验场'}}))
    write('saves/WrenchBridge-Lab/datapacks/WrenchBridgeLab/data/minecraft/tags/function/load.json', json_text({'values': ['wrenchbridge_lab:load']}))
    write('saves/WrenchBridge-Lab/datapacks/WrenchBridgeLab/data/minecraft/tags/function/tick.json', json_text({'values': ['wrenchbridge_lab:tick']}))
    function('load', ['execute unless data storage wrenchbridge_lab:state initialized run function wrenchbridge_lab:build'])
    function('tick', ['execute as @a[tag=!wrenchbridge_lab_ready] at @s run function wrenchbridge_lab:intro'])
    build = [
        'forceload add -16 -16 15 15', 'fill -14 64 -5 14 64 14 minecraft:polished_deepslate',
        'fill -14 63 -5 14 63 14 minecraft:sea_lantern', 'setworldspawn 0 65 10',
        'time set noon', 'weather clear', 'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
        'gamerule doWeatherCycle false', 'gamerule keepInventory true', 'gamerule sendCommandFeedback false',
    ]
    stations = [(-9, 'light_blue', ['AE2', '石英扳手 / 网络工具', '潜行 + 右键拆卸', 'G 切换模拟模式']),
                (-3, 'lime', ['Mekanism', '配置器切到扳手模式', 'G → 配置器 → 扳手', '试拆其他模组靶机']),
                (3, 'orange', ['Create', '机械动力扳手', '潜行 + 右键拆卸', '原生旋转仍可使用']),
                (9, 'yellow', ['铁砧工艺', '皇家钢块 / 溜槽 / 箱', '试用其他模组扳手', '用通用扳手模拟锤子'])]
    for x, color, lines in stations:
        build.append(f'fill {x-2} 64 -1 {x+2} 64 3 minecraft:{color}_concrete')
        build.append(sign(x, 3, lines))
    build.extend([
        'setblock -11 65 8 minecraft:chest[facing=south]',
        sign(-7, 8, ['工具补给', '空手或普通右键开箱', '可重复领起始工具', '点击此牌补充'], '/function wrenchbridge_lab:tools'),
        sign(0, 7, ['精妙背包测试', '把配置器放进背包', '关界面后按 G 搜索', '工具留在原位供能']),
        sign(7, 8, ['重置靶机', '点击此牌恢复靶机', '也可输入 /function', 'wrenchbridge_lab:reset'], '/function wrenchbridge_lab:reset'),
        'function wrenchbridge_lab:reset',
    ])
    for slot, item in enumerate(['wrenchbridge:universal_wrench', 'mekanism:configurator', 'ae2:certus_quartz_wrench',
                                  'create:wrench', 'anvilcraft:anvil_hammer', 'sophisticatedbackpacks:backpack',
                                  'ae2:network_tool', 'minecraft:iron_ingot', 'minecraft:chest']):
        build.append(f'item replace block -11 65 8 container.{slot} with {item} 1')
    build.append('data modify storage wrenchbridge_lab:state initialized set value 1b')
    function('build', build)
    targets = [(-10, 0, 'ae2:interface'), (-8, 0, 'ae2:pattern_provider'),
               (-4, 0, 'mekanism:basic_energy_cube'), (-2, 0, 'mekanism:enrichment_chamber'),
               (2, 0, 'create:shaft[axis=x]'), (4, 0, 'create:cogwheel[axis=x]'),
               (8, 0, 'anvilcraft:royal_steel_block'), (10, 0, 'anvilcraft:chute'), (9, 1, 'anvilcraft:crate')]
    reset = []
    for x, z, block in targets:
        reset += [f'setblock {x} 65 {z} minecraft:air', f'setblock {x} 65 {z} {block}']
    reset.append('tellraw @a ' + json_text({'text': '体验场靶机已恢复。潜行 + 右键拆卸；G 打开模式菜单。', 'color': 'green'}))
    function('reset', reset)
    starter = ['give @s ' + item for item in ['wrenchbridge:universal_wrench', 'mekanism:configurator',
               'ae2:certus_quartz_wrench', 'create:wrench', 'anvilcraft:anvil_hammer', 'sophisticatedbackpacks:backpack', 'ae2:network_tool']]
    function('tools', starter)
    function('intro', ['tag @s add wrenchbridge_lab_ready', 'gamemode creative @s', 'tp @s 0.5 65 10.5 180 10',
        'function wrenchbridge_lab:tools',
        'tellraw @s ' + json_text({'text': '欢迎来到通用扳手体验场！主手拿通用扳手按 G；模式只保存在通用扳手上。', 'color': 'aqua'}),
        'tellraw @s ' + json_text([{'text': '[恢复靶机]', 'color': 'green', 'clickEvent': {'action': 'run_command', 'value': '/function wrenchbridge_lab:reset'}},
                                  {'text': '  [再领工具]', 'color': 'yellow', 'clickEvent': {'action': 'run_command', 'value': '/function wrenchbridge_lab:tools'}}]),
    ])
    (OUT / 'mod-list.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    readme = '''# 通用扳手体验包

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21，建议给游戏 4–5 GB 内存。

包含通用扳手、AE2、Mekanism、Create、铁砧工艺、精妙背包及依赖，另附 JEI 和 Jade。
铁砧工艺使用与桥接测试一致的 1.6.0+pre-release.11。具体文件与 SHA-256 见 mod-list.json。

## 开始体验

1. 用 PCL / HMCL / Prism 导入旁边的 `.mrpack`，安装后启动。也提供 Prism 实例 ZIP。
2. 进入单人游戏，打开“通用扳手体验场”。这是全新创造模式存档，进入后会发放工具。本包为模拟模式版。
3. **主手拿通用扳手按 G**，选择要模拟的工具／模式；主手物品始终是通用扳手。**潜行 + 右键**测试共享拆卸。
4. Mekanism 配置器需要在菜单中切到“扳手”模式。配置、清空等模式不会开启共享拆卸。
5. 用普通右键打开精妙背包，把配置器放进去。关闭界面后，手持通用扳手按 G 选择模拟功能；配置器仍留在背包里供能和提供原生数据，不要求空位。
6. 切回共享拆卸只改变通用扳手自己的配置。原生工具保持位置、身份和原工作模式，不会互相转换。

聊天栏点击“恢复靶机”，或输入 `/function wrenchbridge_lab:reset`，可重复测试。
`/function wrenchbridge_lab:tools` 补领工具；`/wrenchbridge diagnose` 查看探测数量。
测试场地共九个靶机。生存模式掉落测试可输入 `/gamemode survival`，完成后用 `/gamemode creative` 切回。

模拟铁砧锤或 AE2 网络工具时，对空气使用可打开已适配的原生界面；对应原工具需放在玩家普通物品栏中。

G 已留给扳手菜单；Curios 装备栏改为 **U**，Mekanism 胸甲模式键改为 **K**。
模式菜单仅通过可配置的快捷键打开，普通右键留给当前模式的功能。列表随窗口高度增加行数，原生模式名称优先沿用原模组的翻译。
这是小型体验包，未加入大型科技附属、光影和服务器连接。体验世界可随意试拆；首次启动需等待模组完成加载。
'''
    (OUT / 'README.md').write_text(readme, encoding='utf-8')
    write('WrenchBridge-体验说明.txt', readme)
    if package:
        package_instance(manifest, readme)


def package_instance(manifest, readme):
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': VERSION,
             'name': PACK_TITLE, 'summary': PACK_SUMMARY,
             'dependencies': {'minecraft': '1.21.1', 'neoforge': '21.1.248'}, 'files': []}
    with ZipFile(OUT / f'WrenchBridge-Lab-{VERSION}.mrpack', 'w', ZIP_DEFLATED) as archive:
        archive.writestr('modrinth.index.json', json_text(index))
        for file in sorted(GAME.rglob('*')):
            if file.is_file(): archive.write(file, 'overrides/' + file.relative_to(GAME).as_posix())
        archive.writestr('README.md', readme)
        archive.writestr('mod-list.json', json.dumps(manifest, ensure_ascii=False, indent=2))
    with ZipFile(OUT / f'WrenchBridge-Lab-{VERSION}-Prism.zip', 'w', ZIP_DEFLATED) as archive:
        archive.writestr('mmc-pack.json', json_text({'formatVersion': 1, 'components': [
            {'uid': 'net.minecraft', 'version': '1.21.1', 'important': True}, {'uid': 'net.neoforged', 'version': '21.1.248', 'important': True}]}))
        archive.writestr('instance.cfg', '[General]\nInstanceType=OneSix\nname=' + INSTANCE_NAME + '\nOverrideMemory=true\nMinMemAlloc=1024\nMaxMemAlloc=' + str(MAX_MEMORY) + '\n')
        for file in sorted(GAME.rglob('*')):
            if file.is_file(): archive.write(file, '.minecraft/' + file.relative_to(GAME).as_posix())
    print(f'Built {len(MODS)}-mod pack with fresh lab world: {OUT}')


if __name__ == '__main__':
    main()
