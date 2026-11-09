"""Khoa's TEST-only Content-owner probe; business fixtures are created via Admin API."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from datetime import datetime, timezone
import uuid

root = Path(os.environ['KHOA_REVIEW_ROOT']).resolve()
project = os.environ['COMPOSE_PROJECT_NAME']
if project != 'khoa-backlog-review-20261109':
    raise RuntimeError('Select this task-owned TEST project before injecting faults')
sys.path[:0] = [str(root / 'scripts'), str(root.parent.parent / 'english-learning-platform/.tools/pylibs')]
spec = importlib.util.spec_from_file_location('review_backlog', root / 'scripts/verify-khoa-backlog.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
report = {'status': 'RUNNING', 'scope': 'HTTP LIVE + PostgreSQL Content-owner fault injection',
          'startedAt': datetime.now(timezone.utc).isoformat(), 'project': project,
          'sourceSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
          'checks': []}
runner.REPORT = report

def command(*args):
    p = subprocess.run(args, cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if p.returncode:
        raise RuntimeError('TEST command failed: ' + ' '.join(args[:3]))
    return p.stdout.strip()

def sql(statement):
    return command('docker', 'compose', 'exec', '-T', 'content-db', 'psql', '-U', 'postgres',
                   '-d', 'content_db', '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement)

def check(case, expected, actual):
    report['checks'].append({'case': case, 'expected': expected, 'actual': actual})
    if actual != expected:
        raise AssertionError(case)

def cleanup():
    sql('DROP TRIGGER IF EXISTS khoa_review_fail_second ON vocabulary; '
        'DROP FUNCTION IF EXISTS khoa_review_fail_second(); '
        'DROP SEQUENCE IF EXISTS khoa_review_import_attempts;')

db_stopped = False
fault_installed = False
try:
    container = command('docker', 'compose', 'ps', '-q', 'content-db')
    metadata = json.loads(command('docker', 'inspect', container))[0]
    check('fault target belongs to task TEST project', project,
          metadata['Config']['Labels'].get('com.docker.compose.project'))
    check('fault target is Content owner DB', 'content-db',
          metadata['Config']['Labels'].get('com.docker.compose.service'))
    admin = runner.admin_client()
    course = '10000000-0000-4000-8000-000000000001'
    module = admin.request('POST', '/api/v1/admin/modules',
        {'courseId': course, 'title': 'Khoa rollback review ' + uuid.uuid4().hex,
         'orderNo': int(time.time())}, 201)['moduleId']
    lesson = admin.request('POST', '/api/v1/admin/lessons',
        {'moduleId': module, 'title': 'Khoa rollback fixture', 'content': 'TEST', 'orderNo': 1}, 201)['lessonId']
    report['fixture'] = {'courseId': course, 'moduleId': module, 'lessonId': lesson,
                         'file': 'docs/fixtures/vocabulary-valid.xlsx'}
    # Sequence increments are deliberately non-transactional: last_value=2 proves
    # the second INSERT was reached after the first INSERT, even when rows roll back.
    sql('CREATE SEQUENCE khoa_review_import_attempts; '
        'GRANT USAGE ON SEQUENCE khoa_review_import_attempts TO content_runtime; '
        'CREATE FUNCTION khoa_review_fail_second() RETURNS trigger LANGUAGE plpgsql AS $$ '
        f"BEGIN IF NEW.lesson_id = '{lesson}'::uuid THEN "
        "IF nextval('khoa_review_import_attempts') = 2 THEN "
        "RAISE EXCEPTION 'Khoa TEST second-insert conflict' USING ERRCODE='23505'; "
        'END IF; END IF; RETURN NEW; END; $$; '
        'CREATE TRIGGER khoa_review_fail_second BEFORE INSERT ON vocabulary '
        'FOR EACH ROW EXECUTE FUNCTION khoa_review_fail_second();')
    fault_installed = True
    upload = (root / report['fixture']['file']).read_bytes()
    rejected = admin.upload_bytes('vocabulary', upload, 'vocabulary-valid.xlsx', lesson, expected=409)
    check('both INSERT statements reached real PostgreSQL', '2', sql('SELECT last_value FROM khoa_review_import_attempts'))
    check('rejected import createdCount', 0, rejected['import']['createdCount'])
    check('rejected import createdIds', [], rejected['import']['createdIds'])
    check('first inserted row was rolled back', 0,
          admin.request('GET', f'/api/v1/admin/vocabulary?lessonId={lesson}')['totalElements'])
    cleanup()
    fault_installed = False
    accepted = admin.upload_bytes('vocabulary', upload, 'vocabulary-valid.xlsx', lesson, expected=201)
    check('recovery creates both rows', 2, accepted['createdCount'])
    check('recovery persisted both rows', 2,
          admin.request('GET', f'/api/v1/admin/vocabulary?lessonId={lesson}')['totalElements'])
    command('docker', 'compose', 'stop', 'content-db')
    db_stopped = True
    admin.upload_bytes('vocabulary', upload, 'vocabulary-valid.xlsx', lesson, expected=503)
    command('docker', 'compose', 'start', '--wait', 'content-db')
    db_stopped = False
    recovered = None
    for _ in range(30):
        try:
            recovered = admin.request('GET', f'/api/v1/admin/vocabulary?lessonId={lesson}')
            break
        except Exception:
            time.sleep(1)
    check('DB outage and recovery preserved count', 2, recovered['totalElements'] if recovered else None)
    check('ephemeral trigger removed', '0', sql("SELECT count(*) FROM pg_trigger WHERE tgname='khoa_review_fail_second'"))
    report['status'] = 'PASS'
except Exception as error:
    report['status'] = 'FAIL'
    report['failure'] = str(error)
    raise
finally:
    if db_stopped:
        command('docker', 'compose', 'start', '--wait', 'content-db')
    if fault_installed:
        cleanup()
    report['finishedAt'] = datetime.now(timezone.utc).isoformat()
    output = root / '.tools/backlog-review-20261109/import-rollback.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'checks': len(report['checks']), 'report': str(output)}))
