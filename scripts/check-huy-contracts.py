"""Verify approved event bytes in canonical and standalone service resources."""
from pathlib import Path
import hashlib
root = Path(__file__).resolve().parents[1]
files = [
 root / "contracts/events/AssessmentCompleted_v1.schema.json",
 root / "backend/services/assessment-service/src/test/resources/contracts/AssessmentCompleted_v1.schema.json",
 root / "backend/services/progress-service/src/main/resources/contracts/AssessmentCompleted_v1.schema.json",
]
hashes = {hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
if len(hashes) != 1:
 raise SystemExit("FAIL: event contract bytes differ")
print("PASS: canonical and standalone event schemas match:", next(iter(hashes)))
