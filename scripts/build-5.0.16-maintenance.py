"""Build an unsigned 5.0.16 while preserving the complete 5.0.15 release."""
import argparse, glob, hashlib, json, pathlib, re, subprocess, zipfile

BASE_SHA = '7039d0b41cd4b5bc5245bf25dc6011d4793365d9a61ced075ed31d629ef30d01'
VERSION_NAME = '5.0.16'
VERSION_CODE = 75
BUILD_DIR = 'maintenance516'
ROOTS = ('JourneyActivity', 'JourneyPhoneEngine', 'JourneyState')
ROOT = pathlib.Path(__file__).resolve().parents[1]


def run(*args, capture=False):
    return subprocess.run([str(x) for x in args], cwd=ROOT, check=True,
                          stdout=subprocess.PIPE if capture else None, text=True).stdout


def selected(path):
    return any(path == 'org/openenglish/reader/' + r + '.smali' or
               path.startswith('org/openenglish/reader/' + r + '$') for r in ROOTS)


def main():
    p = argparse.ArgumentParser(__doc__)
    for flag in ('base', 'output', 'tools', 'android-jar', 'onnx-jar', 'dex-classpath'):
        p.add_argument('--' + flag, required=True)
    p.add_argument('--build-dir', default=str(ROOT / 'build' / BUILD_DIR))
    a = p.parse_args()
    a.dex_classpath = ':'.join(str(pathlib.Path(p).resolve()) for pattern in a.dex_classpath.split(':')
                              for p in (sorted(glob.glob(pattern)) if '*' in pattern else [pattern])
                              if pathlib.Path(p).is_file() and p.endswith('.jar'))
    if not a.dex_classpath:
        raise ValueError('DEX dependency classpath is empty')
    base, output, tools = map(lambda x: pathlib.Path(x).resolve(), (a.base, a.output, a.tools))
    if hashlib.sha256(base.read_bytes()).hexdigest() != BASE_SHA:
        raise ValueError('Unexpected base APK; no build files written')
    work = pathlib.Path(a.build_dir).resolve()
    if work.exists():
        raise ValueError('Build directory exists; choose a new --build-dir')
    for name in ('classes', 'dex', 'old-smali', 'new-smali'):
        (work / name).mkdir(parents=True, exist_ok=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    javac = ['java', '--add-modules', 'jdk.compiler', 'com.sun.tools.javac.Main']
    sources = sorted((ROOT / 'scripts/journey-stubs/org/openenglish/reader').glob('*.java'))
    sources += sorted((ROOT / 'app/src/main/java/org/openenglish/reader').glob('Journey*.java'))
    run(*javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp',
        a.android_jar + ':' + a.onnx_jar, '-d', work / 'classes', *sources)
    classes = sorted((work / 'classes/org/openenglish/reader').glob('Journey*.class'))
    run(tools / 'd8', '--min-api', '23', '--lib', a.android_jar, '--classpath',
        work / 'classes', '--classpath', a.onnx_jar, '--output', work / 'dex', *classes)
    with zipfile.ZipFile(base) as z:
        (work / 'base.dex').write_bytes(z.read('classes.dex'))
    run(*javac, '-cp', a.dex_classpath, '-d', work / 'classes', ROOT / 'scripts/DexMaintenanceMerge.java')
    run('java', '-cp', str(work / 'classes') + ':' + a.dex_classpath, 'DexMaintenanceMerge',
        work / 'base.dex', work / 'dex/classes.dex', work / 'merged.dex')
    old_resources = run(tools / 'aapt2', 'dump', 'resources', base, capture=True)
    pattern = r'resource (0x[0-9a-f]+) (?:org.openenglish.reader:)?drawable/icon'
    icon = re.search(pattern, old_resources).group(1)
    (work / 'stable.ids').write_text('org.openenglish.reader:drawable/icon = ' + icon + '\n')
    run(tools / 'aapt2', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', work / 'res.zip')
    run(tools / 'aapt2', 'link', '-o', work / 'shell.apk', '-I', a.android_jar, '--manifest',
        ROOT / 'app/src/main/AndroidManifest.xml', '--min-sdk-version', '23',
        '--target-sdk-version', '33', '--stable-ids', work / 'stable.ids', work / 'res.zip')
    new_resources = run(tools / 'aapt2', 'dump', 'resources', work / 'shell.apk', capture=True)
    assert re.search(pattern, new_resources).group(1) == icon, 'Icon ID changed'
    old_manifest = run(tools / 'aapt2', 'dump', 'xmltree', '--file', 'AndroidManifest.xml', base, capture=True)
    new_manifest = run(tools / 'aapt2', 'dump', 'xmltree', '--file', 'AndroidManifest.xml', work / 'shell.apk', capture=True)
    for name in re.findall(r'android:name\([^)]*\)="([^"]+)"', old_manifest):
        assert name in new_manifest, 'Manifest component/permission removed: ' + name
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(work / 'shell.apk') as shell, zipfile.ZipFile(work / 'unsigned.apk', 'w') as out:
        for entry in old.infolist():
            if entry.filename.startswith('META-INF/'):
                continue
            data = old.read(entry.filename)
            if entry.filename == 'classes.dex':
                data = (work / 'merged.dex').read_bytes()
            elif entry.filename == 'AndroidManifest.xml':
                data = shell.read(entry.filename)
            out.writestr(entry, data)
    run(tools / 'zipalign', '-f', '-P', '16', '4', work / 'unsigned.apk', output)
    for dex, folder in [('base.dex', 'old-smali'), ('merged.dex', 'new-smali')]:
        run('java', '-cp', a.dex_classpath, 'org.jf.baksmali.Main', 'disassemble', work / dex, '-o', work / folder)
    original = {str(f.relative_to(work / 'old-smali')): f.read_bytes() for f in (work / 'old-smali').rglob('*.smali')}
    current = {str(f.relative_to(work / 'new-smali')): f.read_bytes() for f in (work / 'new-smali').rglob('*.smali')}
    preserved = {k: v for k, v in original.items() if not selected(k)}
    assert all(current.get(k) == v for k, v in preserved.items()), 'Unrelated compiled class changed'
    assert not any(not selected(k) for k in current.keys() - original.keys()), 'Unexpected new class'
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(output) as new:
        retained = [n for n in old.namelist() if not n.startswith('META-INF/') and n not in ('classes.dex', 'AndroidManifest.xml')]
        assert all(old.read(n) == new.read(n) for n in retained), 'Bundled content changed'
        assert set(new.namelist()) == {n for n in old.namelist() if not n.startswith('META-INF/')}, 'Unexpected ZIP entry'
    badging = run(tools / 'aapt2', 'dump', 'badging', output, capture=True)
    assert f"name='org.openenglish.reader' versionCode='{VERSION_CODE}' versionName='{VERSION_NAME}'" in badging
    report = dict(base_sha256=BASE_SHA, versionName=VERSION_NAME, versionCode=VERSION_CODE,
                  preserved_classes=len(preserved), replaced_class_families=list(ROOTS),
                  retained_zip_entries=len(retained), bundled_models_and_media_unchanged=True,
                  physical_device_tested=False)
    (work / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
