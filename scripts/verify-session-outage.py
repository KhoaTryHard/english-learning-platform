"""Fault injection for an explicitly selected disposable Compose test environment.

Requires ALLOW_TEST_FAULT_INJECTION=1 and COMPOSE_PROJECT_NAME. Restores session SELECT
permission in finally. Never run on a shared/live application database.
"""
from pathlib import Path
from datetime import datetime, timezone
import json
import os
import subprocess
import uuid
from smoke import Client, ROOT
from test_runtime import fresh_report, require_docker_test, write_report


def sql(service, statement):
    if os.environ.get('HOST_RUNTIME_EVIDENCE'):
        evidence = Path(os.environ['HOST_RUNTIME_EVIDENCE']).resolve()
        namespace = os.environ.get('LIVE_TEST_NAMESPACE')
        if not namespace or evidence.parent != (ROOT / '.tools' / namespace).resolve():
            raise AssertionError('Select this namespace\'s local HOST runtime evidence')
        runtime = json.loads(evidence.read_text())
        if runtime.get('runtimeMode') != 'HOST' or runtime.get('namespace') != namespace:
            raise AssertionError('HOST runtime namespace mismatch')
        env = os.environ.copy()
        env['PGPASSWORD'] = (evidence.parent / 'postgres-password').read_text().strip()
        return subprocess.check_output([str(Path(os.environ['HOST_PG_BIN']) / 'psql.exe'),
            '-h', '127.0.0.1', '-p', str(runtime['databases'][service]['port']), '-U', 'postgres',
            '-d', service + '_db', '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement],
            cwd=ROOT, text=True, env=env, stderr=subprocess.STDOUT).strip()
    return subprocess.check_output(['docker', 'compose', 'exec', '-T', service + '-db',
        'psql', '-U', 'postgres', '-d', service + '_db', '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement],
        cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def verify():
    namespace = os.environ.get('LIVE_TEST_NAMESPACE') if os.environ.get('HOST_RUNTIME_EVIDENCE') else os.environ.get('COMPOSE_PROJECT_NAME')
    if os.environ.get('ALLOW_TEST_FAULT_INJECTION') != '1' or not namespace:
        raise SystemExit('Select a disposable TEST namespace and set ALLOW_TEST_FAULT_INJECTION=1.')
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
        'runtimeMode': 'HOST' if os.environ.get('HOST_RUNTIME_EVIDENCE') else 'DOCKER', 'testNamespace': namespace,
        'invalidCsrf': 403, 'missingSession': 403, 'sessionStoreFailure': 503,
        'failureCode': 'DEPENDENCY_UNAVAILABLE', 'contentRowsUnchangedOnFailure': True,
        'permissionRestored': True, 'recoveryCreatesCourse': True,
        'notCovered': ['network partition', 'pool exhaustion', 'full Assessment/Progress integration']}
    print('Session runtime: invalid CSRF/session 403; repository failure 503; no business row; recovery PASS')
    return report


def main():
    destination = fresh_report('SESSION_REPORT', ROOT / 'docs/evidence/session-runtime.json')
    if not os.environ.get('HOST_RUNTIME_EVIDENCE'):
        require_docker_test(['user', 'content', 'user-db', 'content-db'])
    report = {'status': 'FAIL', 'environment': 'TEST',
              'backendSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'startedAt': datetime.now(timezone.utc).isoformat()}
    try:
        report.update(verify())
    except Exception as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        report['finishedAt'] = datetime.now(timezone.utc).isoformat()
        write_report(destination, report)


if __name__ == '__main__':
    main()
