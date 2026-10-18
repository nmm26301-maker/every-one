#!/usr/bin/env python3
"""Use upstream OpenPronounce's actual phonemization/normalization, once at build time."""
import importlib.util, sys, types, pathlib, json, shutil
root=pathlib.Path(__file__).resolve().parents[1]
up=pathlib.Path(sys.argv[1]).resolve()
package=types.ModuleType('openpronounce');package.__path__=[str(up/'openpronounce')];sys.modules['openpronounce']=package
from openpronounce import phones
assets=root/'app/src/main/assets/journey'
vocab=json.loads(pathlib.Path(sys.argv[2]).read_text())
rules={'upstream':'Halleck45/OpenPronounce','commit':'74bc17ea406e6f057f15eea0e70951e1931af868','vocab':[x for x,i in sorted(vocab.items(),key=lambda x:x[1])],'aliases':phones._TOKEN_ALIASES,'map':phones._PHONE_MAP['en'],'near':[sorted(x) for x in phones._NEAR_PHONES['en']]}
(assets/'phone-rules.json').write_text(json.dumps(rules,ensure_ascii=False,separators=(',',':'))+'\n')
texts=[s['en'] for u in json.loads((assets/'course.json').read_text())['lessons'] for s in u['sentences']]
texts+=['think','sink','then','den','right','light','very','berry','ship','sheep','bad','bed','sing','sin','she','see','cap','cab','about','a cup of tea']
refs={}
for text in texts:
    words,groups=phones.get_expected_phones(text)
    assert all(groups),text
    refs[text]={'words':words,'groups':groups,'alternates':[phones._pronunciations(w,g,groups[i-1][-1] if i else None) for i,(w,g) in enumerate(zip(words,groups))]}
(assets/'phone-reference.json').write_text(json.dumps(refs,ensure_ascii=False,separators=(',',':'))+'\n')
shutil.copyfile(root/'third_party/openpronounce/LICENSE',assets/'OPENPRONOUNCE-LICENSE.txt')
shutil.copyfile(root/'third_party/openpronounce/MODEL-LICENSE',assets/'PHONE-MODEL-LICENSE.txt')
print('Generated',len(refs),'fixed utterance references with upstream OpenPronounce/espeak-ng')
