"""Opt-in TEST seed and retry/retake acceptance through the real Gateway.

Uses only public APIs; readiness and fault phases remain in verify-readiness-live.ps1.
Reports contain case names, status codes and fixture IDs, never credentials or bodies.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


class AcceptanceError(Exception):
    pass


def write_json(path, value, exclusive=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x' if exclusive else 'w', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write('\n')


class Client:
    def __init__(self, base, origin, checks):
        self.base, self.origin, self.checks = base.rstrip('/'), origin, checks
        self.csrf = None
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def request(self, case, method, path, body=None, expected=200, headers=None):
        request_headers = {'Origin': self.origin, 'Content-Type': 'application/json'}
        if self.csrf:
            request_headers['X-CSRF-Token'] = self.csrf
        request_headers.update(headers or {})
        payload = None if body is None else json.dumps(body).encode('utf-8')
        request = urllib.request.Request(self.base + path, data=payload,
                                         headers=request_headers, method=method)
        try:
            response = self.http.open(request, timeout=40)
        except urllib.error.HTTPError as error:
            response = error
        except (urllib.error.URLError, TimeoutError):
            self.checks.append({'case': case, 'expected': expected, 'actual': 'TRANSPORT_ERROR'})
            raise AcceptanceError(case) from None
        with response:
            raw = response.read()
            self.checks.append({'case': case, 'method': method, 'path': path.split('?')[0],
                                'expected': expected, 'actual': response.status})
            if response.status != expected:
                raise AcceptanceError(case)
            value = json.loads(raw) if raw and 'json' in response.headers.get('Content-Type', '') else {}
            return value, response.headers

    def call(self, case, method, path, body=None, expected=200, headers=None):
        return self.request(case, method, path, body, expected, headers)[0]

    def login(self, email, password):
        self.csrf = self.call('csrf before login', 'GET', '/api/v1/auth/csrf')['csrfToken']
        self.call('login', 'POST', '/api/v1/auth/login', {'email': email, 'password': password})
        self.csrf = self.call('csrf after login', 'GET', '/api/v1/auth/csrf')['csrfToken']


def check(report, name, condition):
    report['checks'].append({'case': name, 'expected': True, 'actual': bool(condition)})
    if not condition:
        raise AcceptanceError(name)


def seed(manifest, manifest_path, admin, report):
    # The Admin listing reconciles a partially completed run without duplicate Exercises.
    existing, page = [], 0
    while True:
        result = admin.call('list Assessment fixtures', 'GET',
                            f"/api/v1/admin/assessments?courseId={manifest['courseId']}&page={page}&size=100")
        existing.extend(result['items'])
        if (page + 1) * 100 >= result['totalElements']:
            break
        page += 1
    for name, fixture in manifest['fixtures'].items():
        lesson = admin.call('inspect draft ' + name, 'GET',
                            '/api/v1/admin/lessons/' + fixture['lessonId'])
        check(report, name + ' belongs to prepared draft Module', lesson['published'] is False
              and lesson['moduleId'] == manifest['moduleId'])
        matches = [item for item in existing if item.get('lessonId') == fixture['lessonId']]
        check(report, name + ' unique mapping', len(matches) <= 1)
        if name == 'missingMapping':
            check(report, 'missingMapping has no Exercise', not matches)
            continue
        if matches:
            assessment_id = matches[0]['assessmentId']
            check(report, name + ' recorded ID matches',
                  fixture.get('exerciseId') in (None, assessment_id))
        else:
            result = admin.call('create ' + name, 'POST', '/api/v1/admin/assessments', {
                'type': 'EXERCISE', 'courseId': manifest['courseId'], 'lessonId': fixture['lessonId'],
                'title': 'W6 LIVE ' + name, 'timeLimitSeconds': None}, 201)
            assessment_id = result['assessmentId']
        fixture['exerciseId'] = assessment_id
        write_json(manifest_path, manifest)
        path = '/api/v1/admin/assessments/' + assessment_id
        detail = admin.call('inspect ' + name, 'GET', path)
        check(report, name + ' belongs to fixture', detail['lessonId'] == fixture['lessonId']
              and detail['courseId'] == manifest['courseId'] and detail['type'] == 'EXERCISE')
        if name == 'notReady':
            check(report, 'notReady remains draft without questions',
                  detail['ready'] is False and not detail['questions'])
            fixture['questionCount'] = 0
        else:
            questions = detail['questions']
            if not questions:
                questions = [admin.call('question ' + name, 'POST', path + '/questions', {
                    'questionType': 'TRUE_FALSE', 'content': 'False is a valid answer',
                    'points': 2, 'correctAnswer': False}, 201)]
            check(report, name + ' expected seed question', len(questions) == 1
                  and questions[0]['questionType'] == 'TRUE_FALSE'
                  and questions[0]['correctAnswer'] is False and questions[0]['points'] == 2)
            admin.call('ready ' + name, 'PATCH', path, {'ready': True})
            fixture['questionCount'], fixture['questionId'] = 1, questions[0]['questionId']
        write_json(manifest_path, manifest)
    report['status'] = 'SEED_READY_REVIEW_REQUIRED'


def retry(manifest, admin, report, origin):
    checks = report['checks']
    learner = Client(manifest['gatewayUrl'], origin, checks)
    anon = Client(manifest['gatewayUrl'], origin, checks)
    fixture = manifest['fixtures']['ready']
    assessment_id = fixture['exerciseId']
    path = f'/api/v1/assessments/{assessment_id}/attempts'
    anon.call('missing session', 'GET', f'/api/v1/assessments/{assessment_id}', expected=401)
    _, cors = anon.request('credentialed preflight', 'OPTIONS', path, expected=204, headers={
        'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'Idempotency-Key,X-CSRF-Token'})
    check(report, 'CORS allowed origin and credentials', cors.get('Access-Control-Allow-Origin') == origin
          and cors.get('Access-Control-Allow-Credentials') == 'true'
          and 'idempotency-key' in cors.get('Access-Control-Allow-Headers', '').lower())
    anon.call('foreign origin', 'OPTIONS', path, expected=403, headers={'Origin': 'http://invalid.example'})
    password = str(uuid.uuid4()) + '-Acceptance'
    email = 'attempt-' + uuid.uuid4().hex + '@example.com'
    learner.csrf = learner.call('register csrf', 'GET', '/api/v1/auth/csrf')['csrfToken']
    user = learner.call('register learner', 'POST', '/api/v1/auth/register', {
        'email': email, 'password': password, 'fullName': 'W6 LIVE retry'}, 201)
    learner.login(email, password)
    learner.call('learner setup', 'PUT', '/api/v1/me/learning-setup', {
        'courseId': manifest['courseId'], 'levelId': manifest['levelId']})
    learner.call('learner denied Admin API', 'GET', '/api/v1/admin/assessments', expected=403)
    admin.call('Admin denied Learner API', 'GET', f'/api/v1/assessments/{assessment_id}', expected=403)
    before = learner.call('attempt count before rejected start', 'GET', '/api/v1/me/attempts')['totalElements']
    learner.call('invalid CSRF', 'POST', path, expected=403,
                 headers={'Idempotency-Key': str(uuid.uuid4()), 'X-CSRF-Token': 'invalid'})
    after = learner.call('attempt count after rejected start', 'GET', '/api/v1/me/attempts')['totalElements']
    check(report, 'rejected start creates no attempt', before == after == 0)
    key = str(uuid.uuid4())
    first = learner.call('start', 'POST', path, expected=201, headers={'Idempotency-Key': key})
    same = learner.call('transport retry', 'POST', path, expected=201, headers={'Idempotency-Key': key})
    check(report, 'same key same attempt and snapshot', first['attemptId'] == same['attemptId']
          and first['startedAt'] == same['startedAt'] and first['questions'] == same['questions'])
    new = learner.call('retake', 'POST', path, expected=201,
                       headers={'Idempotency-Key': str(uuid.uuid4())})
    check(report, 'new key new attempt', first['attemptId'] != new['attemptId'])
    check(report, 'public question has no answer key', all(
        'correctAnswer' not in q and 'correctOptionId' not in q for q in first['questions']))
    attempt_path = '/api/v1/attempts/' + first['attemptId']
    learner.call('save false answer', 'PUT', attempt_path + '/answers', {'answers': [{
        'questionId': fixture['questionId'], 'questionType': 'TRUE_FALSE', 'value': False}]})
    result = learner.call('submit after save ACK', 'POST', attempt_path + '/submit')
    check(report, 'saved false answer scores correctly', result['scorePercent'] == 100
          and result['earnedPoints'] == result['maxPoints'] == 2)
    same = learner.call('retry after submit', 'POST', path, expected=201, headers={'Idempotency-Key': key})
    check(report, 'terminal retry retains attempt', same['attemptId'] == first['attemptId']
          and same['status'] == 'SUBMITTED')
    attempts = learner.call('count attempts after retries', 'GET', '/api/v1/me/attempts')
    check(report, 'retries created exactly two attempts', attempts['totalElements'] == 2)
    # Revoke the live session through the supported Admin API, then restore the test account.
    user_path = '/api/v1/admin/users/' + user['userId']
    try:
        admin.call('block test learner', 'PATCH', user_path, {'status': 'BLOCKED'})
        learner.call('BLOCKED session', 'GET', '/api/v1/me/profile', expected=401)
    finally:
        admin.call('restore test learner', 'PATCH', user_path, {'status': 'ACTIVE'})
    learner.login(email, password)
    old_cookie = '; '.join(cookie.name + '=' + cookie.value for cookie in learner.jar)
    learner.call('logout', 'POST', '/api/v1/auth/logout', expected=204)
    anon.call('replay revoked session', 'GET', '/api/v1/me/profile', expected=401,
              headers={'Cookie': old_cookie})
    report['attempts'] = {'first': first['attemptId'], 'sameKey': same['attemptId'],
                          'newKey': new['attemptId']}
    report['resultId'] = result.get('resultId')
    report['status'] = 'PASS_REVIEW_REQUIRED'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['seed', 'retry'], required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if args.report.exists():
        parser.error('Use a fresh report path; previous evidence is preserved.')
    manifest = json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    if (os.environ.get('RUN_LIVE_ASSESSMENT') != '1' or not os.environ.get('COMPOSE_PROJECT_NAME')
            or manifest.get('environment') != 'TEST'):
        parser.error('Set RUN_LIVE_ASSESSMENT=1 and COMPOSE_PROJECT_NAME for the isolated TEST stack.')
    url = urllib.parse.urlsplit(manifest['gatewayUrl'])
    if url.scheme != 'http' or url.hostname not in ('localhost', '127.0.0.1') or url.username or url.password:
        parser.error('This local acceptance helper requires a loopback Gateway URL.')
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if manifest.get('backendSha') != sha:
        parser.error('Manifest backendSha must equal the current checkout SHA.')
    if manifest.get('assessment', {}).get('kind') != 'LIVE':
        parser.error('An explicit LIVE Assessment manifest is required.')
    origin = os.environ.get('FRONTEND_ORIGIN', 'http://127.0.0.1:5173')
    report = {'phase': args.phase, 'checkedAt': datetime.now(timezone.utc).isoformat(),
              'backendSha': sha, 'assessment': manifest['assessment'],
              'composeProject': os.environ['COMPOSE_PROJECT_NAME'],
              'manifestSha256': hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
              'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'status': 'FAIL', 'checks': [], 'notCovered': ['Kafka/Progress E2E', 'team approval']}
    try:
        admin = Client(manifest['gatewayUrl'], origin, report['checks'])
        admin.login(os.environ.get('ADMIN_EMAIL', 'admin@example.com'),
                    (ROOT / '.secrets/admin_password').read_text().strip())
        if args.phase == 'seed':
            seed(manifest, args.manifest, admin, report)
        else:
            retry(manifest, admin, report, origin)
    except Exception as error:
        report['failureType'] = type(error).__name__
        if isinstance(error, AcceptanceError):
            report['failedCase'] = str(error)
    finally:
        report['fixtures'] = manifest['fixtures']
        write_json(args.report, report, exclusive=True)
    print(f"{args.phase}: {report['status']}; report: {args.report}")
    return 0 if report['status'] != 'FAIL' else 1


if __name__ == '__main__':
    sys.exit(main())
