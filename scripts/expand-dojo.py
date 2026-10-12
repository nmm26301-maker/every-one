import json,pathlib,subprocess,concurrent.futures,shutil,hashlib
r=pathlib.Path(__file__).resolve().parents[1];up=r/'vendor/jyut-dojo';assets=r/'app/src/main/assets/languages';vendor=r/'vendor/jyut-dojo';data=json.load(open(assets/'lessons.json'));ids={l['id'] for l in data};scenes=json.load(open(up/'src/data/scenarios.json'));names={s['id']:s['title'] for s in scenes};tasks=[];extra=[]
for kind in ['words','sentences','tonesets']:
 entries=json.load(open(up/f'src/data/{kind}.json'))
 if kind=='tonesets':entries=[dict(t,id=x['syllable']+str(t['tone'])) for x in entries if x['syllable']!='si' for t in x['items']]
 for e in entries:
  id='dojo_'+kind[:1]+'_'+e['id'];wave=id+'.wav';reading=e['jyutping'];tip='先听整句，再分成小段跟读。每个字不拖长，保持自己的舒服音高。'
  focus='phrase'
  if kind=='words':
   tip='先跟着示范读一个词，再自己回放对照。注意粤拼末尾的数字是声调。'
   if any(x.rstrip('123456').endswith(('p','t','k')) for x in reading.split()):tip='入声的尾音 -p、-t、-k 要收住，不要再补一个“啊”。先慢听，再短促地结束。'
  if kind=='tonesets':
   tone=e['tone'];focus={1:'level_high',2:'rise_high',3:'level_mid',4:'fall_low',5:'rise_low',6:'level_low'}[tone]
   tip={1:'舒服中音上方，保持高而平，不要用力喊。',2:'从中音向高音升上去。',3:'舒服的中音，平稳结束。',4:'从较低的位置轻轻降下去。',5:'低音起步，向中音轻轻抬起。',6:'比自己的中音低一些，保持平稳。'}[tone]
  lesson={'id':id,'lang':'yue','stage':'04 · 声调家族' if kind=='tonesets' else '05 · 常用词语' if kind=='words' else '06 · '+names.get(e.get('scenarioId'),'生活对话'),'title':e['hanzi'],'text':e['hanzi'],'reading':reading,'meaning':e['mandarin'],'tip':tip,'focus':focus,'pair':'','question':'这句话 / 这个词是什么意思？','answer':e['mandarin'],'wrong':'还没有想起来','audio':wave,'source':'Jyut Dojo · MIT · synthetic Edge TTS','scenario':names.get(e.get('scenarioId'),'自由聊天'),'speaker':e.get('speaker','')}
  if id not in ids:data.append(lesson);ids.add(id)
  src=vendor/'original-audio'/('dojo_'+kind+'_'+pathlib.Path(e['audio']).name)
  tasks.append((src,assets/wave));extra.append({'id':id,'source_audio':e['audio'],'source_sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'wave':wave})
def convert(p):
 src,out=p
 subprocess.run(['ffmpeg','-y','-v','error','-i',str(src),'-ar','16000','-ac','1','-c:a','pcm_s16le',str(out)],check=True)
with concurrent.futures.ThreadPoolExecutor(4) as pool:list(pool.map(convert,tasks))
for l in data:
 if l['id'].startswith('dojo_t_'):
  base=l['id'][:-1];tone=int(l['id'][-1]);l['pair']=base+str({1:3,2:5,3:1,4:6,5:2,6:4}[tone])
(assets/'lessons.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
(vendor/'EXPANSION.json').write_text(json.dumps(extra,ensure_ascii=False,indent=2))
print('lessons',len(data),'new Jyut lessons',len(tasks))
