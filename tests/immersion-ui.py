"""UI/recording state smoke on a disposable x86 copy; no acoustic accuracy claim."""
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path('output/ui')
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'org.openenglish.reader'
checks = []


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=45)


def shell(command):
    return adb('shell', command).decode()


def nodes():
    shell('uiautomator dump /sdcard/immersion.xml >/dev/null')
    root = ET.fromstring(adb('exec-out', 'cat', '/sdcard/immersion.xml'))
    return list(root.iter('node'))


def swipe(up=True):
    width, height = map(int, re.findall(r'(\d+)x(\d+)', shell('wm size'))[-1])
    start, end = (.80, .28) if up else (.28, .80)
    shell(f'input swipe {width//2} {int(height*start)} {width//2} {int(height*end)} 260')
    time.sleep(.25)


def top():
    for _ in range(3):
        swipe(False)


def tap(label):
    for _ in range(18):
        for node in nodes():
            if node.get('text') != label:
                continue
            x, y, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
            if bottom - y < 20:
                continue
            shell(f'input tap {(x+right)//2} {(y+bottom)//2}')
            time.sleep(.5)
            return
        swipe()
    raise AssertionError('Missing button: ' + label)


def expect(text):
    for _ in range(6):
        if any(text in n.get('text', '') for n in nodes()):
            checks.append(text)
            return
        time.sleep(.4)
    raise AssertionError('Missing text: ' + text)


def shot(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))


try:
    shell('logcat -c')
    shell(f'pm grant {PACKAGE} android.permission.RECORD_AUDIO')
    shell(f'am start -n {PACKAGE}/.JourneyActivity')
    time.sleep(2)
    expect('马上开口')
    shot('01-home')
    tap('粤语 · 立即跟读')
    expect('港式粤语')
    expect('nei5 hou2')
    assert not any(n.get('text') == '选视频' for n in nodes()), 'Advanced tools should start collapsed'
    shot('02-quick-yue')
    tap('● 开始录音')
    expect('■ 停止录音')
    tap('▶ 回放')
    expect('请先点「停止录音」')
    tap('■ 停止录音')
    expect('录音完成')
    top()
    expect('本句录音 1 次')
    tap('▶ 回放')
    time.sleep(5)
    expect('回放结束')
    tap('下一句')
    expect('m4 goi1')
    tap('▶ 回放')
    expect('开始练习')
    top()
    tap('日语')
    expect('はじめまして')
    tap('英语')
    expect('Could you say that again?')
    top()
    tap('粤语')
    expect('m4 goi1')
    tap('上一句')
    expect('本句录音 1 次')
    tap('下一句')
    tap('● 开始录音')
    time.sleep(12)
    expect('自动停止')
    expect('● 开始录音')
    shot('03-recorded')
    tap('展开影视精听与高级工具')
    expect('收起高级工具')
    tap('选视频')
    # The system picker opens; returning should preserve the app state.
    shell('input keyevent KEYCODE_BACK')
    time.sleep(.7)
    top()
    tap('收起高级工具 · 返回快速练习')
    expect('展开影视精听与高级工具')
    top()
    tap('返回')
    expect('每天开口')
    tap('粤语 · 立即跟读')
    expect('m4 goi1')
    expect('本句录音 1 次')
    shell(f'am force-stop {PACKAGE}')
    shell(f'am start -n {PACKAGE}/.JourneyActivity')
    time.sleep(1)
    tap('粤语 · 立即跟读')
    expect('本句录音 1 次')
    shot('04-restarted')
    top()
    tap('返回')
    tap('开始今日课程')
    expect('初次见面')
    tap('跟读、纠音和脱稿')
    expect('开源离线纠音')
    shot('05-original-course')
    logs = shell('logcat -d -s AndroidRuntime')
    (OUT / 'runtime.txt').write_text(logs)
    assert 'FATAL EXCEPTION' not in logs
    report = dict(passed=checks, fixture='x86 UI copy; ARM libraries omitted',
                  emulator_recording_state=True, physical_device_tested=False,
                  acoustic_accuracy_tested=False, actual_video_playback_tested=False)
    (OUT / 'checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('PASS', len(checks), 'UI observations')
except Exception:
    shot('failure')
    (OUT / 'failure-log.txt').write_text(shell('logcat -d -s AndroidRuntime'))
    raise
