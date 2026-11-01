"""Local TEST fallback: four real JARs and three isolated PostgreSQL17 clusters.

Does not change Docker, Windows services, existing databases, or authentication.
Only the test namespace's recorded processes/data directories are managed.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
HIDDEN = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def run(args, env=None, stdin=None):
    # PostgreSQL descendants can inherit a PIPE handle on Windows after pg_ctl exits.
    # A regular file lets the short-lived command finish without waiting for server EOF.
    with tempfile.TemporaryFile(mode='w+b') as output:
        result = subprocess.run([str(x) for x in args], input=stdin, text=True, env=env,
                                stdout=output, stderr=subprocess.STDOUT,
                                creationflags=HIDDEN, timeout=120)
        output.seek(0)
        captured = output.read().decode('utf-8', errors='replace')
    if result.returncode:
        (ROOT / '.tools/w6-host-command-failure.log').write_text(captured, encoding='utf-8')
        raise RuntimeError(f'{Path(args[0]).name} failed (exit {result.returncode}); inspect local TEST logs')
    return captured.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop', 'stop-assessment', 'start-assessment',
                                         'stop-api', 'start-api',
                                         'stop-assessment-db', 'start-assessment-db'])
    parser.add_argument('--namespace', required=True)
    parser.add_argument('--pg-bin', type=Path, required=True)
    parser.add_argument('--huy-root', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'khoa-w6-host-[a-z0-9-]+', args.namespace):
        parser.error('Select an explicit isolated khoa-w6-host-* TEST namespace.')
    area = ROOT / '.tools' / args.namespace
    area.mkdir(parents=True, exist_ok=True)
    state_path = area / 'runtime.json'
    pg_bin = args.pg_bin.resolve()
    if not run([pg_bin / 'postgres.exe', '--version']).startswith('postgres (PostgreSQL) 17.'):
        parser.error('PostgreSQL17 is required.')
    java = ROOT / '.tools/jdk/jdk-25.0.5+8/bin/java.exe'
    key_dir = ROOT / '.secrets/keys'
    common = os.environ.copy()
    common.update({'SERVER_ADDRESS': '127.0.0.1', 'PLATFORM_KEYS': str(key_dir / 'public'),
                   'USER_URL': 'http://127.0.0.1:8081', 'CONTENT_URL': 'http://127.0.0.1:8082',
                   'ASSESSMENT_URL': 'http://127.0.0.1:8090', 'PROGRESS_URL': 'http://127.0.0.1:18099',
                   'FRONTEND_ORIGIN': 'http://127.0.0.1:5173', 'SECURE_COOKIES': 'false',
                   'KAFKA_ENABLED': 'false', 'MIGRATE': 'false', 'PLATFORM_MODE': 'serve'})
    common['PATH'] = str(pg_bin) + os.pathsep + common.get('PATH', '')
    specs = {
        'user': (8081, ROOT / 'backend/services/user-service/target/user-service-0.1.0-SNAPSHOT.jar'),
        'content': (8082, ROOT / 'backend/services/learning-content-service/target/content-service-0.1.0-SNAPSHOT.jar'),
        'api': (18080, ROOT / 'backend/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar'),
        'assessment': (8090, args.huy_root.resolve() / 'backend/services/assessment-service/target/assessment-service-0.1.0-SNAPSHOT.jar')}
    db_ports = {'user': 15541, 'content': 15542, 'assessment': 15543}
    secret_file = area / 'postgres-password'
    if not secret_file.exists():
        secret_file.write_text(secrets.token_hex(32), encoding='ascii')
    pg_env = common | {'PGPASSWORD': secret_file.read_text().strip()}

    def sql(service, query, database=None):
        return run([pg_bin / 'psql.exe', '-h', '127.0.0.1', '-p', str(db_ports[service]),
                    '-U', 'postgres', '-d', database or service + '_db', '-At',
                    '-v', 'ON_ERROR_STOP=1'], env=pg_env, stdin=query)

    def database(service, action):
        data = area / (service + '-data')
        if action == 'start':
            run([pg_bin / 'pg_ctl.exe', '-D', data, '-l', area / (service + '-postgres.log'),
                 '-o', f'-h 127.0.0.1 -p {db_ports[service]}', '-w', 'start'], env=pg_env)
        else:
            run([pg_bin / 'pg_ctl.exe', '-D', data, '-m', 'fast', '-w', 'stop'], env=pg_env)

    def service_env(service):
        env = common | {'SERVER_PORT': str(specs[service][0]),
                        'PRIVATE_KEY_FILE': str(key_dir / (service + '.private.pem'))}
        if service != 'api':
            env['DB_URL'] = f'jdbc:postgresql://127.0.0.1:{db_ports[service]}/{service}_db'
            env['DB_USER'] = service if service == 'assessment' else service + '_runtime'
            env['DB_PASSWORD'] = secret_file.read_text().strip() if service == 'assessment' else (ROOT / '.secrets' / (service + '_runtime')).read_text().strip()
        return env

    def health(service):
        url = f'http://127.0.0.1:{specs[service][0]}/actuator/health'
        for _ in range(90):
            try:
                with urllib.request.urlopen(url, timeout=2) as response:
                    if json.load(response)['status'] == 'UP':
                        return
            except Exception:
                pass
            time.sleep(1)
        raise RuntimeError(service + ' health did not become UP; inspect local TEST logs')

    def launch(service, state):
        port, jar = specs[service]
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', port)) == 0:
                raise RuntimeError(f'Port {port} is occupied; existing processes are preserved')
        with (area / (service + '-app.log')).open('ab') as log:
            process = subprocess.Popen([str(java), '-Xmx256m', '-Duser.timezone=UTC', '-jar', str(jar)],
                                       env=service_env(service), stdout=log, stderr=log, creationflags=HIDDEN)
        started = run(['powershell.exe', '-NoProfile', '-Command',
                       f"(Get-Process -Id {process.pid}).StartTime.ToUniversalTime().ToString('o')"])
        state['services'][service] = {'pid': process.pid, 'processPath': str(java),
            'processStartedAt': started, 'port': port, 'jarPath': str(jar), 'jarSha256': sha(jar),
            'sourceSha': state['assessmentSha'] if service == 'assessment' else state['backendSha']}
        save(state_path, state)

    def stop_service(service, state):
        identity = state['services'][service]
        # Match PID, start time, executable and listening port before stopping only our process.
        command = (f"$p=Get-Process -Id {identity['pid']} -ErrorAction Stop; "
                   f"if($p.StartTime.ToUniversalTime().ToString('o') -ne '{identity['processStartedAt']}'){{throw 'PID reused'}}; "
                   f"if($p.Path -ne '{identity['processPath']}'){{throw 'Executable mismatch'}}; "
                   f"$p | Stop-Process -Force")
        run(['powershell.exe', '-NoProfile', '-Command', command])

    if args.action != 'start':
        state = json.loads(state_path.read_text())
        if state['namespace'] != args.namespace:
            raise RuntimeError('Runtime namespace mismatch')
        if args.action == 'stop-assessment':
            stop_service('assessment', state)
        elif args.action == 'start-assessment':
            launch('assessment', state)
            health('assessment')
        elif args.action == 'stop-assessment-db':
            database('assessment', 'stop')
        elif args.action == 'start-assessment-db':
            database('assessment', 'start')
            health('assessment')
        elif args.action == 'stop-api':
            stop_service('api', state)
        elif args.action == 'start-api':
            launch('api', state)
            health('api')
        else:
            for service in ['api', 'assessment', 'content', 'user']:
                stop_service(service, state)
            for service in db_ports:
                database(service, 'stop')
            state['stoppedAt'] = datetime.now(timezone.utc).isoformat()
            save(state_path, state)
        print(args.action + ': complete; TEST data preserved')
        return
    if state_path.exists():
        raise RuntimeError('Namespace already initialized; choose a fresh namespace or use a specific recovery action')
    state = {'namespace': args.namespace, 'runtimeMode': 'HOST',
             'backendSha': run(['git', 'rev-parse', 'HEAD']),
             'assessmentSha': run(['git', '-C', args.huy_root, 'rev-parse', 'HEAD']),
             'startedAt': datetime.now(timezone.utc).isoformat(),
             'contentAssessmentUrl': common['ASSESSMENT_URL'], 'services': {}, 'databases': {}}
    for service, port in db_ports.items():
        data = area / (service + '-data')
        if data.exists():
            raise RuntimeError('Fresh namespace required; existing database data is preserved')
        run([pg_bin / 'initdb.exe', '-D', data, '-U', 'postgres', '--pwfile', secret_file,
             '--auth-host=scram-sha-256', '--auth-local=scram-sha-256', '--encoding=UTF8', '--locale=C'], env=pg_env)
        database(service, 'start')
        roles = [service] if service == 'assessment' else [service + '_migration', service + '_runtime']
        for role in roles:
            password = secret_file.read_text().strip() if service == 'assessment' else (ROOT / '.secrets' / role).read_text().strip()
            sql(service, f"CREATE ROLE {role} LOGIN PASSWORD '{password.replace(chr(39), chr(39)*2)}';", 'postgres')
        owner = service if service == 'assessment' else service + '_migration'
        sql(service, f'CREATE DATABASE {service}_db OWNER {owner};', 'postgres')
        sql(service, f'REVOKE ALL ON DATABASE {service}_db FROM PUBLIC; GRANT CONNECT ON DATABASE {service}_db TO ' + ','.join(roles) + '; REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT ALL ON SCHEMA public TO ' + owner + ';')
        state['databases'][service] = {'port': port, 'dataDirectory': str(data),
            'version': sql(service, 'SELECT version();')}
    for service in ['user', 'content']:
        env = service_env(service) | {'MIGRATE': 'true', 'PLATFORM_MODE': 'migrate', 'SERVER_PORT': '0',
            'DB_USER': service + '_migration', 'DB_PASSWORD': (ROOT / '.secrets' / (service + '_migration')).read_text().strip()}
        output = run([java, '-Xmx256m', '-jar', specs[service][1]], env=env)
        (area / (service + '-migration.log')).write_text(output, encoding='utf-8')
    bootstrap = service_env('user') | {'PLATFORM_MODE': 'bootstrap-admin', 'SERVER_PORT': '0',
        'ADMIN_EMAIL': 'admin@example.com', 'ADMIN_PASSWORD': (ROOT / '.secrets/admin_password').read_text().strip()}
    run([java, '-Xmx256m', '-jar', specs['user'][1]], env=bootstrap)
    seed = service_env('content') | {'PLATFORM_MODE': 'seed', 'SERVER_PORT': '0',
        'SEED_FILE': str(ROOT / 'backend/services/learning-content-service/deploy/course-levels.json')}
    run([java, '-Xmx256m', '-jar', specs['content'][1]], env=seed)
    for service in specs:
        launch(service, state)
    for service in specs:
        health(service)
    for service in db_ports:
        state['databases'][service]['flyway'] = json.loads(sql(service,
            'SELECT json_agg(x) FROM (SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank) x;'))
    save(state_path, state)
    manifest = json.loads((ROOT / 'docs/fixtures/readiness-live.example.json').read_text())
    manifest.update({'runtimeMode': 'HOST', 'backendSha': state['backendSha'],
        'gatewayUrl': 'http://127.0.0.1:18080', 'assessmentUrl': common['ASSESSMENT_URL'],
        'assessmentUrlFromContent': common['ASSESSMENT_URL'], 'hostRuntimeEvidence': str(state_path)})
    manifest['assessment'] = {'kind': 'LIVE', 'sourceSha': state['assessmentSha'],
        'image': 'NOT_APPLICABLE_HOST_JAR', 'jarSha256': state['services']['assessment']['jarSha256'],
        'dbEngine': state['databases']['assessment']['version'], 'migration': 'V1__assessment_foundation.sql',
        'seedCommand': 'python scripts/verify-assessment-live.py --phase seed --manifest <prepared> --report <fresh>',
        'runtimeEvidence': str(state_path)}
    save(area / 'manifest.json', manifest)
    print('HOST LIVE runtime UP; provenance: ' + str(state_path))


if __name__ == '__main__':
    main()
