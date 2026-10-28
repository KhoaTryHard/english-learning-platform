"""Exercise every owner public operation through Gateway; reports never contain secrets."""
from pathlib import Path
import urllib.request,urllib.error,http.cookiejar,json,uuid,os,re,time,subprocess
import yaml,jsonschema

ROOT=Path(__file__).resolve().parents[1]
BASE=os.environ.get('GATEWAY_URL','http://localhost:8080')
ORIGIN=os.environ.get('FRONTEND_ORIGIN','http://localhost:5173')
SPEC=yaml.safe_load((ROOT/'docs/contracts/public-v0.1.yaml').read_text(encoding='utf-8'))
OPS={}
for path,methods in SPEC['paths'].items():
    for method,op in methods.items():
        if isinstance(op,dict) and op.get('x-owner-service') in ('User Service','Learning Content Service'):
            OPS[op['operationId']]=(method.upper(),'/api/v1'+path,op)

def nullable(node):
    if isinstance(node,list):return [nullable(x) for x in node]
    if not isinstance(node,dict):return node
    value={k:nullable(v) for k,v in node.items() if k!='nullable'}
    if node.get('nullable'):return {'anyOf':[value,{'type':'null'}]}
    return value

SCHEMAS=nullable(SPEC['components']['schemas'])
COVERED=set();CHECKS=[]
class Client:
    def __init__(self):
        self.jar=http.cookiejar.CookieJar();self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar));self.csrf=None
    def request(self,method,path,body=None,expected=200,content_type='application/json',operation=None):
        headers={'Origin':ORIGIN,'Content-Type':content_type}
        if self.csrf:headers['X-CSRF-Token']=self.csrf
        data=None if body is None else body if isinstance(body,bytes) else json.dumps(body,ensure_ascii=False).encode()
        try:r=self.http.open(urllib.request.Request(BASE+path,data=data,headers=headers,method=method),timeout=35)
        except urllib.error.HTTPError as e:r=e
        raw=r.read();status=r.status
        if status!=expected:
            # Error bodies are sanitized by backend; never include cookies/request data.
            raise AssertionError(f'{method} {path}: expected {expected}, got {status}: {raw[:600]!r}')
        value=json.loads(raw) if raw and 'json' in r.headers.get('Content-Type','') else raw
        if operation:
            _,_,op=OPS[operation];response=op['responses'].get(str(status),{});schema=response.get('content',{}).get('application/json',{}).get('schema')
            if schema:
                document={'components':{'schemas':SCHEMAS},**nullable(schema)}
                jsonschema.Draft4Validator(document,format_checker=jsonschema.FormatChecker()).validate(value)
            COVERED.add(operation)
        CHECKS.append({'method':method,'path':path.split('?')[0],'status':status,'operation':operation})
        return value
    def op(self,name,body=None,params=None,expected=None,query=''):
        method,path,definition=OPS[name]
        for key,value in (params or {}).items():path=path.replace('{'+key+'}',str(value))
        if expected is None:expected=201 if method=='POST' and name not in ('login','logout') else 204 if name in ('logout','removeVocabulary') else 200
        return self.request(method,path+query,body,expected,operation=name)
    def csrf_token(self):self.csrf=self.op('getCsrf')['csrfToken']
    def login(self,email,password):
        self.csrf_token();self.op('login',{'email':email,'password':password});self.csrf_token()
    def upload(self,name,file,parent_key,parent,order=None,expected=201):
        method,path,_=OPS[name];boundary='group7-'+uuid.uuid4().hex
        mime='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' if file.endswith('.xlsx') else 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        segments=[f'--{boundary}\r\nContent-Disposition: form-data; name="{parent_key}"\r\n\r\n{parent}\r\n'.encode()]
        if order is not None:segments.append(f'--{boundary}\r\nContent-Disposition: form-data; name="orderNo"\r\n\r\n{order}\r\n'.encode())
        segments.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file}"\r\nContent-Type: {mime}\r\n\r\n'.encode()+(ROOT/'docs/fixtures'/file).read_bytes()+f'\r\n--{boundary}--\r\n'.encode())
        return self.request(method,path,b''.join(segments),expected,'multipart/form-data; boundary='+boundary,name if expected==201 else None)

def main():
    learner=Client();learner.csrf_token();email='smoke-'+uuid.uuid4().hex+'@example.com';password='learner-password-12345'
    user=learner.op('register',{'email':email,'password':password,'fullName':'Khoa'})
    learner.login(email,password)
    learner.op('getMyProfile');learner.op('patchMyProfile',{'fullName':None});learner.op('getLearningSetup')
    admin=Client();admin.login(os.environ.get('ADMIN_EMAIL','admin@example.com'),(ROOT/'.secrets/admin_password').read_text().strip())
    admin.op('listUsers',query='?q=smoke&status=ACTIVE');admin.op('getUser',params={'userId':user['userId']});admin.op('patchUser',{'fullName':'Khoa Test'},params={'userId':user['userId']})
    created=admin.op('createCourse',{'name':'Smoke '+uuid.uuid4().hex});admin.op('adminListCourses');admin.op('adminGetCourse',params={'courseId':created['courseId']});admin.op('patchCourse',{'name':'Renamed '+uuid.uuid4().hex},params={'courseId':created['courseId']})
    seed=json.loads((ROOT/'backend/services/learning-content-service/deploy/course-levels.json').read_text());course=seed['courses'][0]['courseId'];level=seed['levels'][0]['levelId'];admin.op('patchCourse',{'published':True},params={'courseId':course})
    module=admin.op('createModule',{'courseId':course,'title':'Smoke '+uuid.uuid4().hex,'orderNo':int(time.time())});mid=module['moduleId']
    admin.op('adminListModules');admin.op('adminGetModule',params={'moduleId':mid});admin.op('patchModule',{'published':True},params={'moduleId':mid})
    lesson=admin.op('createLesson',{'moduleId':mid,'title':'Lesson','content':'Learn greetings','orderNo':1});lid=lesson['lessonId']
    admin.op('adminListLessons');admin.op('adminGetLesson',params={'lessonId':lid});admin.op('patchLesson',{'published':True},params={'lessonId':lid})
    vocab=admin.upload('importVocabulary','vocabulary-valid.xlsx','lessonId',lid);vid=vocab['createdIds'][0]
    admin.op('adminListVocabulary');admin.op('adminGetVocabulary',params={'vocabularyId':vid});admin.op('patchVocabulary',{'published':True},params={'vocabularyId':vid})
    content=admin.op('createLearningContent',{'lessonId':lid,'type':'GRAMMAR','title':'Greetings','text':'Hello and goodbye'});cid=content['contentId']
    admin.op('adminListLearningContent');admin.op('adminGetLearningContent',params={'contentId':cid});admin.op('patchLearningContent',{'published':True},params={'contentId':cid})
    admin.upload('importLessons','lesson-leading-empty.docx','moduleId',mid,2);admin.upload('importLessons','lesson-missing-title.docx','moduleId',mid,3,422)
    assert admin.op('getTemplateVocabulary');assert admin.op('getTemplateLessons')
    learner.op('putLearningSetup',{'courseId':course,'levelId':level});learner.op('listCourses');learner.op('getCourse',params={'courseId':course});learner.op('listLevels',params={'courseId':course});learner.op('listModules',params={'courseId':course});learner.op('listLessons',params={'moduleId':mid});learner.op('getLessonLearning',params={'lessonId':lid})
    learner.op('saveVocabulary',params={'vocabularyId':vid});learner.op('saveVocabulary',params={'vocabularyId':vid});assert len(learner.op('listSavedVocabulary')['items'])==1
    admin.op('patchCourse',{'published':False},params={'courseId':course});learner.op('saveVocabulary',params={'vocabularyId':vid});assert learner.op('listSavedVocabulary')['items'][0]['availability']=='AVAILABLE'
    admin.op('patchVocabulary',{'meaning':'updated source'},params={'vocabularyId':vid});assert learner.op('listSavedVocabulary')['items'][0]['content']['meaning']=='updated source'
    learner.op('removeVocabulary',params={'vocabularyId':vid});learner.op('removeVocabulary',params={'vocabularyId':vid})
    learner.request('GET','/internal/v1/courses/contexts',expected=404);learner.request('DELETE','/api/v1/admin/courses/'+course,expected=405)
    learner.request('GET','/api/v1/admin/users',expected=403);old=learner.csrf;learner.csrf='wrong';learner.request('PATCH','/api/v1/me/profile',{'fullName':'bad'},403);learner.csrf=old
    learner.op('logout');learner.request('GET','/api/v1/me/profile',expected=401)
    missing=set(OPS)-COVERED
    assert not missing,f'Unverified operations: {missing}'
    report={'status':'PASS','publicOperations':len(COVERED),'requests':len(CHECKS),'assessment':'MOCK_ONLY','checks':CHECKS}
    (ROOT/'docs/verification-smoke.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(f'Gateway smoke PASS: {len(COVERED)} public operations; {len(CHECKS)} requests; Assessment mock only')

if __name__=='__main__':main()
