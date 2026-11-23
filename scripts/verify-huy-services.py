"""Prove Huy's Assessment and Progress services stay standalone and owner-only.

Mirrors the rules scripts/check-service-layout.py applies to the User, Content and Gateway
folders, for the two services Huy owns. Kept separate so neither owner edits the other's guard.

    python scripts/verify-huy-services.py
    python scripts/verify-huy-services.py --build --report docs/evidence/huy-service-layout.json

Offline runs reuse an already downloaded Maven and local repository instead of the wrapper:

    python scripts/verify-huy-services.py --build --maven <path-to-mvn> --repo <path-to-m2-repo>
"""
from pathlib import Path
import argparse, json, os, shutil, subprocess, tempfile, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SERVICES = {
    'backend/services/assessment-service': 'assessment',
    'backend/services/progress-service': 'progress',
}
# Owner packages that must never be bundled into another owner's executable jar.
FOREIGN = {'user', 'content', 'api', 'assessment', 'progress'}
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
REQUIRED_FILES = (
    'Dockerfile',
    'compose.yaml',
    'README.md',
    'mvnw',
    'mvnw.cmd',
    '.mvn/wrapper/maven-wrapper.properties',
    '.env.example',
    'pom.xml',
)


def check_layout(folder, owner):
    app = ROOT / folder
    pom = ET.parse(app / 'pom.xml').getroot()
    assert pom.findtext('m:parent/m:groupId', namespaces=NS) == 'org.springframework.boot', folder
    assert pom.findtext('m:parent/m:relativePath', namespaces=NS) in ('', None), folder
    # A sibling or shared vn.group7 artifact would break the standalone build contract.
    assert not any(
        d.findtext('m:groupId', namespaces=NS) == 'vn.group7'
        for d in pom.findall('m:dependencies/m:dependency', NS)
    ), folder
    for name in REQUIRED_FILES:
        assert (app / name).is_file(), f'{folder}/{name}'
    docker = (app / 'Dockerfile').read_text(encoding='utf-8')
    assert 'COPY ../' not in docker and 'COPY backend' not in docker, folder
    migrations = app / 'src/main/resources/db/migration'
    assert migrations.is_dir(), folder
    assert any(p.suffix == '.sql' for p in migrations.iterdir()), folder
    assert (app / f'src/main/java/vn/group7/{owner}').is_dir(), folder
    return {
        'service': folder,
        'owner': owner,
        'parent': 'Spring Boot',
        'siblingMavenDependency': False,
        'dockerContext': folder,
        'requiredFiles': 'PASS',
        'ownedMigrations': 'PASS',
    }


def isolated_build(folder, owner, result, maven, repo):
    app = ROOT / folder
    with tempfile.TemporaryDirectory(prefix=f'huy-{owner}-') as workspace:
        isolation = Path(workspace) / Path(folder).name
        # Secrets, logs and previous build output never enter the isolated copy.
        shutil.copytree(
            app,
            isolation,
            ignore=shutil.ignore_patterns('target', '.secrets', '.git', '.env', '*.log'),
        )
        if maven:
            command = [maven, '-B', '-o', f'-Dmaven.repo.local={repo}', '-DskipTests', 'package']
        elif os.name == 'nt':
            command = [str(isolation / 'mvnw.cmd'), '-B', '-DskipTests', 'package']
        else:
            command = ['bash', str(isolation / 'mvnw'), '-B', '-DskipTests', 'package']
        subprocess.run(command, cwd=isolation, check=True)
        jar = next((isolation / 'target').glob(f'{Path(folder).name}-*.jar'))
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
            assert any(n.startswith(f'BOOT-INF/classes/vn/group7/{owner}/') for n in names), owner
            for other in FOREIGN - {owner}:
                assert not any(
                    n.startswith(f'BOOT-INF/classes/vn/group7/{other}/') for n in names
                ), f'{owner} jar contains {other}'
            assert not any('/platform-core-' in n for n in names), owner
        result['isolatedBuild'] = 'PASS'
        result['ownerOnlyExecutableJar'] = 'PASS'
        result['artifact'] = jar.name
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--maven', help='mvn executable for offline runs')
    parser.add_argument('--repo', help='local Maven repository for offline runs')
    args = parser.parse_args()
    if args.maven and not args.repo:
        parser.error('--maven requires --repo')

    results = []
    for folder, owner in SERVICES.items():
        result = check_layout(folder, owner)
        if args.build:
            isolated_build(folder, owner, result, args.maven, args.repo)
        results.append(result)

    if args.report:
        report = ROOT / args.report
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_bytes(
            json.dumps(
                {
                    'status': 'PASS',
                    'owner': 'Nguyen Quang Huy',
                    'isolatedBuildRequested': args.build,
                    'services': results,
                },
                indent=2,
            ).encode()
        )
    print(
        'Huy service folders verified'
        + (' with isolated Maven builds' if args.build else ' (layout only)')
    )


if __name__ == '__main__':
    main()
