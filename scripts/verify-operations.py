"""All-table backup/restore and restart checks for a TEST Compose project.

Source databases/volumes are never deleted. OPS_REPORT and GATEWAY_URL select output/endpoint.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def sql(service, database, statement):
    return command('docker', 'compose', 'exec', '-T', service + '-db', 'psql',
                   '-U', 'postgres', '-d', database, '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement)


def snapshot(service, database):
    tables = sql(service, database, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").splitlines()
    result = {}
    for table in tables:
        identifier = '"' + table.replace('"', '""') + '"'
        # Compare complete row multisets independent of physical order; never log raw data.
        rows = sql(service, database, 'SELECT md5(to_jsonb(t)::text) FROM public.' + identifier + ' t ORDER BY 1')
        result[table] = {'rows': len(rows.splitlines()), 'digest': hashlib.sha256(rows.encode()).hexdigest()}
    constraints = sql(service, database, "SELECT conrelid::regclass::text, conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY 1,2")
    # PostgreSQL can deparse a varchar-literal array cast as per-element text casts
    # after pg_dump/restore. Normalize only this equivalent literal-array form.
    def array_cast(match):
        values = match.group(1).split(', ')
        if not all(re.fullmatch(r"'[^']*'::character varying", value) for value in values):
            return match.group(0)
        return '(ARRAY[' + ', '.join('(' + value + ')::text' for value in values) + '])'
    constraints = re.sub(r'\(\(ARRAY\[(.*?)\]\)::text\[\]\)', array_cast, constraints)
    grants = sql(service, database, "SELECT relname, pg_get_userbyid(relowner), coalesce(relacl::text,'') FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind IN ('r','S') ORDER BY relname")
    return {'tables': result, 'constraints': constraints, 'ownersAndGrants': grants}


def wait_for_apps():
    for attempt in range(60):
        try:
            with urllib.request.urlopen(os.environ.get('GATEWAY_URL', 'http://localhost:8080').rstrip('/') + '/actuator/health', timeout=3) as response:
                assert json.load(response)['status'] == 'UP'
            for service, port in [('user', 8081), ('content', 8082)]:
                health = command('docker', 'compose', 'exec', '-T', service, 'curl', '-fsS', f'http://localhost:{port}/actuator/health')
                assert json.loads(health)['status'] == 'UP'
            return
        except (OSError, subprocess.CalledProcessError, AssertionError):
            time.sleep(2)
    raise AssertionError('Applications failed health after restart')


def main():
    reports = []
    before_restart = {}
    for service in ['user', 'content']:
        source = service + '_db'
        target = service + '_restore_' + uuid.uuid4().hex[:12]
        dump = '/tmp/' + target + '.dump'
        before = snapshot(service, source)
        sql(service, source, 'CREATE DATABASE ' + target)
        try:
            # Roles already exist in the same test cluster; preserve ownership and ACLs.
            command('docker', 'compose', 'exec', '-T', service + '-db', 'pg_dump', '-U', 'postgres', '-d', source, '-Fc', '-f', dump)
            command('docker', 'compose', 'exec', '-T', service + '-db', 'pg_restore', '-U', 'postgres', '-d', target, dump)
            assert before == snapshot(service, target), source + ' restore differs in rows/schema/grants'
            reports.append({'database': source, 'backupRestore': 'PASS', 'tables': before['tables'],
                            'constraints': 'PASS', 'ownersAndGrants': 'PASS', 'flywayHistory': 'PASS'})
        finally:
            # Only this invocation's randomly named database and dump are removed.
            sql(service, source, 'DROP DATABASE ' + target)
            command('docker', 'compose', 'exec', '-T', service + '-db', 'rm', '-f', dump)
        before_restart[service] = before

    command('docker', 'compose', 'restart', 'user-db', 'content-db')
    command('docker', 'compose', 'restart', 'user', 'content', 'api')
    wait_for_apps()
    for service, expected in before_restart.items():
        assert expected == snapshot(service, service + '_db'), service + ' changed after restart'
    assert command('docker', 'compose', 'exec', '-T', 'api', 'sh', '-c',
                   'test ! -e /run/secrets/user_runtime && test ! -e /run/secrets/content_runtime && echo isolated') == 'isolated'
    report = {'status': 'PASS', 'restartPersistence': 'PASS', 'restartScope': 'databases and applications',
              'gatewayNoDatabaseCredentials': 'PASS', 'restoreScope': 'new databases in same test cluster; not recovery to a new host',
              'databases': reports}
    destination = Path(os.environ.get('OPS_REPORT', str(ROOT / 'docs/verification-operations.json')))
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('All-table backup/restore, ownership/grants and DB/application restart persistence: PASS')


if __name__ == '__main__':
    main()
