import zipfile,pathlib,sys,re
base=pathlib.Path(sys.argv[1]);build=pathlib.Path(sys.argv[2])
def icon(text):return re.search(r'resource (0x[0-9a-f]+) (?:org.openenglish.reader:)?drawable/icon',text)[1]
assert icon((build/'base-res.txt').read_text())==icon((build/'new-res.txt').read_text())
# Keep every existing activity, permission and query. Only move the launcher.
old=(build/'base-manifest.txt').read_text();new=(build/'new-manifest.txt').read_text()
for name in re.findall(r'android:name\([^)]*\)="([^"]+)"',old):
 assert name in new,('Manifest component/permission removed',name)
with zipfile.ZipFile(base) as src,zipfile.ZipFile(build/'shell.apk') as shell,zipfile.ZipFile(build/'unsigned.apk','w') as out:
 for entry in src.infolist():
  if entry.filename.startswith('META-INF/'):continue
  data=src.read(entry.filename)
  if entry.filename=='AndroidManifest.xml':data=shell.read(entry.filename)
  if entry.filename=='classes.dex':data=(build/'merged.dex').read_bytes()
  out.writestr(entry,data)
 for p in pathlib.Path('app/src/main/assets/journey').rglob('*'):
  if p.is_file():out.write(p,'assets/journey/'+str(p.relative_to('app/src/main/assets/journey')),compress_type=zipfile.ZIP_DEFLATED)
