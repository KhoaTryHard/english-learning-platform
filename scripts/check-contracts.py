"""Verify frozen baselines and owner operation inventory without modifying sources."""
from pathlib import Path
import hashlib,json,yaml

root=Path(__file__).resolve().parents[1]
hashes=json.loads((root/'docs/contracts/baseline-sha256.json').read_text())
for name,expected in hashes.items():
    assert hashlib.sha256((root/'docs/contracts'/name).read_bytes()).hexdigest()==expected,name
public=yaml.safe_load((root/'docs/contracts/public-v0.1.yaml').read_text(encoding='utf-8'))
counts={s:0 for s in ('User Service','Learning Content Service')}
for path,methods in public['paths'].items():
    for method,operation in methods.items():
        if isinstance(operation,dict) and operation.get('x-owner-service') in counts:
            counts[operation['x-owner-service']]+=1
            assert not (method=='delete' and any(x in path for x in ('courses','modules','lessons')))
assert counts=={'User Service':11,'Learning Content Service':32},counts
proposal=yaml.safe_load((root/'docs/contracts/internal-v0.1.1-proposal.yaml').read_text(encoding='utf-8'))
request=proposal['components']['schemas']['SessionCheckRequest']
assert request['required']==['token','requestMethod']
assert request['additionalProperties'] is False
print('Frozen SHA-256 and 43 public owner operations: PASS; internal 0.1.1 is separate')
