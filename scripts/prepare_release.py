"""Prepare the tested 1.21.1 release and a source archive from a Git ref; never uploads."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--ref', default='HEAD')
args = parser.parse_args()
module = root / 'versions/neoforge-1.21.1'
version = re.search(r"version\s*=\s*'([^']+)'", (module / 'build.gradle').read_text(encoding='utf-8')).group(1)
out = root / 'dist/publish' / version
out.mkdir(parents=True, exist_ok=True)
stem = f'wrenchbridge-neoforge-1.21.1-{version}'
files = []
for suffix in ('.jar', '-sources.jar'):
    source = module / 'build/libs' / (stem + suffix)
    with ZipFile(source) as z:
        assert z.testzip() is None, source.name
        assert not any('/verification/' in n for n in z.namelist()), 'Verification fixture in release JAR'
        if suffix == '.jar':
            assert version in z.read('META-INF/neoforge.mods.toml').decode()
            assert 'dev/wrenchbridge/mixin/ServerFeedbackMixin.class' in z.namelist()
    target = out / source.name
    shutil.copy2(source, target)
    files.append(target)
archive = out / f'wrenchbridge-{version}-source.zip'
subprocess.run(['git', 'archive', '--format=zip', f'--prefix=wrenchbridge-{version}/', f'--output={archive}', args.ref], cwd=root, check=True)
with ZipFile(archive) as z:
    assert z.testzip() is None
    forbidden = {'.git', '.gradle', '.toolchains', 'dist', 'build', '__pycache__'}
    assert not any(any(p in forbidden or p in {'run', 'runs'} or p.startswith('run-') for p in Path(n).parts) for n in z.namelist())
files.append(archive)
(out / 'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files), encoding='utf-8')
print(f'Prepared {len(files)} release artifacts and SHA256SUMS.txt in {out}')
