import asyncio, json, pathlib, shutil, ssl, subprocess, hashlib
import edge_tts, edge_tts.communicate

ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/languages'
OUT.mkdir(parents=True,exist_ok=True)
edge_tts.communicate._SSL_CTX=ssl.create_default_context(cafile='/etc/ssl/certs/ca-certificates.crt')
lessons=[]
def add(lang,key,title,text,reading,meaning,tip,focus='pitch',stage='发音花园',pair='',question='跟读时，先做哪一步？',answer='先听完整示范，再开口',wrong='跳过示范，越快越好',source=''):
 lessons.append(dict(lang=lang,id=lang+'_'+key,title=title,text=text,reading=reading,meaning=meaning,tip=tip,focus=focus,stage=stage,pair=lang+'_'+pair if pair else '',question=question,answer=answer,wrong=wrong,audio=lang+'_'+key+'.wav',source=source))

for n,h,z,t in [(3,'試','中平调','先用舒服的中音定位置，平稳送气，不要拖出升降。'),(1,'詩','高平调','比中音抬高一点，保持平直，不需要用力喊。'),(6,'事','低平调','比中音低一点，声音仍然清楚，别压嗓子。'),(2,'史','高升调','从中低的位置向上走，像轻轻提一个问题。'),(5,'市','低升调','低处起音，轻轻升到中音；不要像二声升得那么高。'),(4,'時','低降调','从较低位置轻轻落下，收在低处。')]:
 add('yue','si'+str(n),z,h,'si'+str(n),{1:'诗',2:'历史',3:'尝试',4:'时间',5:'城市',6:'事情'}[n],t+' 舌尖靠近下齿后，s 的气流细而连续。','level' if n in (1,3,6) else 'pitch','01 · 高中低' if n in (1,3,6) else '02 · 升调与低降',pair='si3' if n!=3 else 'si1',question='粤拼尾数字表示什么？',answer='声调，不是读几遍',wrong='这个字要读几遍',source='tones/si'+str(n)+'.mp3')
words=json.loads((ROOT/'vendor/jyut-dojo/src/data/words.json').read_text())
for key,title,tip in [('sik6','入声 · 食','尾音 -k 在舌后部收住，短促结束；不要再补一个“克”。'),('baat3daat6tung1','入声 · 八达通','baat 和 daat 都在 -t 收住，舌尖抵住上齿龈，不再加一个元音。'),('m4goi1','礼貌 · 唔該','唔是闭唇的鼻音 m；該 goi 从圆唇开口开始。')]:
 w=next(x for x in words if x['id']==key)
 if key=='sik6':w=dict(w,hanzi='食飯',jyutping='sik6 faan6',mandarin='吃饭')
 add('yue',key,title,w['hanzi'],w['jyutping'],w['mandarin'],tip,'rhythm','03 · 入声与短句',source=w['audio'].replace('/audio/',''),question='粤语入声收尾应该怎样？',answer='收住 -p/-t/-k，不再添元音',wrong='把最后的辅音再读成一个字')
sentences=json.loads((ROOT/'vendor/jyut-dojo/src/data/sentences.json').read_text())
for key,title,tip in [('g1','见面 · 你好呀','nei5 是低升，hou2 是高升，最后 aa3 轻松收在中音。'),('c3','点餐 · 两位','loeng5 的韵母要圆唇；先听单词，再把短句连起来。'),('c8','点餐 · 买单','先说唔該，再说埋單；语气轻松，按示范的停顿来。')]:
 s=next(x for x in sentences if x['id']==key)
 add('yue',key,title,s['hanzi'],s['jyutping'],s['mandarin'],tip,'rhythm','04 · 生活小咒语',source=s['audio'].replace('/audio/',''))

for key,h,r,title,tip in [('ma1','妈','mā','一声 · 平稳','嘴自然打开，m 闭唇起音；高处平稳送出，不往下掉。'),('ma2','麻','má','二声 · 向上','从中音向上走，后半段升起来，不要先明显降下去。'),('ma3','马','mǎ','三声 · 转弯','练单字时先低后升，别把拐点抬得太高；连读时常只读低段。'),('ma4','骂','mà','四声 · 落下','起点较高，干脆落下；别把尾巴拖成上扬。')]:
 add('zh',key,title,h,r,'单字四声跟读',tip,'pitch','01 · 四声小阶梯',pair='ma2' if key=='ma4' else 'ma4',question='单字“马”的示范属于哪一声？',answer='三声，先低后升',wrong='一声，始终平直')
for a,b,ha,hb,ra,rb in [('zhi','zi','知','资','zhī','zī'),('chi','ci','吃','词','chī','cí'),('shi','si','诗','丝','shī','sī'),('nian','lian','年','连','nián','lián')]:
 for key,text,reading,pair in [(a,ha,ra,b),(b,hb,rb,a)]:
  retro=key in ('zhi','chi','shi'); nasal=key=='nian'
  if key in ('nian','lian'):
   tip=('舌尖抵上齿龈，气流从鼻腔出来；可先哼 n 再接 ian。' if nasal else '舌尖抵上齿龈，气流从舌两侧出来；可先轻读 l 再接 ian。')+' 将年/连对照听，不用加快速度。'
  else:
   tip=('舌尖稍抬向上齿龈后方，不必用力卷舌。' if retro else '舌尖靠近下齿后，气流在前方；不要带上翘的舌位。')+('送气明显，用纸巾感受轻吹。' if key in ('chi','ci') else '气流细而稳定。')
  add('zh',key,'舌位 · '+text,text,reading,'和“'+(hb if key==a else ha)+'”对照听',tip,'rhythm','02 · 舌尖与鼻音',pair=pair,question='纠正舌位时，哪种练法更合适？',answer='先练单音，再到词和短句',wrong='立刻高速读绕口令')

for key,text,reading,title,tip in [('words_zhi','知道','zhī dao','连成词 · 知道','把“知”的舌位带进词里；道读轻声，不要每一字都用同样的力。'),('words_chi','吃饭','chī fàn','连成词 · 吃饭','吃是轻轻送气的翘舌音，饭干脆落下；从单音吃过渡到自然词语。'),('sentence','这是我的书。','zhè shì wǒ de shū','连成句 · 我的书','先慢读这、是，留意舌尖位置；再把全句自然连起来，的读轻声。'),('request','请再说一遍。','qǐng zài shuō yí biàn','连成句 · 再说一遍','请不要省去后鼻音 ng。说的舌尖稍抬；一在遍前读二声 yí。')]:
 add('zh',key,title,text,reading,'从单音到词，再到一句自然的话',tip,'rhythm','03 · 把声音连成句')

for key,text,reading,tip in [('a','あ','a','嘴自然打开，舌头放松，声音短而清楚。'),('i','い','i','嘴角轻轻向两边展开，不要把声音挤在喉咙里。'),('u','う','u','双唇稍收，避免像汉语“乌”那样用力突出嘴唇。'),('e','え','e','嘴稍张，接近清楚的单元音，不拖成 ei。'),('o','お','o','口腔圆一点，保持一个元音，不拖成 ou。')]:
 add('ja',key,'五十音 · '+text,text,reading,'先学一个清楚的短音',tip,'rhythm','01 · 元音小花园',question='日语短元音先怎么练？',answer='一拍清楚读完，不拖出第二个元音',wrong='尽量拉长，读成两个元音')
for key,text,reading,title,meaning,tip,pair in [('obasan','おばさん','o-ba-sa-n','短音 · 阿姨','阿姨','四拍：お・ば・さ・ん。ば读一拍，不要拉成ばあ。','obaasan'),('obaasan','おばあさん','o-ba-a-sa-n','长音 · 奶奶','奶奶','五拍：お・ば・あ・さ・ん。ばあ占两拍，拍手时别吞掉中间那一拍。','obasan'),('kita','来た','ki-ta','短音 · 来了','来了','两拍：き・た。中间直接衔接，不留促音那一拍。','kitta'),('kitta','切った','ki-t-ta','促音 · 切了','切了','三拍：き・っ・た。っ留一拍准备 t，但不发新的元音。','kita'),('nihon','日本','ni-ho-n','鼻音 · 日本','日本','三拍：に・ほ・ん。ん占一拍，按示范自然收在鼻音。',''),('hello','こんにちは','ko-n-ni-chi-wa','问候 · 你好','你好','五拍：こ・ん・に・ち・は。这里的は读 wa，逐拍听后再连起来。',''),('thanks','ありがとう','a-ri-ga-to-o','礼貌 · 谢谢','谢谢','五拍：あ・り・が・と・う。最后とう为长音，别把最后一拍吞掉。','')]:
 add('ja',key,title,text,reading,meaning,tip,'rhythm','02 · 长音与促音' if pair else '03 · 生活小咒语',pair=pair,question='日语的长音或促音怎么算？',answer='各占一拍，不能随便省掉',wrong='只是装饰，不占时间')

for l in lessons:
 if l['id']=='yue_si1':l['focus']='level_high'
 if l['id']=='yue_si6':l['focus']='level_low'

async def make_audio():
 sem=asyncio.Semaphore(3)
 async def one(l):
  target=OUT/l['audio']; temp=OUT/(l['id']+'.mp3')
  if target.exists() and target.stat().st_size>44:return
  async with sem:
   if l['lang']=='yue' and l['id']!='yue_sik6':
    shutil.copyfile((ROOT/'vendor/jyut-dojo/original-audio')/l['source'],temp)
   else:
    for attempt in range(3):
     try:
      await edge_tts.Communicate(l['text'],'ja-JP-NanamiNeural' if l['lang']=='ja' else 'zh-HK-WanLungNeural' if l['lang']=='yue' else 'zh-CN-XiaoxiaoNeural',rate='-35%' if l['lang']=='ja' else '-25%' if l['lang']=='yue' else '+0%',receive_timeout=30).save(str(temp));break
     except Exception:
      if attempt==2:raise
   subprocess.run(['ffmpeg','-y','-v','error','-i',str(temp),'-ac','1','-ar','16000','-c:a','pcm_s16le',str(target)],check=True)
   temp.unlink();print(l['id'],target.stat().st_size,flush=True)
 await asyncio.gather(*(one(l) for l in lessons))

if __name__=='__main__':
 (OUT/'lessons.json').write_text(json.dumps(lessons,ensure_ascii=False,indent=2)+'\n')
 asyncio.run(make_audio())
 (OUT/'audio-provenance.json').write_text(json.dumps([dict(id=l['id'],sha256=hashlib.sha256((OUT/l['audio']).read_bytes()).hexdigest(),origin='Jyut Dojo MIT repository, synthetic zh-HK reference' if l['lang']=='yue' and l['id']!='yue_sik6' else 'Build-time Edge TTS synthesis: '+('Nanami ja-JP, -35% rate' if l['lang']=='ja' else 'WanLung zh-HK, -25% rate' if l['lang']=='yue' else 'Xiaoxiao zh-CN'),human_reviewed=False) for l in lessons],indent=2)+'\n')
 print('Lessons:',len(lessons))
