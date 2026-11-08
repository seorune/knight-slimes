"""Package current art/audio assets and license notices on explicit request."""
from pathlib import Path
import argparse
import collections
import hashlib
import json
import zipfile


def package(root, output):
    root = Path(root).resolve()
    output = Path(output).resolve()
    files = set()
    for folder in ('assets/art', 'assets/audio'):
        base = root / folder
        if base.is_dir():
            files.update(p for p in base.rglob('*') if p.is_file() and p.suffix.lower() in ('.png', '.wav', '.json'))
    # Preserve legal notices alongside the media files.
    for base in (root, root / 'assets'):
        if not base.exists():
            continue
        candidates = base.iterdir() if base == root else base.rglob('*')
        files.update(p for p in candidates if p.is_file() and p.name.upper().startswith(('LICENSE', 'LICENCE', 'COPYING', 'COPYRIGHT', 'NOTICE', 'AUTHORS', 'CREDITS')))
    files.discard(output)
    files = sorted(files, key=lambda p: p.relative_to(root).as_posix())
    if not files:
        raise ValueError('No art or audio assets found')
    records = []
    for path in files:
        data = path.read_bytes()
        records.append({'file': path.relative_to(root).as_posix(), 'bytes': len(data),
                        'sha256': hashlib.sha256(data).hexdigest()})
    counts = dict(collections.Counter(Path(r['file']).suffix.lower() for r in records))
    manifest = {'schema_version': 1, 'description': 'Sprites, scenery, terrain, sound effects and license notices.',
                'file_count_excluding_this_manifest': len(records), 'extensions': counts, 'files': records}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path, record in zip(files, records):
            archive.write(path, record['file'])
        archive.writestr('PACK_MANIFEST.json', json.dumps(manifest, ensure_ascii=False, indent=2))
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('ZIP CRC check failed')
        if len(archive.namelist()) != len(records) + 1:
            raise RuntimeError('ZIP file count mismatch')
        for record in records:
            data = archive.read(record['file'])
            if len(data) != record['bytes'] or hashlib.sha256(data).hexdigest() != record['sha256']:
                raise RuntimeError('ZIP read-back mismatch: ' + record['file'])
    return {'zip': str(output), 'bytes': output.stat().st_size,
            'entries': len(records) + 1, 'extensions': counts, 'verified': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path, help='Destination archive path')
    args = parser.parse_args()
    print(json.dumps(package(Path(__file__).resolve().parents[1], args.output), ensure_ascii=False))
