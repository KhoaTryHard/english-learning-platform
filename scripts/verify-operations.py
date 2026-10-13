"""Isolated backup/restore and restart checks; never restore over a live database."""
from pathlib import Path
import subprocess,json,uuid,time,urllib.request

ROOT=Path(__file__).resolve().parents[1]
def command(*args):
    return subprocess.check_output(args,cwd=ROOT,text=True,stderr=subprocess.STDOUT).strip()
def sql(service,database,statement):
    return command('docker','compose','exec','-T',service+'-db','psql','-U','postgres','-d',database,'-At','-v','ON_ERROR_STOP=1','-c',statement)
reports=[]
for service,table in [('user','app_user'),('content','course')]:
    source=service+'_db';target=service+'_restore_'+uuid.uuid4().hex[:12]
    before=sql(service,source,'SELECT count(*) FROM '+table)
    sql(service,source,'CREATE DATABASE '+target)
    try:
        command('docker','compose','exec','-T',service+'-db','pg_dump','-U','postgres','-d',source,'-Fc','--no-owner','--no-privileges','-f','/tmp/group7-roundtrip.dump')
        command('docker','compose','exec','-T',service+'-db','pg_restore','-U','postgres','-d',target,'--no-owner','--no-privileges','/tmp/group7-roundtrip.dump')
        after=sql(service,target,'SELECT count(*) FROM '+table)
        assert before==after
        constraints=sql(service,target,"SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND contype IN ('f','u','c')")
        assert int(constraints)>0
        assert sql(service,source,'SELECT count(*) FROM flyway_schema_history')==sql(service,target,'SELECT count(*) FROM flyway_schema_history')
        reports.append({'database':source,'backupRestore':'PASS','rows':int(after),'constraints':int(constraints)})
    finally:
        sql(service,source,'DROP DATABASE '+target)

tracked=[('user','app_user'),('user','learning_setup'),('user','revoked_token'),('user','spring_session'),('content','course'),('content','user_vocabulary')]
counts={(s,t):sql(s,s+'_db','SELECT count(*) FROM '+t) for s,t in tracked}
command('docker','compose','restart','user','content','api')
for attempt in range(60):
    try:
        with urllib.request.urlopen('http://localhost:8080/actuator/health',timeout=3) as r:
            if json.load(r)['status']=='UP':break
    except Exception:pass
    time.sleep(2)
else:raise AssertionError('Gateway failed health after restart')
for s,t in tracked:assert counts[(s,t)]==sql(s,s+'_db','SELECT count(*) FROM '+t)
assert command('docker','compose','exec','-T','api','sh','-c','test ! -e /run/secrets/user_runtime && test ! -e /run/secrets/content_runtime && echo isolated')=='isolated'
report={'status':'PASS','restartPersistence':'PASS','gatewayNoDatabaseCredentials':'PASS','databases':reports}
(ROOT/'docs/verification-operations.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('Isolated backup/restore and restart persistence: PASS')
