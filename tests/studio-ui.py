#!/usr/bin/env python3
"""Run on the disposable emulator after UpgradeSeed/UpgradeValidation. Does not clear data."""
import pathlib
exec(compile(pathlib.Path(__file__).with_name('ui-tools.py').read_text(),str(pathlib.Path(__file__).with_name('ui-tools.py')),'exec'))
try:
    shell('pm grant org.openenglish.reader android.permission.RECORD_AUDIO');shell('logcat -c');shell('input keyevent 82');shell('am start -n org.openenglish.reader/.StudioActivity');time.sleep(3)
    expect('让英语，成为日常');shot('v3-01-home');nav(1);expect('我的材料');shot('v3-02-library')
    # Open demo by title, using the card's study button rather than another card's.
    node=next(n for n in dump().iter('node') if n.attrib.get('text')=='One small step');x,y,a,b=bounds(node)
    shell(f'input tap 100 {b+65}');time.sleep(1);expect('SENTENCE 1 / 10');shot('v3-03-player')
    tap('▶ 播放');time.sleep(1.2);tap('Ⅱ 暂停');checks.append('actual playback start and pause')
    tap('逐句训练');tap('跟读录音与回放');tap('● 录一遍我的声音');time.sleep(1.3);tap('■ 停止录音',contains=True);expect('已保存本句录音');tap('回放我的录音');time.sleep(.5);shot('v3-04-training')
    tap('语音识别对照');expect('连接学习服务');tap('稍后');tap('完成');tap('逐句训练');tap('听写练习');fill(0,'A small habit can make a big difference.');tap('检查');expect('文字匹配');tap('关闭')
    nav(1);tap('＋ 导入音视频 / 文章');tap('粘贴英文文章');fill(0,'My first story');fill(1,'Small habits matter. Read one sentence today.');tap('保存');expect('My first story');shot('v3-05-reading')
    node=next(n for n in dump().iter('node') if n.attrib.get('text','').startswith('Small habits matter.'));x,y,a,b=bounds(node);shell(f'input swipe {x+24} {y+14} {x+24} {y+14} 1300');time.sleep(.8);shot('v3-06-selection')
    shell(f'input tap 256 {max(70,y-32)}');time.sleep(.8);expect('收藏到词库');tap('加入词库')
    tap('原文助教');tap('翻译成中文');expect('连接学习服务');tap('稍后');nav(2);expect('把英语，说出来');tap('＋ 新建一个话题');tap('咖啡店点单');expect('咖啡店点单');tap('给我一句开场');tap("I'd like a small latte, please.");shot('v3-07-chat');nav(2);tap('＋ 新建一个话题');tap('旅行与住宿');expect('旅行与住宿');nav(2);expect('咖啡店点单');expect('旧版口语对话')
    nav(4);expect('我的学习空间');shot('v3-08-mine');tap('离线常用词典',contains=True);expect('常用英汉词典');fill(0,'habit');tap('查询单词');expect('习惯');assert not any('inputmethod' in n.attrib.get('package','') for n in dump().iter('node'));shot('v3-10-dictionary');tap('返回',scroll=False);tap('学习服务与朗读设置');expect('AI 助教 / 对话');tap('真实发音评测 / Azure Speech');shot('v3-09-settings')
    shell('am force-stop org.openenglish.reader');bridge_enabled=False;shell('am start -n org.openenglish.reader/.StudioActivity');nav(1);expect('My first story');checks.append('article survived process restart')
    out=shell('am instrument -w org.openenglish.validation/.PersistenceValidation');(qa/'ui-persistence.txt').write_text(out);assert 'PASS:' in out and 'error=' not in out,out
    logs=shell('logcat -d -s AndroidRuntime');(qa/'runtime.txt').write_text(logs);assert not ('FATAL EXCEPTION' in logs and 'org.openenglish.reader' in logs),logs
    (qa/'v3-ui-checks.json').write_text(json.dumps({'passed':checks},ensure_ascii=False,indent=2));print('PASS UI flow and persistence',flush=True)
except Exception as e:
    shot('v3-ui-failure');(qa/'v3-ui-failure.txt').write_text(str(e));print('FAIL',str(e),flush=True);raise
