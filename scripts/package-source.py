"""Package distributable source, signing key and current validation evidence only."""
import pathlib,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
destination=root/'dist/Everyone-AI-English-4.6.1-source.zip'
files=[]
for directory in ['app','scripts','tests','native','vendor']:
    for path in (root/directory).rglob('*'):
        if not path.is_file() or '__pycache__' in path.parts:continue
        if path.name in ['ui-smoke.py','final-probes.py']:continue
        files.append(path)
for name in ['README.md','README-4.1.md','README-4.2.md','README-4.3.md','README-4.4.md','README-4.5.md','README-4.6.1.md','VALIDATION.md','LICENSE','build.sh','.gitignore','build/debug.keystore']:
    files.append(root/name)
for path in (root/'qa').glob('*'):
    if path.is_file() and ((path.name.startswith(('v3-', 'v31-', 'v32-', 'v33-', 'v34-', 'v35-', 'v36-', 'v37-', 'v38-', 'v39-', 'v40-', 'v41-', 'v42-', 'v43-', 'v44-', 'v45-', 'v46-', 'v461-')) and not any(term in path.name for term in ['failure','diagnosis','first'])) or path.name in ['legacy-storage.txt','studio-validation.txt','upgrade-seed.txt','upgrade-validation.txt','ui-persistence.txt','runtime.txt','dictionary-ui.txt']):files.append(path)
destination.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(files):archive.write(path,'Everyone-AI-English-4.6.1/'+path.relative_to(root).as_posix())
print(destination)
print('Source files:',len(files),'ZIP bytes:',destination.stat().st_size)
