"""Disposable x86 emulator UI smoke; fixture excludes ARM libs, never used as release APK."""
import subprocess,time,re,json,pathlib,xml.etree.ElementTree as E
out=pathlib.Path('release/ui');out.mkdir(parents=True,exist_ok=True);checks=[]
def adb(*a):return subprocess.check_output(['adb',*a],timeout=45)
def shell(s):return adb('shell',s).decode()
def tree():
 shell('uiautomator dump /sdcard/journey.xml >/dev/null');return E.fromstring(adb('exec-out','cat','/sdcard/journey.xml'))
def nodes():return list(tree().iter('node'))
def tap(label):
 for attempt in range(18):
  for n in nodes():
   if n.get('text')==label:
    x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
    if b-y<15:continue
    shell(f'input tap {(x+r)//2} {(y+b)//2}');time.sleep(.6);return
  width,height=map(int,re.findall(r'(\d+)x(\d+)',shell('wm size'))[-1]);shell(f'input swipe {width//2} {int(height*.82)} {width//2} {int(height*.26)} 350');time.sleep(.3)
 raise AssertionError('button not found: '+label)
def expect(fragment):
 for attempt in range(5):
  if any(fragment in n.get('text','') for n in nodes()):checks.append(fragment);return
  time.sleep(.5)
 raise AssertionError('missing '+fragment)
def shot(name):(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def home():tap('首页')
try:
 shell('logcat -c');shell('am start -n org.openenglish.reader/.JourneyActivity');time.sleep(3)
 expect('每天开口');shot('01-home');tap('开始今日课程');expect('初次见面');shot('02-lesson')
 tap('听懂这句话');expect('先听，再选意思');tap('你好，我是梅。');expect('听懂了');shot('03-listening')
 tap('进入跟读');expect('Hi, I\'m Mei.');tap('显示 / 隐藏英文');expect('英文已隐藏');shot('04-practice')
 home();tap('纠音训练室');expect('纠音训练室');shot('05-pronunciation')
 home();tap('场景交流 · AI 对话');expect('把英语用起来');shot('06-conversation');tap('帮我开场');time.sleep(2);expect('64 位 ARM');checks.append('unsupported native model fails gracefully, no fabricated reply')
 home();tap('设置、模型与备份');expect('把练习方式配置好');shot('07-settings');tap('仅录音回听 · 无自动分数');tap('保存纠音设置');expect('已保存');tap('知道了')
 home();tap('开始今日课程');tap('跟读、纠音和脱稿');expect('录音回听模式');checks.append('assessment mode persisted')
 shell('am force-stop org.openenglish.reader');shell('am start -n org.openenglish.reader/.JourneyActivity');time.sleep(2);expect('每天开口');tap('开始今日课程');tap('跟读、纠音和脱稿');expect('录音回听模式');checks.append('settings survive process restart')
 logs=shell('logcat -d -s AndroidRuntime');(out/'runtime.txt').write_text(logs);assert 'FATAL EXCEPTION' not in logs,logs
 (out/'checks.json').write_text(json.dumps(dict(passed=checks,fixture='x86 UI only; ARM libs omitted',real_audio=False,live_cloud=False),ensure_ascii=False,indent=2));print('PASS',len(checks),'UI observations')
except Exception:
 shot('failure');(out/'failure-log.txt').write_text(shell('logcat -d -s AndroidRuntime'));raise
