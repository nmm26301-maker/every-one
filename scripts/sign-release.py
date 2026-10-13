"""Sign with the existing private credentials without printing them."""
import argparse,os,pathlib,re,subprocess
p=argparse.ArgumentParser()
for name in ['apksigner','keystore','signing_doc','input_apk','output_apk']:p.add_argument(name)
a=p.parse_args();doc=pathlib.Path(a.signing_doc).read_text();env=os.environ.copy()
for label,var in [('Store password','RELEASE_STORE_PASSWORD'),('Key password','RELEASE_KEY_PASSWORD')]:
    match=re.search(r'(?im)'+re.escape(label)+r': (.*)$',doc)
    if not match:raise ValueError('Signing password label absent')
    env[var]=match.group(1).strip().strip('`')
subprocess.run([a.apksigner,'sign','--ks',a.keystore,'--ks-key-alias','androiddebugkey','--ks-pass','env:RELEASE_STORE_PASSWORD','--key-pass','env:RELEASE_KEY_PASSWORD','--out',a.output_apk,a.input_apk],env=env,check=True)
