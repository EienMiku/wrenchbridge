"""Validate all platform builds and package sources from an explicit Git ref; never uploads."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    'forge-1.12.2': 52,
    'forge-1.16.5': 52,
    'forge-1.20.1': 61,
    'neoforge-1.21.1': 65,
    'neoforge-26.1.2': 69,
    'neoforge-26.2': 69,
}
TEXTURE = 'assets/wrenchbridge/textures/item/universal_wrench.png'
TEXTURE_HASH = hashlib.sha256((ROOT / 'common/src/main/resources' / TEXTURE).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref', default='HEAD')
    args = parser.parse_args()
    ref = subprocess.check_output(['git', 'rev-parse', '--verify', args.ref + '^{commit}'], cwd=ROOT, text=True).strip()
    version = re.search(r"version\s*=\s*'([^']+)'", (ROOT / 'build.gradle').read_text(encoding='utf-8')).group(1)
    dirty = subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=normal'], cwd=ROOT, text=True)
    assert not dirty.strip(), 'Commit the release tree first'
    committed = subprocess.check_output(['git', 'show', ref + ':build.gradle'], cwd=ROOT).decode('utf-8')
    assert re.search(r"version\s*=\s*'([^']+)'", committed).group(1) == version
    out = ROOT / 'dist/publish' / version
    out.mkdir(parents=True, exist_ok=True)
    artifacts = []
    for target, major in TARGETS.items():
        module = ROOT / 'versions' / target
        build_text = (module / 'build.gradle').read_text(encoding='utf-8')
        assert re.search(r"version\s*=\s*'([^']+)'", build_text).group(1) == version
        # The source ref must contain this build's complete public source tree.
        differences = subprocess.check_output(['git', 'diff', '--name-only', ref, '--', 'common', str(module.relative_to(ROOT))], cwd=ROOT, text=True)
        assert not differences.strip(), f'Uncommitted source changes for {target}'
        stem = f'wrenchbridge-{target}-{version}'
        for suffix in ('.jar', '-sources.jar'):
            source = module / 'build/libs' / (stem + suffix)
            with ZipFile(source) as z:
                assert z.testzip() is None, source.name
                names = z.namelist()
                assert len(names) == len(set(names)), f'Duplicate ZIP entries in {source.name}'
                assert not any('/verification/' in n for n in names), 'Test fixture leaked into release'
                assert 'LICENSE-wrenchbridge.txt' in names
                if suffix == '.jar':
                    assert struct.unpack('>H', z.read('dev/wrenchbridge/WrenchBridge.class')[6:8])[0] == major
                    assert hashlib.sha256(z.read(TEXTURE)).hexdigest() == TEXTURE_HASH
                    assert 'dev/wrenchbridge/mixin/ServerFeedbackMixin.class' in names
                    assert 'dev/wrenchbridge/SharingPacket.class' in names
                    for name in names:
                        if name.endswith('.json'):
                            json.loads(z.read(name))
                    metadata = 'mcmod.info' if target == 'forge-1.12.2' else 'META-INF/' + ('neoforge.mods.toml' if target.startswith('neoforge') else 'mods.toml')
                    assert version in z.read(metadata).decode('utf-8')
                    if target.startswith('forge'):
                        mappings = json.loads(z.read('wrenchbridge.refmap.json'))['mappings']
                        assert any('Shared' in key and 'Client' in key for key in mappings), 'Missing client remapping'
                        assert any('Shared' in key and 'Server' in key for key in mappings), 'Missing server remapping'
                    if target == 'forge-1.16.5':
                        assert 'com/llamalad7/mixinextras/MixinExtrasBootstrap.class' in names
                    if target == 'forge-1.20.1':
                        assert any(n.startswith('META-INF/jarjar/') and n.endswith('.jar') for n in names), 'Missing bundled MixinExtras'
            dest = out / source.name
            shutil.copy2(source, dest)
            artifacts.append(dest)
    archive = out / f'wrenchbridge-{version}-source.zip'
    subprocess.run(['git', 'archive', '--format=zip', f'--prefix=wrenchbridge-{version}/', f'--output={archive}', ref], cwd=ROOT, check=True)
    with ZipFile(archive) as z:
        forbidden = {'.git', '.gradle', '.toolchains', 'build', 'dist', '__pycache__'}
        assert z.testzip() is None
        assert not any(any(p in forbidden or p in {'run', 'runs'} or p.startswith('run-') for p in Path(n).parts) for n in z.namelist())
    artifacts.append(archive)
    (out / 'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in artifacts), encoding='utf-8')
    print(f'Validated {len(TARGETS)} runtime/source pairs; source ref {ref}; artifacts: {out}')


if __name__ == '__main__':
    main()
