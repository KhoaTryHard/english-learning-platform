"""Verify published report checksums against Git blob bytes, independent of OS EOL."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--manifest', default='docs/evidence/khoa-backlog-2026-11-01/manifest.json')
args = parser.parse_args()
manifest_path = ROOT / args.manifest
manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
assert manifest['checksumScope'] == 'Git blob bytes (LF), not platform checkout line endings'
for report in manifest['reports']:
    path = (manifest_path.parent / report['report']).relative_to(ROOT).as_posix()
    raw = subprocess.check_output(['git', 'show', 'HEAD:' + path], cwd=ROOT)
    assert hashlib.sha256(raw).hexdigest() == report['sha256'], path
print('Committed backlog report checksums PASS: ' + str(len(manifest['reports'])))
