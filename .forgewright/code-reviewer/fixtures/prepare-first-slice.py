import importlib.util
import json
import os
from pathlib import Path
import sys
import time
import uuid

root = Path(os.environ['KHOA_REVIEW_ROOT']).resolve()
sys.path[:0] = [str(root / 'scripts'), str(root.parent.parent / 'english-learning-platform/.tools/pylibs')]
spec = importlib.util.spec_from_file_location('review_backlog', root / 'scripts/verify-khoa-backlog.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
runner.REPORT = {'checks': []}
admin = runner.admin_client()
course = '10000000-0000-4000-8000-000000000001'
admin.request('PATCH', '/api/v1/admin/courses/' + course, {'published': True})
module = admin.request('POST', '/api/v1/admin/modules', {'courseId': course,
    'title': 'Khoa FE22 ' + uuid.uuid4().hex, 'orderNo': int(time.time())}, 201)['moduleId']
admin.request('PATCH', '/api/v1/admin/modules/' + module, {'published': True})
lesson = admin.request('POST', '/api/v1/admin/lessons', {'moduleId': module,
    'title': 'Khoa review LIVE lesson', 'content': 'Khoa source integration', 'orderNo': 1}, 201)['lessonId']
exercise, question = runner.ready_lesson(admin, course, lesson)
imported = admin.upload_bytes('vocabulary', (root / 'docs/fixtures/vocabulary-valid.xlsx').read_bytes(),
    'vocabulary-valid.xlsx', lesson)
for vid in imported['createdIds']:
    admin.request('PATCH', '/api/v1/admin/vocabulary/' + vid, {'published': True})
stats = admin.request('GET', '/api/v1/admin/statistics', expected=404)
runner.REPORT.update(status='PASS', scope='fixture via owner APIs and Statistics absence probe',
    fixture={'courseId': course, 'moduleId': module, 'lessonId': lesson,
             'exerciseId': exercise, 'questionId': question, 'vocabularyIds': imported['createdIds']},
    statistics={'expected': 404, 'actual': 404, 'meaning': 'consumer absent at owner413a929; #36 remains BLOCKED'})
(root / '.tools/backlog-review-20261109/first-slice-fixture.json').write_text(
    json.dumps(runner.REPORT, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('PASS: new first-slice fixture; Statistics consumer remains absent at owner413a929')
