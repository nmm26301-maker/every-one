import json,pathlib,zipfile,sqlite3,tempfile,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
with zipfile.ZipFile(root/'dist/Everyone-AI-English-4.6.1.apk') as apk:
    assert 'classes.dex' in apk.namelist()
    for name in ['libenglish_whisper.so','libenglish_llama.so','libenglish_speech.so','libc++_shared.so','libonnxruntime.so','libonnxruntime4j_jni.so']:assert 'lib/arm64-v8a/'+name in apk.namelist()
    assert hashlib.sha256(apk.read('assets/models/ggml-tiny.en.bin')).hexdigest()=='921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f'
    assert 'assets/LOCAL-AI-NOTICES.txt' in apk.namelist()
    assert hashlib.sha256(apk.read('assets/models/ggml-base.bin')).hexdigest()=='60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe'
    assert 'assets/languages/espeak-data.zip' in apk.namelist()
    for n in ['Jyut-Dojo-MIT.txt','eSpeak-NG-GPL-3.txt','Kuromoji-Apache-2.txt','IPADIC-NOTICE.txt']:assert 'assets/licenses/'+n in apk.namelist()
    assert hashlib.sha256(apk.read('assets/models/ggml-silero-v6.2.0.bin')).hexdigest()=='2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987'
    assert 'assets/SILERO-VAD-LICENSE.txt' in apk.namelist()
    language=json.loads(apk.read('assets/languages/lessons.json'))
    assert len(language)==204
    tifa=json.loads(apk.read('assets/languages/tifa-manifest.json'))
    for name,item in tifa.items():
        content=apk.read('assets/languages/tifa/'+name)
        assert len(content)==item['bytes'] and hashlib.sha256(content).hexdigest()==item['sha256']
    templates=json.loads(apk.read('assets/languages/segments.json'));spans=json.loads(apk.read('assets/languages/reference-segments.json'))
    assert set(templates)==set(spans)=={l['id'] for l in language}
    for id,t in templates.items():
        assert len(t['tokens'])==len(t['groups'])==len(t['phones'])==len(spans[id]['spans'])
    for license in ['TIFA-MIT.txt','TIFA-MODEL-NOTICE.txt','TIFA-MODEL-CC-BY-NC-SA-4.0.txt','ONNX-Runtime-MIT.txt']:
        assert len(apk.read('assets/licenses/'+license))>500

    ids={l['id'] for l in language};assert len(ids)==204
    assert [sum(l['lang']==code for l in language) for code in ['yue','ja','zh']]==[176,12,16]
    provenance={p['id']:p for p in json.loads(apk.read('assets/languages/reference-index-v41.json'))}
    audio_hashes=[]
    for l in language:
        assert not l['pair'] or l['pair'] in ids
        content=apk.read('assets/languages/'+l['audio'])
        assert content[:4]==b'RIFF' and content[8:12]==b'WAVE'
        digest=hashlib.sha256(content).hexdigest();audio_hashes.append(digest)
        assert digest==provenance[l['id']]['sha256']
        assert content==(root/'app/src/main/assets/languages'/l['audio']).read_bytes()
    assert len(set(audio_hashes))==199
    # Five legacy starter cards intentionally retain their IDs/progress when the
    # same original Jyut Dojo examples also appear in the expanded course.
    duplicates={}
    for lesson,digest in zip(language,audio_hashes):duplicates.setdefault(digest,[]).append(lesson)
    expected={frozenset(pair) for pair in [('yue_baat3daat6tung1','dojo_w_baat3daat6tung1'),('yue_m4goi1','dojo_w_m4goi1'),('yue_g1','dojo_s_g1'),('yue_c3','dojo_s_c3'),('yue_c8','dojo_s_c8')]}
    actual={frozenset(l['id'] for l in g) for g in duplicates.values() if len(g)>1}
    assert actual==expected
    for g in duplicates.values():assert len({(l['text'],l['reading']) for l in g})==1
    assert b'Copyright (c) 2026 cheeryaudio' in apk.read('assets/licenses/Jyut-Dojo-MIT.txt')
    assert len(json.loads(apk.read('assets/lessons.json')))==60
    assert len([p for p in apk.namelist() if p.startswith('assets/book/') and p.endswith('.html')])==10
    assert len(json.loads(apk.read('assets/studio-demo.json')))==10
    for name in ['LICENSE','ECDICT-LICENSE.txt','studio-demo.m4a','lexicon-source.json']:assert 'assets/'+name in apk.namelist()
    meta=json.loads(apk.read('assets/lexicon-source.json'))
    with tempfile.TemporaryDirectory(dir=root/'build') as temporary:
        path=pathlib.Path(temporary)/'lexicon.db';path.write_bytes(apk.read('assets/lexicon.db'))
        with sqlite3.connect(path) as db:
            assert db.execute('select count(*) from lexicon').fetchone()[0]==20000
            assert db.execute('select count(*) from forms').fetchone()[0]==meta['forms']
print('PASS: DEX, 204 three-language lesson cards, 199 distinct verified clips (five legacy examples retained), MIT notice, 60 English lessons, 10 book chapters, real audio, 10 cues, 20,000 dictionary entries, forms and licenses')
