"""Explicitly vendor the infrastructure SDK; --check is non-mutating."""
from pathlib import Path
import argparse,hashlib,json,sys

ROOT=Path(__file__).resolve().parents[1]
SERVICES=('backend/services/user-service','backend/services/learning-content-service','backend/api-gateway')
PARTS=('src/main/java/vn/group7/core','src/main/resources','src/test/java/vn/group7/core')
def snapshot():
    base=ROOT/'shared/platform-core'
    return {str(p.relative_to(base)).replace('\\','/'):p.read_bytes().replace(b'\r\n',b'\n')
            for part in PARTS for p in (base/part).rglob('*') if p.is_file()}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    source=snapshot();failures=[]
    for service in SERVICES:
        for relative,data in source.items():
            target=ROOT/service/relative
            if args.check:
                if not target.exists() or target.read_bytes().replace(b'\r\n',b'\n')!=data:failures.append(service+'/'+relative)
            else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        manifest={'version':'0.1.0','source':'shared/platform-core','sha256':{k:hashlib.sha256(v).hexdigest() for k,v in sorted(source.items())}}
        manifest_path=ROOT/service/'sdk-snapshot.json'
        if args.check:
            if not manifest_path.exists() or json.loads(manifest_path.read_text())!=manifest:failures.append(service+'/sdk-snapshot.json')
        else:manifest_path.write_bytes(json.dumps(manifest,indent=2).encode('utf-8'))
    if failures:raise SystemExit('SDK snapshot drift: '+', '.join(failures))
    print('Infrastructure snapshots '+('verified' if args.check else 'updated')+' for all standalone applications')
if __name__=='__main__':main()
