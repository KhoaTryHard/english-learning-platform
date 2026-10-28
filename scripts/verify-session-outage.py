"""Fault injection for an explicitly selected disposable Compose test environment.

Requires ALLOW_TEST_FAULT_INJECTION=1 and COMPOSE_PROJECT_NAME. Restores session SELECT
permission in finally. Never run on a shared/live application database.
"""
from pathlib import Path
import json
import os
import subprocess
import uuid
from smoke import Client, ROOT


def sql(service, statement):
    return subprocess.check_output(['docker', 'compose', 'exec', '-T', service + '-db',
        'psql', '-U', 'postgres', '-d', service + '_db', '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement],
        cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def main():
    if os.environ.get('ALLOW_TEST_FAULT_INJECTION') != '1' or not os.environ.get('COMPOSE_PROJECT_NAME'):
        raise SystemExit('Select a disposable COMPOSE_PROJECT_NAME and set ALLOW_TEST_FAULT_INJECTION=1.')
    admin = Client()
    admin.login(os.environ.get('ADMIN_EMAIL', 'admin@example.com'), (ROOT / '.secrets/admin_password').read_text().strip())
    body = {'name': 'Session regression ' + uuid.uuid4().hex}
    before = sql('content', 'SELECT count(*) FROM course')
    token = admin.csrf
    admin.csrf = 'wrong-csrf'
    assert admin.request('POST', '/api/v1/admin/courses', body, 403)['code'] == 'CSRF_INVALID'
    admin.csrf = token
    cookie = next(c for c in admin.jar if c.name == 'csrf_session')
    old = cookie.value
    try:
        cookie.value = str(uuid.uuid4())
        assert admin.request('POST', '/api/v1/admin/courses', body, 403)['code'] == 'CSRF_INVALID'
    finally:
        cookie.value = old
    assert sql('user', "SELECT has_table_privilege('user_runtime','spring_session','SELECT')") == 't'
    try:
        sql('user', 'REVOKE SELECT ON spring_session FROM user_runtime')
        result = admin.request('POST', '/api/v1/admin/courses', body, 503)
        assert result['code'] == 'DEPENDENCY_UNAVAILABLE'
        assert 'spring_session' not in json.dumps(result)
        assert sql('content', 'SELECT count(*) FROM course') == before
    finally:
        sql('user', 'GRANT SELECT ON spring_session TO user_runtime')
    admin.op('createCourse', body)
    assert int(sql('content', 'SELECT count(*) FROM course')) == int(before) + 1
    report = {'status': 'PASS', 'scope': 'REAL Gateway/User/Content/PostgreSQL; session-table privilege fault only',
        'invalidCsrf': 403, 'missingSession': 403, 'sessionStoreFailure': 503,
        'failureCode': 'DEPENDENCY_UNAVAILABLE', 'contentRowsUnchangedOnFailure': True,
        'permissionRestored': True, 'recoveryCreatesCourse': True,
        'notCovered': ['network partition', 'pool exhaustion', 'full Assessment/Progress integration']}
    destination = Path(os.environ.get('SESSION_REPORT', str(ROOT / 'docs/evidence/session-runtime.json')))
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('Session runtime: invalid CSRF/session 403; repository failure 503; no business row; recovery PASS')


if __name__ == '__main__':
    main()
