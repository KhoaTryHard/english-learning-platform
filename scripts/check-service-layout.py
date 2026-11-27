"""Prove services build from an isolated folder and contain only their owner classes."""
from pathlib import Path
import argparse,subprocess,tempfile,shutil,os,json,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SERVICES={'backend/services/user-service':'user','backend/services/learning-content-service':'content','backend/api-gateway':'api'}
NS={'m':'http://maven.apache.org/POM/4.0.0'}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--build',action='store_true');parser.add_argument('--report',type=Path);args=parser.parse_args()
    results=[]
    for folder,owner in SERVICES.items():
        app=ROOT/folder;pom=ET.parse(app/'pom.xml').getroot()
        assert pom.findtext('m:parent/m:groupId',namespaces=NS)=='org.springframework.boot'
        assert pom.findtext('m:parent/m:relativePath',namespaces=NS) in ('',None)
        assert not any(d.findtext('m:groupId',namespaces=NS)=='vn.group7' for d in pom.findall('m:dependencies/m:dependency',NS))
        for name in ('Dockerfile','compose.yaml','README.md','mvnw','mvnw.cmd','.mvn/wrapper/maven-wrapper.properties'):assert (app/name).is_file(),folder+'/'+name
        docker=(app/'Dockerfile').read_text();assert 'COPY ../' not in docker and 'COPY backend' not in docker
        result={'service':folder,'parent':'Spring Boot','siblingMavenDependency':False,'dockerContext':folder}
        if args.build:
            isolation=ROOT/'.tools/isolated-builds'/folder
            assert isolation.resolve().is_relative_to(ROOT/'.tools')
            # Only task-owned build copies are refreshed; real sources/secrets are excluded.
            if isolation.exists():shutil.rmtree(isolation)
            shutil.copytree(app,isolation,ignore=shutil.ignore_patterns('target','.secrets','.git','.env','*.log'))
            wrapper=str(isolation/('mvnw.cmd' if os.name=='nt' else 'mvnw'))
            command=[wrapper,'-B','-DskipTests','package'] if os.name=='nt' else ['bash',wrapper,'-B','-DskipTests','package']
            subprocess.run(command,cwd=isolation,check=True)
            jar=next((isolation/'target').glob('*.jar'))
            with zipfile.ZipFile(jar) as archive:
                names=archive.namelist()
                assert any(n.startswith('BOOT-INF/classes/vn/group7/'+owner+'/') for n in names)
                for other in set(SERVICES.values())-{owner}:assert not any(n.startswith('BOOT-INF/classes/vn/group7/'+other+'/') for n in names)
                assert not any('/platform-core-' in n for n in names)
            result['isolatedBuild']='PASS';result['ownerOnlyExecutableJar']='PASS'
        results.append(result)
    if args.report:
        report_path=ROOT/args.report
        report_path.parent.mkdir(parents=True,exist_ok=True)
        report_path.write_bytes(json.dumps({'status':'PASS','isolatedBuildRequested':args.build,'services':results},indent=2).encode())
    print('Standalone service folders verified'+(' with isolated Maven builds' if args.build else ''))
if __name__=='__main__':main()
