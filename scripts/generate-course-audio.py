"""Build-time only: Piper 1.8.0 / en_US-ljspeech-high, MP3 course samples.

Install piper-tts==1.8.0 and ffmpeg. Pass the downloaded model path and card.
The Android application includes only generated audio and attribution.
"""
import os
os.environ['ORT_DISABLE_TELEMETRY'] = '1'  # Must precede onnxruntime import.
import argparse, hashlib, json, pathlib, subprocess, wave, zipfile
import onnxruntime as ort
ort.disable_telemetry_events()
from piper import PiperVoice

ROOT = pathlib.Path(__file__).resolve().parents[1]
sha = lambda b: hashlib.sha256(b).hexdigest()

def main():
    p = argparse.ArgumentParser(__doc__)
    p.add_argument('--model', required=True)
    p.add_argument('--model-card', required=True)
    a = p.parse_args()
    model = pathlib.Path(a.model)
    card = pathlib.Path(a.model_card).read_bytes()
    if b'public domain' not in card.lower():
        raise ValueError('Review the voice dataset license before generating samples')
    target = ROOT / 'app/src/main/assets/journey/course-audio'
    target.mkdir(parents=True, exist_ok=True)
    work = ROOT / 'build/course-audio'
    work.mkdir(parents=True, exist_ok=True)
    voice = PiperVoice.load(str(model))
    course = json.loads((ROOT / 'app/src/main/assets/journey/course.json').read_text())
    index = dict(voice='en_US-ljspeech-high', engine='piper-tts 1.8.0 (build-time GPL-3.0)',
                 model_sha256=sha(model.read_bytes()),
                 config_sha256=sha(pathlib.Path(str(model)+'.json').read_bytes()),
                 telemetry_disabled_before_runtime_import=True, samples=[])
    packs = {i: [] for i in range(6)}
    for unit in course['lessons']:
        for n, sentence in enumerate(unit['sentences']):
            key = unit['id']+'_'+str(n)
            wav, mp3 = work/(key+'.wav'), work/(key+'.mp3')
            with wave.open(str(wav), 'wb') as f:
                voice.synthesize_wav(sentence['en'], f)
            subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-ac','1','-ar','22050',
                            '-b:a','40k','-map_metadata','-1',str(mp3)], check=True)
            data = mp3.read_bytes()
            packs[unit['level']].append((key+'.mp3', data))
            index['samples'].append(dict(key=key, text=sentence['en'], pack='level'+str(unit['level'])+'.zip',
                                         sha256=sha(data), bytes=len(data)))
        print(unit['id'], flush=True)
    for level, items in packs.items():
        with zipfile.ZipFile(target/('level'+str(level)+'.zip'), 'w', compression=zipfile.ZIP_STORED) as z:
            for name, data in items:
                z.writestr(zipfile.ZipInfo(name, (1980,1,1,0,0,0)), data)
    (target/'index.json').write_text(json.dumps(index, ensure_ascii=False, indent=2)+'\n')
    (target/'MODEL_CARD.txt').write_bytes(card)
    (target/'NOTICE.txt').write_text('Built-in course sample speech: en_US-ljspeech-high.\n'
        'Voice: https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/ljspeech/high\n'
        'Voice repository MIT; individual model card identifies LJ Speech dataset as public domain.\n'
        'Generator: https://github.com/OHF-Voice/piper1-gpl (GPL-3.0), build-time only.\n'
        'Piper engine, ONNX Runtime and voice weights are not distributed in the APK.\n'
        'Generated Everyone curriculum audio: CC0-1.0 to the extent we hold rights.\n'
        'Synthetic single-speaker samples; supplement with real speakers and varied accents.\n')

if __name__ == '__main__':
    main()
