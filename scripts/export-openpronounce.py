#!/usr/bin/env python3
"""Export the Apache-2.0 OpenPronounce phone checkpoint, never a word ASR model.
Pinned tooling: requirements-phone-export.txt. Float convolution + int8 MatMul.
"""
import argparse, hashlib, json, pathlib, urllib.request, time
import numpy as np
import torch
from transformers import Wav2Vec2ForCTC
from onnxruntime.quantization import quantize_dynamic, QuantType
import onnxruntime as ort

REVISION='ae45363bf3413b374fecd9dc8bc1df0e24c3b7f4'
MODEL='facebook/wav2vec2-lv-60-espeak-cv-ft'
p=argparse.ArgumentParser();p.add_argument('--checkpoint',default=MODEL);p.add_argument('--out',default='build/phone-export');a=p.parse_args()
out=pathlib.Path(a.out);out.mkdir(parents=True,exist_ok=True)
torch.set_num_threads(2)
model=Wav2Vec2ForCTC.from_pretrained(a.checkpoint,revision=REVISION,attn_implementation='eager').eval()
class Wrapper(torch.nn.Module):
    def __init__(self):super().__init__();self.model=model
    def forward(self,input_values):return self.model(input_values).logits
full=out/'phone-float.onnx';quant=out/'openpronounce-en-int8.onnx'
with torch.no_grad():
    torch.onnx.export(Wrapper().eval(),(torch.zeros(1,32000),),str(full),input_names=['input_values'],output_names=['logits'],dynamic_axes={'input_values':{1:'samples'},'logits':{1:'frames'}},opset_version=17,dynamo=False)
model.eval()  # Export restores the wrapper's training flag; comparisons must disable dropout.
quantize_dynamic(str(full),str(quant),weight_type=QuantType.QInt8,op_types_to_quantize=['MatMul','Gemm'],per_channel=True)
opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
session=ort.InferenceSession(str(quant),opts,providers=['CPUExecutionProvider'])
# Real human speech, not synthesized audio. A numerical conversion check, not learner validation.
import soundfile as sf
sample=out/'sample1.flac'
urllib.request.urlretrieve('https://cdn-media.huggingface.co/speech_samples/sample1.flac',sample)
wave,sr=sf.read(sample,dtype='float32');assert sr==16000 and wave.ndim==1
wave=wave[:128000];wave=(wave-wave.mean())/np.sqrt(wave.var()+1e-7)
with torch.no_grad(): expected=model(torch.from_numpy(wave[None])).logits.numpy()
t=time.monotonic();actual=session.run(None,{'input_values':wave[None]})[0];elapsed=time.monotonic()-t
def collapse(ids):
    return [int(x) for i,x in enumerate(ids) if x!=0 and (i==0 or x!=ids[i-1])]
from Levenshtein import distance
ref=collapse(expected[0].argmax(-1));got=collapse(actual[0].argmax(-1))
ratio=distance(ref,got)/max(1,len(ref));assert ratio<.10,(ratio,ref,got)
# Dynamic input length and finite output, including short utterances.
for n in (8000,48000):
    z=session.run(None,{'input_values':np.zeros((1,n),dtype='float32')})[0]
    assert z.shape[0]==1 and np.isfinite(z).all()
manifest={'schema':1,'filename':quant.name,'url':'https://github.com/nmm26301-maker/every-one/releases/download/openpronounce-en-v1/'+quant.name,'sha256':hashlib.sha256(quant.read_bytes()).hexdigest(),'bytes':quant.stat().st_size,'upstream_model':MODEL,'revision':REVISION,'license':'Apache-2.0','sample_rate':16000,'normalization_epsilon':1e-7,'onnx_opset':17,'quantization':'MatMul/Gemm dynamic int8; Conv float32'}
(out/'phone-model.json').write_text(json.dumps(manifest,indent=2)+'\n')
report={'real_audio':'https://cdn-media.huggingface.co/speech_samples/sample1.flac','audio_sha256':hashlib.sha256(sample.read_bytes()).hexdigest(),'seconds':len(wave)/sr,'cpu_inference_seconds':elapsed,'float_phone_ids':ref,'int8_phone_ids':got,'conversion_phone_edit_ratio':ratio,'dynamic_lengths_checked':[8000,48000],'scope':'Numerical conversion only. Not accuracy on Chinese learners or phone latency.'}
(out/'MODEL-VERIFICATION.json').write_text(json.dumps(report,indent=2)+'\n')
np.save(out/'real-audio-logits.npy',actual[0])
print(json.dumps(manifest));print(json.dumps(report))
