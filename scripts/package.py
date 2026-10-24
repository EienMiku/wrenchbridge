"""Validate release JAR contents and package binaries plus the portable source tree."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import re
import shutil
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r"version\s*=\s*'([^']+)'", (ROOT / 'build.gradle').read_text(encoding='utf-8')).group(1)
DIST = ROOT / 'dist'
TARGETS = {'forge-1.12.2': 52, 'forge-1.16.5': 52, 'forge-1.20.1': 61,
           'neoforge-1.21.1': 65, 'neoforge-26.1.2': 69, 'neoforge-26.2': 69}

DIST.mkdir(exist_ok=True)
(DIST / 'sources').mkdir(exist_ok=True)
checksums = []
current_names = []
for target, major in TARGETS.items():
    module_source = (ROOT / 'versions' / target / 'build.gradle').read_text(encoding='utf-8')
    module_version = re.search(r"version\s*=\s*'([^']+)'", module_source).group(1)
    name = f'wrenchbridge-{target}-{module_version}.jar'
    current_names.append(name)
    source = ROOT / 'versions' / target / 'build/libs' / name
    with ZipFile(source) as jar:
        names = jar.namelist()
        assert not any('/verification/' in n for n in names), f'Test fixture leaked into {name}'
        assert 'LICENSE-wrenchbridge.txt' in names, f'License missing in {name}'
        assert struct.unpack('>H', jar.read('dev/wrenchbridge/WrenchBridge.class')[6:8])[0] == major, f'Wrong Java target: {name}'
        for resource in names:
            if resource.endswith('.json'):
                json.loads(jar.read(resource))
        metadata = 'mcmod.info' if target == 'forge-1.12.2' else 'META-INF/' + ('neoforge.mods.toml' if target.startswith('neoforge') else 'mods.toml')
        assert module_version in jar.read(metadata).decode('utf-8'), f'Unexpanded version in {name}'
    shutil.copy2(source, DIST / name)
    sources_name = name.replace('.jar', '-sources.jar')
    shutil.copy2(source.with_name(sources_name), DIST / 'sources' / sources_name)
    checksums.append(f'{hashlib.sha256(source.read_bytes()).hexdigest()}  {name}')

for name in ('README.md', 'LICENSE'):
    shutil.copy2(ROOT / name, DIST / name)
shutil.copytree(ROOT / 'docs', DIST / 'docs', dirs_exist_ok=True)
result = ROOT / 'versions/neoforge-1.21.1/run-verification/verification-result.txt'
if result.exists():
    summary = result.read_text(encoding='utf-8')
    assert summary.startswith('PASS:'), f'Latest integration verification failed: {summary}'
    (DIST / 'docs/verification-result.txt').write_text(summary + '\n', encoding='utf-8')
(DIST / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n', encoding='utf-8')

source_zip = DIST / f'wrenchbridge-{VERSION}-source.zip'
excluded = {'.gradle', '.toolchains', '.idea', '.git', 'build', 'dist', '__pycache__'}
with ZipFile(source_zip, 'w', ZIP_DEFLATED) as archive:
    # An allowlist from Git excludes local launcher helpers and other ignored files.
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    for name in sorted(filter(None, tracked)):
        relative = Path(name)
        file = ROOT / relative
        if not file.is_file() or any(part in excluded or part == 'run' or part == 'runs' or part.startswith('run-') for part in relative.parts):
            continue
        archive.write(file, Path('wrenchbridge') / relative)

binaries_zip = DIST / f'wrenchbridge-{VERSION}-binaries.zip'
with ZipFile(binaries_zip, 'w', ZIP_DEFLATED) as archive:
    for name in current_names:
        archive.write(DIST / name, name)
        source_name = name.replace('.jar', '-sources.jar')
        archive.write(DIST / 'sources' / source_name, 'sources/' + source_name)
    for name in ('README.md', 'LICENSE', 'SHA256SUMS.txt'):
        archive.write(DIST / name, name)
    for file in sorted((DIST / 'docs').rglob('*')):
        if file.is_file(): archive.write(file, file.relative_to(DIST))
print(f'Validated {len(TARGETS)} platform JARs; packaged {binaries_zip.name} and {source_zip.name}.')
