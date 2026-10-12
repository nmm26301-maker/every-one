"""Generate honest, offline synthetic demo speech using FFmpeg/libflite."""
import json, pathlib, subprocess, tempfile, wave
root = pathlib.Path(__file__).resolve().parents[1]
phrases = ['A small habit can make a big difference.', 'Start with one sentence today.', 'Listen carefully and speak slowly.', 'You do not need to be perfect.', 'Try again when a word feels difficult.', 'Read a little and notice something new.', 'Keep the expressions you want to use.', 'Ask a question and enjoy the conversation.', 'Every small step helps you grow.', 'Make English a part of your day.']
with tempfile.TemporaryDirectory() as temp:
    temp = pathlib.Path(temp); cues = []; pcm = bytearray(); rate = 16000
    for i, phrase in enumerate(phrases):
        text = temp / f'{i}.txt'; audio = temp / f'{i}.wav'; text.write_text(phrase)
        subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i',f'flite=textfile={text}:voice=slt','-ar',str(rate),'-ac','1','-c:a','pcm_s16le',str(audio)],check=True)
        with wave.open(str(audio)) as wav: data = wav.readframes(wav.getnframes())
        start = len(pcm) * 1000 // (rate*2); pcm.extend(data)
        end = len(pcm) * 1000 // (rate*2); cues.append({'start':start,'end':end,'text':phrase})
        pcm.extend(bytes(rate*2*350//1000))
    all_audio = temp / 'all.wav'
    with wave.open(str(all_audio),'wb') as wav:
        wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(rate);wav.writeframes(pcm)
    assets = root / 'app/src/main/assets'
    subprocess.run(['ffmpeg','-y','-v','error','-i',str(all_audio),'-c:a','aac','-b:a','96k',str(assets/'studio-demo.m4a')],check=True)
    (assets/'studio-demo.json').write_text(json.dumps(cues,ensure_ascii=False,indent=2))
print('Generated 10 synthetic sentences with actual audio timestamps')
