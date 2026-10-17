"""Preserve the exact signed APK using unchanged baseline assets plus a small restore kit."""
import argparse,base64,hashlib,json,os,pathlib,struct,tempfile,zipfile,zlib

def digest(data):return hashlib.sha256(data).hexdigest()
def b64(data):return base64.b64encode(data).decode('ascii')
def unb64(data):return base64.b64decode(data,validate=True)
def deflate(data):
    compressor=zlib.compressobj(6,zlib.DEFLATED,-15)
    return compressor.compress(data)+compressor.flush()

def pack(apk_path,kit_path):
    raw=pathlib.Path(apk_path).read_bytes()
    with zipfile.ZipFile(apk_path) as apk:
        entries=sorted(apk.infolist(),key=lambda i:i.header_offset)
        central=apk.start_dir;signature_start=central
        if raw[central-16:central]==b'APK Sig Block 42':
            size=struct.unpack_from('<Q',raw,central-24)[0]
            signature_start=central-size-8
            if signature_start<0 or struct.unpack_from('<Q',raw,signature_start)[0]!=size:
                raise ValueError('Invalid APK signing block')
        layout={'format':1,'apk_sha256':digest(raw),'apk_bytes':len(raw),'zlib_version':zlib.ZLIB_RUNTIME_VERSION,'entries':[]}
        target=pathlib.Path(kit_path);target.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as kit:
            for index,entry in enumerate(entries):
                at=entry.header_offset
                if raw[at:at+4]!=b'PK\x03\x04':raise ValueError('Invalid local header')
                name_size,extra_size=struct.unpack_from('<HH',raw,at+26)
                data_at=at+30+name_size+extra_size;data_end=data_at+entry.compress_size
                next_at=entries[index+1].header_offset if index+1<len(entries) else signature_start
                if data_end>next_at:raise ValueError('Overlapping archive entries')
                compressed=raw[data_at:data_end];plain=apk.read(entry.filename)
                reusable=entry.filename.startswith(('assets/','lib/')) and not entry.filename.startswith('assets/journey/') and not entry.is_dir()
                row={'name':entry.filename,'offset':at,'header':b64(raw[at:data_at]),'tail':b64(raw[data_end:next_at]),'method':entry.compress_type,'plain_sha256':digest(plain),'raw_sha256':digest(compressed),'raw_bytes':len(compressed),'baseline':reusable}
                if reusable:
                    rebuilt=plain if entry.compress_type==0 else deflate(plain) if entry.compress_type==8 else None
                    if rebuilt!=compressed:raise ValueError('Compression cannot reproduce '+entry.filename)
                else:
                    row['payload']='payload/'+str(index)+'.bin';kit.writestr(row['payload'],compressed)
                layout['entries'].append(row)
            kit.writestr('footer.bin',raw[signature_start:]);layout['footer_sha256']=digest(raw[signature_start:])
            kit.writestr('layout.json',json.dumps(layout,indent=2))
    return layout

def restore(base_path,kit_path,output_path):
    output=pathlib.Path(output_path)
    if output.resolve() in (pathlib.Path(base_path).resolve(),pathlib.Path(kit_path).resolve()):
        raise ValueError('Output must differ from inputs')
    output.parent.mkdir(parents=True,exist_ok=True);temporary=None
    try:
        with zipfile.ZipFile(base_path) as base,zipfile.ZipFile(kit_path) as kit:
            layout=json.loads(kit.read('layout.json'))
            if layout['format']!=1:raise ValueError('Unsupported restore format')
            names=set()
            with tempfile.NamedTemporaryFile(dir=output.parent,prefix='apk-restore-',delete=False) as sink:
                temporary=pathlib.Path(sink.name);whole=hashlib.sha256()
                def write(data):sink.write(data);whole.update(data)
                for row in layout['entries']:
                    if row['name'] in names:raise ValueError('Duplicate restore entry')
                    names.add(row['name'])
                    if sink.tell()!=row['offset']:raise ValueError('Archive layout offset mismatch')
                    if row['baseline']:
                        if not row['name'].startswith(('assets/','lib/')):raise ValueError('Unexpected baseline entry')
                        plain=base.read(row['name'])
                        if digest(plain)!=row['plain_sha256']:raise ValueError('Baseline checksum differs: '+row['name'])
                        if row['method']==0:data=plain
                        elif row['method']==8:data=deflate(plain)
                        else:raise ValueError('Unsupported ZIP compression')
                    else:data=kit.read(row['payload'])
                    if len(data)!=row['raw_bytes'] or digest(data)!=row['raw_sha256']:
                        raise ValueError('Compressed entry checksum differs: '+row['name'])
                    write(unb64(row['header']));write(data);write(unb64(row['tail']))
                footer=kit.read('footer.bin')
                if digest(footer)!=layout['footer_sha256']:raise ValueError('APK footer checksum differs')
                write(footer)
                if sink.tell()!=layout['apk_bytes'] or whole.hexdigest()!=layout['apk_sha256']:
                    raise ValueError('Restored signed APK checksum differs')
                sink.flush();os.fsync(sink.fileno())
        os.replace(temporary,output);temporary=None
        return layout['apk_sha256']
    finally:
        if temporary is not None:temporary.unlink(missing_ok=True)

if __name__=='__main__':
    p=argparse.ArgumentParser();sub=p.add_subparsers(dest='command',required=True)
    a=sub.add_parser('pack');a.add_argument('apk');a.add_argument('kit')
    a=sub.add_parser('restore');a.add_argument('base');a.add_argument('kit');a.add_argument('output')
    args=p.parse_args()
    if args.command=='pack':print(json.dumps({'sha256':pack(args.apk,args.kit)['apk_sha256'],'kit':args.kit}))
    else:print('Exact signed APK restored: '+restore(args.base,args.kit,args.output))
