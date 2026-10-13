"""Replace only executable code and same-length version metadata in a verified APK."""
import argparse,hashlib,pathlib,struct,zipfile
p=argparse.ArgumentParser()
p.add_argument('base');p.add_argument('dex');p.add_argument('output');p.add_argument('expected_sha');p.add_argument('old_version');p.add_argument('new_version');p.add_argument('old_code',type=int);p.add_argument('new_code',type=int)
a=p.parse_args();assert hashlib.sha256(pathlib.Path(a.base).read_bytes()).hexdigest()==a.expected_sha
with zipfile.ZipFile(a.base) as src:
    manifest=bytearray(src.read('AndroidManifest.xml'));position=8;resources=[];changed=0
    while position<len(manifest):
        kind,header,size=struct.unpack_from('<HHI',manifest,position);assert size>=header and size>0
        if kind==0x180:resources=list(struct.unpack_from('<'+'I'*((size-header)//4),manifest,position+header))
        if kind==0x102:
            start,stride,count=struct.unpack_from('<HHH',manifest,position+24)
            for i in range(count):
                at=position+16+start+i*stride;name=struct.unpack_from('<I',manifest,at+4)[0]
                if name<len(resources) and resources[name]==0x0101021b:
                    assert struct.unpack_from('<I',manifest,at+16)[0]==a.old_code
                    struct.pack_into('<I',manifest,at+16,a.new_code);changed+=1
        position+=size
    assert changed==1
    replaced=0
    for encoding in ['utf-8','utf-16le']:
        old=a.old_version.encode(encoding);new=a.new_version.encode(encoding);assert len(old)==len(new)
        replaced+=manifest.count(old);manifest=manifest.replace(old,new)
    assert replaced==1
    out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,'w') as dst:
        for entry in src.infolist():
            if entry.filename.startswith('META-INF/'):continue
            data=src.read(entry.filename)
            if entry.filename=='AndroidManifest.xml':data=manifest
            elif entry.filename=='classes.dex':data=pathlib.Path(a.dex).read_bytes()
            dst.writestr(entry,data)
print('Packaged '+a.new_version+' versionCode '+str(a.new_code))
