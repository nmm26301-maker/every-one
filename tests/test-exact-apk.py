import importlib.util,pathlib,tempfile,unittest,zipfile,json,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('exact',root/'scripts/apk-exact.py');exact=importlib.util.module_from_spec(spec);spec.loader.exec_module(exact)

class ExactAPKTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.dir=pathlib.Path(self.temp.name)
        self.original=self.dir/'original.apk';self.base=self.dir/'base.apk';self.kit=self.dir/'kit.zip';self.output=self.dir/'output.apk'
        rows=[('assets/model.bin',b'unchanged model'*2000,8),('lib/arm64-v8a/native.so',b'native bytes'*1000,0),('classes.dex',b'new code',8),('META-INF/SIGNATURE',b'original signature',8)]
        with zipfile.ZipFile(self.original,'w') as z:
            z.comment=b'preserved archive comment'
            for name,data,method in rows:
                info=zipfile.ZipInfo(name,(2026,10,13,12,0,0));info.compress_type=method;info.extra=b'\x34\x12\x04\x00DEMO';z.writestr(info,data)
        with zipfile.ZipFile(self.base,'w') as z:
            for name,data,_ in reversed(rows):z.writestr(name,b'old code' if name=='classes.dex' else data)
        exact.pack(self.original,self.kit)
    def tearDown(self):self.temp.cleanup()
    def alter_kit(self,change):
        with zipfile.ZipFile(self.kit) as z:rows={n:z.read(n) for n in z.namelist()}
        change(rows)
        with zipfile.ZipFile(self.kit,'w',zipfile.ZIP_DEFLATED) as z:
            for n,data in rows.items():z.writestr(n,data)
    def test_exact_bytes_despite_old_code_and_different_base_layout(self):
        sha=exact.restore(self.base,self.kit,self.output)
        self.assertEqual(self.original.read_bytes(),self.output.read_bytes());self.assertEqual(sha,hashlib.sha256(self.original.read_bytes()).hexdigest())
    def test_wrong_baseline_preserves_existing_output(self):
        self.output.write_bytes(b'keep previous backup')
        with zipfile.ZipFile(self.base,'w') as z:z.writestr('assets/model.bin',b'wrong model')
        with self.assertRaises(ValueError):exact.restore(self.base,self.kit,self.output)
        self.assertEqual(self.output.read_bytes(),b'keep previous backup');self.assertEqual(list(self.dir.glob('apk-restore-*')),[])
    def test_payload_tamper_rejected(self):
        self.alter_kit(lambda rows:rows.update({'payload/2.bin':b'bad code'}))
        with self.assertRaises(ValueError):exact.restore(self.base,self.kit,self.output)
        self.assertFalse(self.output.exists())
    def test_footer_tamper_rejected(self):
        self.alter_kit(lambda rows:rows.update({'footer.bin':b'bad footer'}))
        with self.assertRaises(ValueError):exact.restore(self.base,self.kit,self.output)
    def test_header_tamper_caught_by_whole_file_hash(self):
        def change(rows):
            data=json.loads(rows['layout.json']);data['entries'][0]['header']=exact.b64(b'bad header');rows['layout.json']=json.dumps(data).encode()
        self.alter_kit(change)
        with self.assertRaises(ValueError):exact.restore(self.base,self.kit,self.output)
    def test_new_journey_assets_are_embedded_without_baseline_copy(self):
        with zipfile.ZipFile(self.original,'a') as z:z.writestr('assets/journey/course.json',b'{"new":"curriculum"}')
        exact.pack(self.original,self.kit)
        exact.restore(self.base,self.kit,self.output)
        self.assertEqual(self.original.read_bytes(),self.output.read_bytes())
    def test_inputs_cannot_be_overwritten(self):
        before=self.base.read_bytes()
        with self.assertRaises(ValueError):exact.restore(self.base,self.kit,self.base)
        self.assertEqual(before,self.base.read_bytes())

unittest.main()
