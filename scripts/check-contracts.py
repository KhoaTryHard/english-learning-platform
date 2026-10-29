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
routes=json.loads((root/'backend/api-gateway/src/main/resources/public-routes.json').read_text())
expected={(('/api/v1'+path),method.upper()):(operation['x-owner-service'],operation['x-roles'])
          for path,methods in public['paths'].items() for method,operation in methods.items()
          if isinstance(operation,dict) and 'x-owner-service' in operation}
assert len(routes)==len(expected)==63
assert {(r['path'],r['method']):(r['owner'],r['roles']) for r in routes}==expected
proposal=yaml.safe_load((root/'docs/contracts/internal-v0.1.1-proposal.yaml').read_text(encoding='utf-8'))
request=proposal['components']['schemas']['SessionCheckRequest']
assert request['required']==['token','requestMethod']
assert request['additionalProperties'] is False
collection=json.loads((root/'docs/postman/Khoa-services.postman_collection.json').read_text(encoding='utf-8'))
requests=[item for folder in collection['item'] for item in folder['item']]
assert len(requests)==43
assert all(item['request']['url'].startswith('{{baseUrl}}/api/v1') for item in requests)
print('Frozen SHA-256 and 43 public owner operations: PASS; internal 0.1.1 is separate')
print('Gateway routes: all 63 approved operations have matching owner and roles')
