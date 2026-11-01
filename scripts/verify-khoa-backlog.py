"""Opt-in acceptance for Khoa's isolated Docker TEST project; no production writes.

Each phase requires a fresh report. Credentials stay beside the ignored state file;
published reports contain fixture IDs, expected/actual and selected runtime metadata.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import zipfile

import smoke

ROOT = Path(__file__).resolve().parents[1]
REPORT = {}


def command(*args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError('Command failed: ' + ' '.join(args[:3]))
    return result.stdout.strip()


def sql(owner, statement):
    return command('docker', 'compose', 'exec', '-T', owner + '-db', 'psql',
                   '-U', 'postgres', '-d', owner + '_db', '-At', '-v', 'ON_ERROR_STOP=1', '-c', statement)


def check(name, condition):
    REPORT['checks'].append({'case': name, 'expected': True, 'actual': bool(condition)})
    if not condition:
        raise AssertionError(name)


class Client(smoke.Client):
    def request(self, method, path, body=None, expected=200, content_type='application/json',
                operation=None, headers=None):
        values = {'Origin': smoke.ORIGIN, 'Content-Type': content_type}
        if self.csrf:
            values['X-CSRF-Token'] = self.csrf
        values.update(headers or {})
        data = None if body is None else body if isinstance(body, bytes) else json.dumps(body).encode()
        request = urllib.request.Request(smoke.BASE + path, data=data, headers=values, method=method)
        try:
            response = self.http.open(request, timeout=40)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            value = json.loads(raw) if raw and 'json' in response.headers.get('Content-Type', '') else raw
            REPORT['checks'].append({'case': operation or method + ' ' + path.split('?')[0],
                'expected': expected, 'actual': response.status,
                'errorCode': value.get('code') if response.status >= 400 and isinstance(value, dict) else None})
            if response.status not in (expected if isinstance(expected, tuple) else (expected,)):
                raise AssertionError(method + ' ' + path.split('?')[0] + ': HTTP ' + str(response.status))
            if operation:
                definition = smoke.OPS[operation][2]
                schema = definition['responses'].get(str(expected), {}).get('content', {}).get('application/json', {}).get('schema')
                if schema:
                    smoke.jsonschema.Draft4Validator({'components': {'schemas': smoke.SCHEMAS}, **smoke.nullable(schema)},
                        format_checker=smoke.jsonschema.FormatChecker()).validate(value)
                REPORT.setdefault('publicOperations', [])
                if operation not in REPORT['publicOperations']:
                    REPORT['publicOperations'].append(operation)
            self.last_headers = dict(response.headers)
            self.last_status = response.status
            return value

    def upload_bytes(self, kind, payload, filename, parent, order=None, expected=201, headers=None):
        boundary = 'khoa-' + uuid.uuid4().hex
        field = 'lessonId' if kind == 'vocabulary' else 'moduleId'
        mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' if kind == 'vocabulary' else 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        chunks = [f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\n\r\n{parent}\r\n'.encode()]
        if order is not None:
            chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="orderNo"\r\n\r\n{order}\r\n'.encode())
        chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
                      + payload + f'\r\n--{boundary}--\r\n'.encode())
        return self.request('POST', '/api/v1/admin/imports/' + kind, b''.join(chunks), expected,
                            'multipart/form-data; boundary=' + boundary, headers=headers)


def admin_client():
    client = Client()
    client.login(os.environ.get('ADMIN_EMAIL', 'admin@example.com'), (ROOT / '.secrets/admin_password').read_text().strip())
    return client


def learner_client(account):
    client = Client()
    client.login(account['email'], account['password'])
    return client


def register():
    account = {'email': 'backlog-' + uuid.uuid4().hex + '@example.com', 'password': uuid.uuid4().hex + '-Aa1'}
    client = Client()
    client.csrf_token()
    user = client.op('register', {**account, 'fullName': 'Khoa backlog TEST'})
    account['userId'] = user['userId']
    client.login(account['email'], account['password'])
    return client, account


def save_state(path, state):
    path.write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')


def ready_lesson(admin, course, lesson):
    assessment = admin.request('POST', '/api/v1/admin/assessments', {'type': 'EXERCISE',
        'courseId': course, 'lessonId': lesson, 'title': 'Backlog readiness', 'timeLimitSeconds': None}, 201)
    aid = assessment['assessmentId']
    question = admin.request('POST', f'/api/v1/admin/assessments/{aid}/questions', {
        'questionType': 'TRUE_FALSE', 'content': 'A real question', 'points': 1, 'correctAnswer': True}, 201)
    admin.request('PATCH', '/api/v1/admin/assessments/' + aid, {'ready': True})
    admin.op('patchLesson', {'published': True}, params={'lessonId': lesson})
    return aid, question['questionId']


def core(state, path):
    admin = admin_client()
    learner, first = register()
    other, second = register()
    credentials = path.with_suffix('.credentials.json')
    credentials.write_text(json.dumps({'learner': first, 'other': second}), encoding='utf-8')
    state['userId'], state['otherUserId'] = first['userId'], second['userId']
    learner.op('getMyProfile')
    learner.op('patchMyProfile', {'fullName': None})
    learner.op('getLearningSetup')
    learner.request('PATCH', '/api/v1/me/profile', {'userId': second['userId'], 'fullName': 'wrong'}, 400)
    anon = Client()
    anon.csrf_token()
    anon.request('POST', '/api/v1/auth/register', {'email': first['email'], 'password': first['password'], 'role': 'ADMIN'}, 400)
    admin.op('listUsers', query='?q=backlog&status=ACTIVE')
    admin.op('getUser', params={'userId': first['userId']})
    admin.op('patchUser', {'fullName': 'Khoa verified'}, params={'userId': first['userId']})
    course_record = admin.op('createCourse', {'name': 'Backlog ' + uuid.uuid4().hex})
    admin.op('patchCourse', {'name': 'Backlog renamed ' + uuid.uuid4().hex}, params={'courseId': course_record['courseId']})
    seed = json.loads((ROOT / 'docs/fixtures/khoa-backlog-course-levels.json').read_text())
    course, level = seed['courses'][0]['courseId'], seed['levels'][0]['levelId']
    state.update(courseId=course, levelId=level, emptyCourseId=course_record['courseId'])
    admin.op('patchCourse', {'published': True}, params={'courseId': course})
    admin.op('adminListCourses')
    admin.op('adminGetCourse', params={'courseId': course})
    module = admin.op('createModule', {'courseId': course, 'title': 'Backlog ' + uuid.uuid4().hex, 'orderNo': int(time.time())})
    mid = state['moduleId'] = module['moduleId']
    admin.op('adminListModules')
    admin.op('adminGetModule', params={'moduleId': mid})
    admin.op('patchModule', {'published': True}, params={'moduleId': mid})
    lesson = admin.op('createLesson', {'moduleId': mid, 'title': 'LIVE lesson', 'content': 'Backlog', 'orderNo': 1})
    lid = state['lessonId'] = lesson['lessonId']
    save_state(path, state)
    admin.op('adminListLessons')
    admin.op('adminGetLesson', params={'lessonId': lid})
    admin.request('PATCH', '/api/v1/admin/lessons/' + lid, {'published': True}, 409)
    check('readiness failure preserves draft', admin.op('adminGetLesson', params={'lessonId': lid})['published'] is False)
    state['assessmentId'], state['questionId'] = ready_lesson(admin, course, lid)
    fixture = ROOT / 'docs/fixtures'
    imported = admin.upload('importVocabulary', 'vocabulary-valid.xlsx', 'lessonId', lid)
    vids = state['vocabularyIds'] = imported['createdIds']
    check('valid XLSX creates exactly fixture rows', imported['createdCount'] == 2)
    for vid in vids:
        admin.op('patchVocabulary', {'published': True}, params={'vocabularyId': vid})
    admin.op('adminListVocabulary')
    admin.op('adminGetVocabulary', params={'vocabularyId': vids[0]})
    duplicate = admin.upload('importVocabulary', 'vocabulary-valid.xlsx', 'lessonId', lid, expected=409)
    check('duplicate import is atomic', duplicate['import']['createdCount'] == 0 and not duplicate['import']['createdIds'])
    invalid = admin.upload('importVocabulary', 'vocabulary-invalid.xlsx', 'lessonId', lid, expected=422)
    check('invalid import is atomic', invalid['import']['createdCount'] == 0)
    corrupt = admin.upload_bytes('vocabulary', b'not an OOXML file', 'corrupt.xlsx', lid, expected=415)
    check('corrupt file rejected before writes', corrupt['import']['createdCount'] == 0)
    for kind, filename, parent in [('vocabulary', 'vocabulary-valid.xlsx', lid), ('lessons', 'lesson-valid.docx', mid)]:
        data = io.BytesIO()
        with zipfile.ZipFile(fixture / filename) as original, zipfile.ZipFile(data, 'w') as changed:
            for entry in original.infolist():
                raw = original.read(entry.filename)
                if entry.filename == '[Content_Types].xml':
                    raw = raw.replace(b'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml', b'application/vnd.ms-excel.sheet.macroEnabled.main+xml')
                    raw = raw.replace(b'application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml', b'application/vnd.ms-word.document.macroEnabled.main+xml')
                changed.writestr(entry, raw)
        result = admin.upload_bytes(kind, data.getvalue(), filename, parent, 9, 415)
        check(kind + ' macro disguise creates zero', result['import']['createdCount'] == 0)
    admin.upload_bytes('vocabulary', b'x' * (5 * 1024 * 1024 + 1), 'large.xlsx', lid, expected=413)
    before_lessons = admin.op('adminListLessons', query='?moduleId=' + mid)['totalElements']
    admin.upload('importLessons', 'lesson-missing-title.docx', 'moduleId', mid, 2, 422)
    word = admin.upload('importLessons', 'lesson-leading-empty.docx', 'moduleId', mid, 2)
    state['draftLessonId'] = word['createdIds'][0]
    admin.upload('importLessons', 'lesson-leading-empty.docx', 'moduleId', mid, 3, 409)
    check('DOCX invalid and duplicate do not partially write',
          admin.op('adminListLessons', query='?moduleId=' + mid)['totalElements'] == before_lessons + 1)
    race_lesson = admin.op('createLesson', {'moduleId': mid, 'title': 'Import race', 'content': 'TEST', 'orderNo': 4})
    state['raceLessonId'] = race_lesson['lessonId']
    admin_cookie = '; '.join(c.name + '=' + c.value for c in admin.jar)
    def import_race(_):
        client = Client()
        value = client.upload_bytes('vocabulary', (fixture / 'vocabulary-valid.xlsx').read_bytes(),
            'vocabulary-valid.xlsx', state['raceLessonId'], expected=(201, 409),
            headers={'Cookie': admin_cookie, 'X-CSRF-Token': admin.csrf})
        return client.last_status, value
    with ThreadPoolExecutor(max_workers=2) as pool:
        race_results = list(pool.map(import_race, range(2)))
    check('concurrent imports have exactly one atomic winner', sorted(x[0] for x in race_results) == [201, 409]
          and sorted((x[1].get('import', x[1]))['createdCount'] for x in race_results) == [0, 2])
    check('concurrent import commits exactly two source rows',
          admin.op('adminListVocabulary', query='?lessonId=' + state['raceLessonId'])['totalElements'] == 2)
    check('templates available', bool(admin.op('getTemplateVocabulary')) and bool(admin.op('getTemplateLessons')))
    for kind in ['GRAMMAR', 'READING']:
        content = admin.op('createLearningContent', {'lessonId': lid, 'type': kind, 'title': kind, 'text': 'Text'})
        admin.op('patchLearningContent', {'published': True}, params={'contentId': content['contentId']})
        admin.op('adminGetLearningContent', params={'contentId': content['contentId']})
    listening = admin.op('createLearningContent', {'lessonId': lid, 'type': 'LISTENING',
        'title': 'Unavailable audio', 'resourceUrl': 'https://example.com/khoa-test-unavailable.mp3'})
    admin.op('patchLearningContent', {'published': True}, params={'contentId': listening['contentId']})
    admin.op('adminListLearningContent')
    learner.op('putLearningSetup', {'courseId': course, 'levelId': level})
    other.op('putLearningSetup', {'courseId': course, 'levelId': level})
    learner.request('PUT', '/api/v1/me/learning-setup', {'courseId': course, 'levelId': str(uuid.uuid4())}, 409)
    learner.op('listCourses')
    learner.op('getCourse', params={'courseId': course})
    learner.op('listLevels', params={'courseId': course})
    learner.op('listModules', params={'courseId': course})
    learner.op('listLessons', params={'moduleId': mid})
    view = learner.op('getLessonLearning', params={'lessonId': lid})
    check('published Lesson uses real Exercise and learning content', view['exerciseId'] == state['assessmentId']
          and {'GRAMMAR', 'READING'} <= {item['type'] for item in view['contents']})
    check('Listening unavailable is explicit', any(item['type'] == 'LISTENING' and item['availability'] == 'UNAVAILABLE' for item in view['contents']))
    learner.request('GET', '/api/v1/lessons/' + state['draftLessonId'], expected=404)
    for vid in vids:
        learner.op('saveVocabulary', params={'vocabularyId': vid})
    learner.op('saveVocabulary', params={'vocabularyId': vids[0]})
    check('saved vocabulary isolated by learner', other.op('listSavedVocabulary')['totalElements'] == 0)
    pages = [learner.op('listSavedVocabulary', query=f'?page={i}&size=1') for i in range(2)]
    check('stable non-overlapping saved pagination', pages[0]['totalElements'] == 2 and
          pages[0]['items'][0]['vocabularyId'] != pages[1]['items'][0]['vocabularyId'])
    cookie_header = '; '.join(c.name + '=' + c.value for c in learner.jar)
    def concurrent_save(_):
        return Client().request('PUT', '/api/v1/me/vocabulary/' + vids[0], expected=200,
                               headers={'Cookie': cookie_header, 'X-CSRF-Token': learner.csrf})
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(concurrent_save, range(8)))
    check('concurrent saves preserve unique collection', learner.op('listSavedVocabulary')['totalElements'] == 2)
    for resource, identity in [('courses', course), ('modules', mid), ('lessons', lid)]:
        admin.request('PATCH', '/api/v1/admin/' + resource + '/' + identity, {'published': False})
        check('hidden ' + resource + ' preserves saved reference', learner.op('listSavedVocabulary')['totalElements'] == 2)
        admin.request('PATCH', '/api/v1/admin/' + resource + '/' + identity, {'published': True})
    admin.op('patchVocabulary', {'meaning': 'Updated source LIVE'}, params={'vocabularyId': vids[0]})
    saved = learner.op('listSavedVocabulary')
    check('collection reads updated source', any(x['content']['meaning'] == 'Updated source LIVE' for x in saved['items']))
    second_course, second_level = seed['courses'][1]['courseId'], seed['levels'][-1]['levelId']
    second_level = next(x['levelId'] for x in seed['levels'] if x['courseId'] == second_course)
    admin.op('patchCourse', {'published': True}, params={'courseId': second_course})
    learner.op('putLearningSetup', {'courseId': second_course, 'levelId': second_level})
    check('Course change preserves saved collection', learner.op('listSavedVocabulary')['totalElements'] == 2)
    learner.op('putLearningSetup', {'courseId': course, 'levelId': level})
    learner.op('removeVocabulary', params={'vocabularyId': vids[1]})
    learner.op('removeVocabulary', params={'vocabularyId': vids[1]})
    check('remove never deletes source', admin.op('adminGetVocabulary', params={'vocabularyId': vids[1]})['vocabularyId'] == vids[1])
    for resource, identity in [('courses', course), ('modules', mid), ('lessons', lid)]:
        admin.request('DELETE', '/api/v1/admin/' + resource + '/' + identity, expected=405)
    learner.request('GET', '/api/v1/admin/users', expected=403)
    learner.upload_bytes('vocabulary', (fixture / 'vocabulary-valid.xlsx').read_bytes(), 'vocabulary-valid.xlsx', lid, expected=403)
    anon.request('GET', '/api/v1/me/profile', expected=401)
    learner.request('PATCH', '/api/v1/me/profile', {'fullName': 'Rejected'}, 403, headers={'X-CSRF-Token': 'wrong'})
    anon.request('OPTIONS', '/api/v1/me/vocabulary/' + vids[0], expected=204,
                 headers={'Access-Control-Request-Method': 'PUT', 'Access-Control-Request-Headers': 'X-CSRF-Token,Content-Type'})
    check('credentialed CORS preflight', anon.last_headers.get('Access-Control-Allow-Origin') == smoke.ORIGIN
          and anon.last_headers.get('Access-Control-Allow-Credentials') == 'true')
    anon.request('OPTIONS', '/api/v1/me/vocabulary/' + vids[0], expected=403,
                 headers={'Origin': 'http://foreign.example', 'Access-Control-Request-Method': 'PUT'})
    old_cookie = '; '.join(c.name + '=' + c.value for c in other.jar)
    other.op('logout')
    anon.request('GET', '/api/v1/me/profile', expected=401, headers={'Cookie': old_cookie})
    admin.op('patchUser', {'status': 'BLOCKED'}, params={'userId': second['userId']})
    try:
        other.csrf_token()
        other.request('POST', '/api/v1/auth/login', {'email': second['email'], 'password': second['password']}, 401)
    finally:
        admin.op('patchUser', {'status': 'ACTIVE'}, params={'userId': second['userId']})
    check('every User/Content public operation covered', set(REPORT['publicOperations']) == set(smoke.OPS))
    save_state(path, state)


def probe(caller, owner, path, expected=200, tamper=False):
    cp = os.pathsep.join([str(ROOT / '.tools/backlog/probe-classes'),
        str(ROOT / 'backend/services/user-service/target/classes'),
        (ROOT / '.tools/backlog/user-classpath.txt').read_text().strip()])
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java.exe')
    result = subprocess.run([java, '-cp', cp, 'OwnerSourceProbe', str(ROOT)],
        input=json.dumps({'caller': caller, 'owner': owner, 'path': path, 'tamper': tamper}),
        text=True, capture_output=True, cwd=ROOT, timeout=40)
    if result.returncode:
        raise AssertionError('Owner probe failed for ' + caller + '/' + owner)
    response = json.loads(result.stdout)
    check('owner ' + caller + ' -> ' + owner + ' ' + path.split('?')[0] + ' HTTP ' + str(expected), response['status'] == expected)
    return response['body']


def owners(state, path):
    admin = admin_client()
    accounts = json.loads(path.with_suffix('.credentials.json').read_text())
    learner = learner_client(accounts['learner'])
    course, lid, user = state['courseId'], state['lessonId'], state['userId']
    setup = probe('progress', 'user', '/internal/v1/users/' + user + '/learning-setup')
    check('Progress reads real User setup', setup['courseId'] == course and setup['levelId'] == state['levelId'])
    ctx = probe('progress', 'content', '/internal/v1/courses/' + course + '/context')
    check('denominator includes published and draft Lessons', ctx['totalLessons'] >= 2)
    pages = [probe('progress', 'content', '/internal/v1/courses/contexts?page=' + str(p) + '&size=1') for p in range(2)]
    check('context pagination stable', pages[0]['items'][0]['courseId'] != pages[1]['items'][0]['courseId'])
    cohort = probe('progress', 'user', '/internal/v1/learners/cohort?courseId=' + course)
    global_count = cohort['totalLearnersGlobal']
    admin.op('patchUser', {'status': 'BLOCKED'}, params={'userId': state['otherUserId']})
    try:
        blocked = probe('progress', 'user', '/internal/v1/learners/cohort?courseId=' + course)
        check('cohort includes BLOCKED Learner', state['otherUserId'] in [x['userId'] for x in blocked['items']])
    finally:
        admin.op('patchUser', {'status': 'ACTIVE'}, params={'userId': state['otherUserId']})
    empty = probe('progress', 'user', '/internal/v1/learners/cohort?courseId=' + str(uuid.uuid4()))
    check('global Learner count independent of filter', empty['totalElements'] == 0 and empty['totalLearnersGlobal'] == global_count)
    cohort_pages = [probe('progress', 'user', '/internal/v1/learners/cohort?page=' + str(p) + '&size=1') for p in range(2)]
    check('cohort paging stable', cohort_pages[0]['items'][0]['userId'] != cohort_pages[1]['items'][0]['userId'])
    probe('progress', 'user', '/internal/v1/learners/cohort?courseId=bad', 400)
    probe('progress', 'content', '/internal/v1/courses/not-a-uuid/context', 400)
    probe('progress', 'content', '/internal/v1/courses/' + str(uuid.uuid4()) + '/context', 404)
    probe('api', 'user', '/internal/v1/learners/cohort', 403)
    probe('api', 'content', '/internal/v1/courses/contexts', 403)
    probe('progress', 'content', '/internal/v1/lessons/' + lid + '/context', 403)
    lesson = probe('assessment', 'content', '/internal/v1/lessons/' + lid + '/context')
    check('Assessment reads correct Lesson owner context', lesson['courseId'] == course)
    probe('progress', 'content', '/internal/v1/courses/' + course + '/context', 401, True)
    progress = learner.request('GET', '/api/v1/me/progress?courseId=' + course)
    check('real Progress denominator from Content', progress['totalLessons'] == ctx['totalLessons'] and progress['completedLessons'] == 0)
    dashboard = learner.request('GET', '/api/v1/me/dashboard')
    check('real Dashboard reads User setup', dashboard['setup']['courseId'] == course)
    empty_progress = learner.request('GET', '/api/v1/me/progress?courseId=' + state['emptyCourseId'])
    check('empty Course progress is null', empty_progress['progressPercent'] is None and empty_progress['totalLessons'] == 0)
    history = learner.request('GET', '/api/v1/me/history')
    check('no fabricated event history', history['totalElements'] == 0)
    REPORT['notCovered'] = ['non-empty event projection/Kafka E2E', 'Statistics consumer not implemented by owner', 'Huy peer acceptance']


def wait_apps():
    for _ in range(60):
        try:
            with urllib.request.urlopen(smoke.BASE + '/actuator/health', timeout=3) as r:
                if json.load(r)['status'] == 'UP':
                    return
        except Exception:
            pass
        time.sleep(1)
    raise AssertionError('Gateway did not recover')


def faults(state, path):
    accounts = json.loads(path.with_suffix('.credentials.json').read_text())
    learner = learner_client(accounts['learner'])
    admin = admin_client()
    project = os.environ['COMPOSE_PROJECT_NAME']
    before = sql('content', 'SELECT count(*) FROM course')
    for owner in ['content', 'user']:
        container = command('docker', 'compose', 'ps', '-q', owner + '-db')
        network = project + '_' + owner + '-data'
        check('fault target belongs to TEST project', command('docker', 'inspect', '--format',
              '{{index .Config.Labels "com.docker.compose.project"}}', container) == project)
        try:
            command('docker', 'network', 'disconnect', network, container)
            if owner == 'content':
                result = learner.request('GET', '/api/v1/me/progress?courseId=' + state['courseId'], expected=503)
                check('Progress source outage is not zero', result['code'] == 'DEPENDENCY_UNAVAILABLE' and 'progressPercent' not in result)
                admin.request('POST', '/api/v1/admin/courses', {'name': 'Must not persist ' + uuid.uuid4().hex}, 503)
            else:
                learner.request('GET', '/api/v1/me/dashboard', expected=503)
        finally:
            command('docker', 'network', 'connect', '--alias', owner + '-db', network, container)
        for attempt in range(25):
            try:
                if owner == 'content':
                    with urllib.request.urlopen('http://127.0.0.1:18082/actuator/health', timeout=3) as r:
                        if json.load(r)['status'] == 'UP':
                            break
                else:
                    with urllib.request.urlopen('http://127.0.0.1:18081/actuator/health', timeout=3) as r:
                        if json.load(r)['status'] == 'UP':
                            break
            except Exception:
                pass
            time.sleep(1)
        learner.request('GET', '/api/v1/me/dashboard')
    check('failed writes did not persist', sql('content', 'SELECT count(*) FROM course') == before)
    # A local table lock holds all ten application connections; the eleventh request
    # must fail within the configured acquisition timeout and recover after unlock.
    lock = subprocess.Popen(['docker', 'compose', 'exec', '-T', 'content-db', 'psql', '-U', 'postgres',
        '-d', 'content_db', '-v', 'ON_ERROR_STOP=1', '-c',
        'BEGIN; LOCK TABLE course IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(22); ROLLBACK;'],
        cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(50):
            if int(sql('content', "SELECT count(*) FROM pg_locks WHERE relation='course'::regclass AND mode='AccessExclusiveLock' AND granted")) > 0:
                break
            time.sleep(.1)
        cookie = '; '.join(c.name + '=' + c.value for c in admin.jar)
        with ThreadPoolExecutor(max_workers=10) as pool:
            pending = [pool.submit(Client().request, 'GET', '/api/v1/admin/courses',
                                   headers={'Cookie': cookie}) for _ in range(10)]
            for _ in range(60):
                blocked = int(sql('content', "SELECT count(*) FROM pg_stat_activity WHERE usename='content_runtime' AND wait_event_type='Lock'"))
                if blocked >= 10:
                    break
                time.sleep(.1)
            check('ten runtime connections occupied by test lock', blocked >= 10)
            result = admin.request('POST', '/api/v1/admin/courses', {'name': 'Pool rejected ' + uuid.uuid4().hex}, 503)
            check('pool exhaustion returns sanitized dependency error', result['code'] == 'DEPENDENCY_UNAVAILABLE')
            for future in pending:
                future.result(timeout=40)
    finally:
        lock.wait(timeout=35)
    check('pool failure writes nothing', sql('content', 'SELECT count(*) FROM course') == before)
    admin.request('POST', '/api/v1/admin/courses', {'name': 'Pool recovery ' + uuid.uuid4().hex}, 201)
    learner.request('GET', '/api/v1/me/progress?courseId=' + state['courseId'])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--phase', choices=['core', 'owners', 'faults'], required=True)
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if not os.environ.get('COMPOSE_PROJECT_NAME', '').startswith('khoa-backlog-') or os.environ.get('ALLOW_TEST_FAULT_INJECTION') != '1':
        raise SystemExit('Select the dedicated TEST project and ALLOW_TEST_FAULT_INJECTION=1.')
    if args.report.exists():
        raise SystemExit('Use a fresh report path; history is retained.')
    state = json.loads(args.state.read_text()) if args.state.exists() else {}
    REPORT.update(status='RUNNING', phase=args.phase, environment='TEST', runtime='DOCKER_REAL_SERVICES_POSTGRESQL',
        startedAt=datetime.now(timezone.utc).isoformat(), backendSha=command('git', 'rev-parse', 'HEAD'),
        ownerSha=command('git', '-C', os.environ['HUY_SOURCE_ROOT'], 'rev-parse', 'HEAD'),
        composeProject=os.environ['COMPOSE_PROJECT_NAME'], checks=[], images={})
    try:
        for owner in ['user', 'content', 'api', 'assessment-live', 'progress-live']:
            container = command('docker', 'compose', 'ps', '-q', owner)
            REPORT['images'][owner] = command('docker', 'inspect', '--format', '{{.Image}}', container)
        globals()[args.phase](state, args.state)
        REPORT['status'] = 'PASS_REVIEW_REQUIRED'
    except Exception as error:
        REPORT['status'] = 'FAIL'
        REPORT['failure'] = type(error).__name__
        print('Acceptance FAIL; inspect report checks:', type(error).__name__)
        raise
    finally:
        REPORT['fixtures'] = state
        REPORT['finishedAt'] = datetime.now(timezone.utc).isoformat()
        args.report.parent.mkdir(parents=True, exist_ok=True)
        with args.report.open('x', encoding='utf-8') as stream:
            json.dump(REPORT, stream, indent=2, ensure_ascii=False)
            stream.write('\n')
    print(args.phase + ': PASS_REVIEW_REQUIRED (' + str(len(REPORT['checks'])) + ' checks)')


if __name__ == '__main__':
    main()
