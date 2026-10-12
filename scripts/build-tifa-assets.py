"""Rebuild TIFA-derived assets from the verified original release (noncommercial CC BY-NC-SA).
Usage: python3 scripts/build-tifa-assets.py --weights-dir /path/TIFA-1.0-ST
The prebuilt assets are sufficient for building the APK; this pipeline is optional.
"""
import argparse,hashlib,json,pathlib,re,sys,unicodedata,wave
import numpy as np
import torch,onnxruntime as ort,yaml
root=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'vendor/tifa/upstream'))
from lib.config.schema import ModelConfig,InferenceConfig,ConfigurationScope
from inference.backend import ForcedAlignmentInferenceModel
from deployment.exporter import Exporter
from modules.decoding import _decode_flat_single
args=argparse.ArgumentParser();args.add_argument('--weights-dir',type=pathlib.Path,required=True);weights=args.parse_args().weights_dir
expected='8d9bf896da1827f36bf135f80c7ab1fc4f3c1d48f328818b8402c9527c0f014b'
assert hashlib.sha256((weights/'model.pt').read_bytes()).hexdigest()==expected,'Original model hash differs'
cfg=yaml.safe_load((weights/'config.yaml').read_text());backend=ForcedAlignmentInferenceModel(ModelConfig.model_validate(cfg['model'],scope=ConfigurationScope.FA),InferenceConfig.model_validate(cfg['inference']))
backend.load_state_dict(torch.load(weights/'model.pt',map_location='cpu',weights_only=True)['state_dict'],strict=True);backend.eval()
assets=root/'app/src/main/assets/languages';dest=assets/'tifa';export=root/'build/tifa-export';Exporter(backend,export).export();dest.mkdir(exist_ok=True)
manifest={}
for name in ['spectrogram.onnx','model.onnx']:
 data=(export/name).read_bytes();(dest/name).write_bytes(data);manifest[name]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
(assets/'tifa-manifest.json').write_text(json.dumps(manifest,indent=2))
lessons=json.loads((assets/'lessons.json').read_text());vocab=json.loads((weights/'vocabulary.json').read_text())['symbols']
dicts={l:dict(line.strip().split('\t',1) for line in (weights/'dictionaries'/f).read_text().splitlines() if '\t' in line) for l,f in [('yue','jyutping_dict.txt'),('zh','ds-zh-pinyin-lite.txt'),('ja','japanese_dict_full.txt')]};dicts['yue'].update(m='m',ng='ng')
templates={}
for l in lessons:
 lang=l['lang'];labels=l['reading'].split('-') if lang=='ja' else l['reading'].split();ids=[];groups=[];phones=[]
 for i,label in enumerate(labels):
  bare=''.join(c for c in unicodedata.normalize('NFD',label) if not unicodedata.combining(c));bare=re.sub(r'[1-6]$','',bare)
  if lang=='ja' and l['id']=='ja_kitta' and i==1:bare='cl'
  for ph in dicts[lang][bare].split():
   ids.append(vocab[ph if ph in vocab else lang+'/'+ph]);groups.append(i);phones.append(ph)
 templates[l['id']]={'labels':labels,'tokens':ids,'groups':groups,'phones':phones}
(assets/'segments.json').write_text(json.dumps(templates,ensure_ascii=False,separators=(',',':')))
options=ort.SessionOptions();options.intra_op_num_threads=2;options.inter_op_num_threads=1
spec=ort.InferenceSession(str(dest/'spectrogram.onnx'),options);model=ort.InferenceSession(str(dest/'model.onnx'),options);refs={}
for l in lessons:
 with wave.open(str(assets/l['audio'])) as w:
  assert w.getframerate()==16000 and w.getnchannels()==1 and w.getsampwidth()==2
  pcm=np.frombuffer(w.readframes(w.getnframes()),np.int16).astype(np.float32)/32768
 up=np.repeat(pcm,3).reshape(-1,3);next_sample=np.r_[pcm[1:],pcm[-1]];up[:,1]=(pcm*2+next_sample)/3;up[:,2]=(pcm+next_sample*2)/3
 sp,mask=spec.run(None,{'waveform':up.reshape(1,-1),'duration':np.array([len(pcm)/16000],np.float32)})
 t=templates[l['id']];tokens=np.array([t['tokens']],np.int64);sim=model.run(['similarities'],{'spectrogram':sp,'tokens':tokens,'maskT':mask,'maskN':tokens!=0})[0]
 refs[l['id']]={'spans':_decode_flat_single(sim[0],.5,np.r_[True,np.diff(t['groups'])!=0,True]).tolist()}
(assets/'reference-segments.json').write_text(json.dumps(refs,separators=(',',':')))
print('Exported models, verified templates and reference alignments for',len(refs),'courses; rerun tests/test-tifa.sh before release.')
