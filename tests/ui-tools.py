"""UI automation utilities. Use only with the disposable test emulator."""
import subprocess,pathlib,time,xml.etree.ElementTree as ET,re,shlex,json,os
root=pathlib.Path(__file__).resolve().parents[1];qa=root/'qa';qa.mkdir(exist_ok=True);checks=[]
bridge_enabled=os.environ.get('UI_BRIDGE')=='1'
def adb(*args,timeout=120):
    result=subprocess.run(['adb','-s','emulator-5580',*args],capture_output=True,timeout=timeout)
    if result.returncode:raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout
def shell(s):return adb('shell',s).decode(errors='replace')
def bounds(node):return list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
def dump():
    if bridge_enabled:
        seq=str(time.monotonic_ns());base='/data/data/org.openenglish.reader/files/ui-bridge/'
        shell('printf '+shlex.quote(seq+'\n')+' > '+base+'command')
        for attempt in range(20):
            try:
                tree=ET.fromstring(adb('exec-out','cat',base+'response.xml'))
                if tree.attrib.get('seq')==seq and list(tree):
                    (qa/'v3-ui-last.xml').write_bytes(ET.tostring(tree,encoding='utf-8'))
                    return tree
                if tree.attrib.get('seq')==seq:
                    seq=str(time.monotonic_ns())
                    shell('printf '+shlex.quote(seq+'\n')+' > '+base+'command')
            except (ET.ParseError,RuntimeError):pass
            time.sleep(.2)
        raise RuntimeError('Native UI snapshot bridge did not return a populated snapshot')
    shell('uiautomator dump /sdcard/english-ui.xml')
    tree=ET.fromstring(adb('exec-out','cat','/sdcard/english-ui.xml'))
    if any("System UI isn't responding"==n.attrib.get('text','') for n in tree.iter('node')):
        node=next(n for n in tree.iter('node') if n.attrib.get('text')=='Close app');x,y,a,b=bounds(node);shell(f'input tap {(x+a)//2} {(y+b)//2}');time.sleep(2)
        shell('uiautomator dump /sdcard/english-ui.xml');tree=ET.fromstring(adb('exec-out','cat','/sdcard/english-ui.xml'))
    return tree
def texts():return [n.attrib.get('text','') for n in dump().iter('node')]
def tap(label,contains=False,scroll=True):
    for attempt in range(6 if scroll else 2):
        for n in dump().iter('node'):
            t=n.attrib.get('text','');d=n.attrib.get('content-desc','')
            if (label in t if contains else label==t or label==d):
                x,y,a,b=bounds(n)
                if a>x and b>y:shell(f'input tap {(x+a)//2} {(y+b)//2}');time.sleep(.8);return
        if scroll:shell('input swipe 170 640 170 260 450');time.sleep(.4)
    raise AssertionError((label,texts()))
def expect(label):
    for attempt in range(3):
        observed=texts()
        if any(label in t for t in observed):checks.append(label);print('PASS',label,flush=True);return
        time.sleep(1)
    raise AssertionError((label,observed))
def shot(name):(qa/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def back():shell('input keyevent 4');time.sleep(.8)
def nav(n):tap(['今日','材料','对话','词库','我的'][n],scroll=False)
def fill(index,value):
    node=[n for n in dump().iter('node') if n.attrib.get('class')=='android.widget.EditText'][index];x,y,a,b=bounds(node);shell(f'input tap {(x+a)//2} {(y+b)//2}')
    shell('input text '+shlex.quote(value.replace(' ','%s')))
    if any('inputmethod' in n.attrib.get('package','') for n in dump().iter('node')):back()
