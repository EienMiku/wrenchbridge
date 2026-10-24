"""Resolve and checksum the additional 1.21.1 / NeoForge lab mods from Modrinth."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.toolchains/expanded-lab'
DOWNLOADS = CACHE / 'downloads'
SEEDS = ['pneumaticcraft-repressurized', 'pretty-pipes', 'refined-storage', 'powah',
         'enderio', 'rftools-base', 'rftools-utility', 'integrated-dynamics',
         'modern-dynamics', 'pipez', 'supplementaries']
QUERY = urllib.parse.urlencode({'loaders': json.dumps(['neoforge']), 'game_versions': json.dumps(['1.21.1'])})
HEADERS = {'User-Agent': 'WrenchBridgeLocalLab/0.1 (local compatibility testing)'}


def get(path):
    request = urllib.request.Request('https://api.modrinth.com/v2/' + path, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def resolve():
    selected = {}
    pending = [(slug, None) for slug in SEEDS]
    while pending:
        slug, pinned = pending.pop(0)
        project = get('project/' + slug)
        project_id = project['id']
        if project_id in selected:
            if pinned:
                assert selected[project_id]['version_id'] == pinned, 'Conflicting pinned dependency'
            continue
        versions = [get('version/' + pinned)] if pinned else get('project/' + project_id + '/version?' + QUERY)
        versions = [v for v in versions if '1.21.1' in v['game_versions'] and 'neoforge' in v['loaders']]
        releases = [v for v in versions if v['version_type'] == 'release']
        candidates = releases or versions
        assert candidates, 'No matching NeoForge 1.21.1 version: ' + project['slug']
        version = candidates[0]
        file = next((f for f in version['files'] if f.get('primary') and f['filename'].endswith('.jar')),
                    next(f for f in version['files'] if f['filename'].endswith('.jar')))
        selected[project_id] = {
            'name': project['title'], 'slug': project['slug'], 'project_id': project_id,
            'version_id': version['id'], 'version': version['version_number'], 'release_type': version['version_type'],
            'file': file['filename'], 'url': file['url'], 'sha512': file['hashes']['sha512'],
            'bytes': file['size'], 'project_url': 'https://modrinth.com/mod/' + project['slug'],
            'dependencies': version['dependencies']
        }
        print('Resolved:', project['title'], version['version_number'], flush=True)
        for dependency in version['dependencies']:
            if dependency['dependency_type'] != 'required':
                continue
            dep_id = dependency['project_id']
            if dep_id is None and dependency['version_id']:
                dep_id = get('version/' + dependency['version_id'])['project_id']
            assert dep_id, 'Unresolved required dependency: ' + project['slug']
            pending.append((dep_id, dependency['version_id']))
    return list(selected.values())


def download(entry):
    target = DOWNLOADS / entry['file']
    if not target.exists() or hashlib.sha512(target.read_bytes()).hexdigest() != entry['sha512']:
        request = urllib.request.Request(entry['url'], headers=HEADERS)
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
        assert hashlib.sha512(data).hexdigest() == entry['sha512'], 'Download checksum mismatch'
        target.write_bytes(data)
    with ZipFile(target) as jar:
        assert jar.testzip() is None
    entry['sha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
    print('Verified:', entry['file'], flush=True)
    return entry


def main():
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    lock = CACHE / 'mods.lock.json'
    # An existing lock makes repeat builds use the same tested versions.
    portable_lock = ROOT / 'docs/expanded-mods.lock.json'
    existing_lock = lock if lock.exists() else portable_lock
    entries = json.loads(existing_lock.read_text(encoding='utf-8')) if existing_lock.exists() else resolve()
    with ThreadPoolExecutor(max_workers=5) as pool:
        entries = list(pool.map(download, entries))
    lock.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Locked and verified {len(entries)} additional mods and dependencies.')


if __name__ == '__main__':
    main()
