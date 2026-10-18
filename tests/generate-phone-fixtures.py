"""Run against the pinned upstream checkout to create Android adaptation reference fixtures."""
import sys,types,pathlib,json,numpy as np
up=pathlib.Path(sys.argv[1]).resolve();package=types.ModuleType('openpronounce');package.__path__=[str(up/'openpronounce')];sys.modules['openpronounce']=package
from openpronounce import phones
root=pathlib.Path(__file__).resolve().parents[1];rules=json.loads((root/'app/src/main/assets/journey/phone-rules.json').read_text());vocab=rules['vocab'];vmap={p:i for i,p in enumerate(vocab)}
cases=[]
def add(name,ids,reference=None):
    logits=np.full((len(ids),len(vocab)),-12,dtype=np.float32);logits[np.arange(len(ids)),ids]=12
    logp=logits-np.log(np.exp(logits).sum(-1,keepdims=True))
    rec=phones.decode_ctc(logp,vocab)
    c={'name':name,'ids':ids,'phones':rec.phones}
    if reference:
        c['reference']=reference;c['words']=[]
        for r in phones._word_reports(rec,reference):
            edits=r['weighted_edits'];n=len(r['expected']);c['words'].append({'actual':''.join(r['actual']),'flagged':bool(r['distance'] and (edits/n>=.4 or edits>=2)),'confidence':min(1,max(edits/n,edits/2))})
    cases.append(c)
add('blank and repeated frames',[0,38,38,0,17,17,0,18,18,0],'ship')
add('short versus long vowel',[0,38,38,0,30,30,0,18,18,0],'ship')
add('s instead of dental fricative',[0,5,5,0,17,17,0,42,42,0,11,11,0],'think')
add('whole word mismatch',[0,26,26,0,14,14,0,12,12,0],'ship')
add('missing final phone',[0,38,38,0,17,17,0],'ship')
add('extra vowel',[0,38,0,17,0,18,0,7,0],'ship')
add('canonical dialect aliases',[0,vmap['ɐ'],0,vmap['ɔ'],0,vmap['ɾ'],0,vmap['ɚ'],0,vmap['ɹ'],0])
real=json.loads((root/'qa/MODEL-VERIFICATION.json').read_text())['int8_phone_ids'];ids=[]
for token in real:ids.extend([token,token,0])
add('real human CTC token sequence',ids)
(root/'tests/phone-upstream-fixtures.json').write_text(json.dumps({'upstream_commit':'74bc17ea406e6f057f15eea0e70951e1931af868','cases':cases},ensure_ascii=False,indent=2)+'\n')
