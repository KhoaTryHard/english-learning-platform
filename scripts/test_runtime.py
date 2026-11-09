"""Preflight for destructive fault/restart checks in an isolated local TEST stack."""
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]


def require_docker_test(services):
    project = os.environ.get('COMPOSE_PROJECT_NAME', '')
    if (os.environ.get('ALLOW_TEST_FAULT_INJECTION') != '1'
            or not re.fullmatch(r'khoa-(?:backlog|alignment|w6-close|w8-41)-[a-z0-9-]+', project)):
        raise ValueError('Select an isolated khoa TEST project and ALLOW_TEST_FAULT_INJECTION=1.')
    url = urlsplit(os.environ.get('GATEWAY_URL', ''))
    if (url.scheme != 'http' or url.hostname not in ('127.0.0.1', 'localhost')
            or url.username or url.password or url.query or url.fragment or url.path not in ('', '/')):
        raise ValueError('Select an explicit loopback TEST Gateway URL.')
    gateway_port = url.port or 80
    for service in dict.fromkeys(['api', *services]):
        container = subprocess.check_output(['docker', 'compose', 'ps', '-q', service], cwd=ROOT, text=True).strip()
        if not container or len(container.splitlines()) != 1:
            raise ValueError('Expected one running TEST container for ' + service)
        info = json.loads(subprocess.check_output(['docker', 'inspect', container], cwd=ROOT, text=True))[0]
        labels = info['Config']['Labels']
        if (labels.get('com.docker.compose.project') != project
                or labels.get('com.docker.compose.service') != service):
            raise ValueError('TEST container identity mismatch: ' + service)
        bindings = [item for values in info['NetworkSettings']['Ports'].values() for item in (values or [])]
        if any(item['HostIp'] not in ('127.0.0.1', '::1') for item in bindings):
            raise ValueError('TEST ports must bind loopback: ' + service)
        if service.endswith('-db') and bindings:
            raise ValueError('TEST databases must have no published ports.')
        if service == 'api' and not any(int(item['HostPort']) == gateway_port for item in bindings):
            raise ValueError('Gateway URL does not address the selected TEST container.')
    return project


def fresh_report(variable, default):
    destination = Path(os.environ.get(variable, str(default))).resolve()
    if destination.exists():
        raise FileExistsError('Use a fresh report path; preserve previous evidence.')
    destination.parent.mkdir(parents=True, exist_ok=True)
    return destination


def write_report(destination, report):
    with destination.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(report, stream, indent=2, ensure_ascii=False)
        stream.write('\n')
