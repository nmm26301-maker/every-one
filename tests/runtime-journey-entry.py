"""Probe real Journey UI on a disposable emulator; does not validate ARM native engines.

Install the desired APK first. For x86, use a disposable copy without ARM libraries,
keeping classes.dex unchanged. Never use this to claim a full ARM APK runtime test.
"""
import argparse,json,pathlib,re,subprocess,time,xml.etree.ElementTree as ET

p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',default='emulator-5580');p.add_argument('--output',required=True);p.add_argument('--diagnostics',action='store_true');a=p.parse_args()
out=pathlib.Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);checks=[]
def adb(*args):
 r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,timeout=180)
 if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
 return r.stdout.decode(errors='replace')
def shell(*args):return adb('shell',*args)
def tree():
 shell('uiautomator','dump','/sdcard/journey-probe.xml')
 return ET.fromstring(adb('exec-out','cat','/sdcard/journey-probe.xml'))
def tap(label):
 for attempt in range(10):
  for n in tree().iter('node'):
   if n.get('text')==label:
    x,y,b,r=map(int,re.findall(r'-?\d+',n.get('bounds')))
    if b>x and r>y:shell('input','tap',str((x+b)//2),str((y+r)//2));time.sleep(1);return
  shell('input','swipe','180','690','180','230','250')
 raise AssertionError('Button unavailable: '+label)
def expect(text):
 for attempt in range(4):
  if any(text in n.get('text','') for n in tree().iter('node')):checks.append(text);print('PASS',text,flush=True);return
  time.sleep(1)
 raise AssertionError('Expected UI text missing: '+text)
try:
 if shell('getprop','ro.kernel.qemu').strip()!='1' or shell('getprop','ro.product.cpu.abi').strip()!='x86_64':raise RuntimeError('Use a disposable x86_64 emulator only')
 adb('logcat','-c');shell('input','keyevent','82')
 shell('am','force-stop','org.openenglish.reader');print(shell('am','start','-W','-n','org.openenglish.reader/.JourneyActivity'),flush=True)
 expect('每天开口');tap('场景交流 · AI 对话');expect('把英语用起来');expect('发送并继续对话')
 tap('帮我开场');expect('没有完成：');expect('重试上一条')
 if a.diagnostics:
  tap('首页');tap('设置、模型与备份');tap('查看最近错误');expect('java.io.IOException');expect('LocalAI');tap('知道了');tap('清除诊断');tap('查看最近错误');expect('暂无错误记录')
 log=adb('logcat','-d','-b','crash');out.with_suffix('.crash.txt').write_text(log)
 if 'FATAL EXCEPTION' in log and 'org.openenglish.reader' in log:raise AssertionError('Application crashed; see crash log')
 out.write_text(json.dumps({'checks':checks,'passed':True,'native_arm_tested':False},ensure_ascii=False,indent=2)+'\n')
except Exception:
 out.with_suffix('.failure.txt').write_text(adb('logcat','-d','-t','200'))
 out.write_text(json.dumps({'checks':checks,'passed':False,'native_arm_tested':False},ensure_ascii=False,indent=2)+'\n');raise
