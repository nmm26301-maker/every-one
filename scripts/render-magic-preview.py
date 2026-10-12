"""Code-drawn design preview for the actual native v4.4 layout; not a device screenshot."""
from PIL import Image,ImageDraw,ImageFont
import math,sys
S=2;W=1320;H=1240
im=Image.new('RGB',(W*S,H*S),'#10152c');d=ImageDraw.Draw(im)
def font(n):return ImageFont.truetype(sys.argv[1],n*S)
def txt(x,y,s,n=16,c='#f5eeff'):
 for li,row in enumerate(s.split('\n')):
  xx=x*S
  for ch in row:
   if ch=='⌄':
    cx=xx/S;line(cx,y+li*(n+7)+n*.4,cx+n*.3,y+li*(n+7)+n*.7,c);line(cx+n*.3,y+li*(n+7)+n*.7,cx+n*.6,y+li*(n+7)+n*.4,c);xx+=n*.7*S;continue
   f=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',n*S) if ch in '✦✧☾◷' else font(n)
   d.text((xx,(y+li*(n+7))*S),ch,font=f,fill=c);xx+=d.textlength(ch,font=f)
def box(x,y,w,h,c='#222442',r=22):d.rounded_rectangle((x*S,y*S,(x+w)*S,(y+h)*S),radius=r*S,fill=c,outline='#45416a',width=S)
def line(x,y,a,b,c='#bca8ff',width=2):d.line((x*S,y*S,a*S,b*S),fill=c,width=width*S)
def oval(x,y,w,h,c):d.ellipse((x*S,y*S,(x+w)*S,(y+h)*S),fill=c)
def star(x,y,r,c='#acebdf'):
 p=[]
 for i in range(8):
  a=-math.pi/2+i*math.pi/4;rr=r if i%2==0 else r*.35;p.append(((x+math.cos(a)*rr)*S,(y+math.sin(a)*rr)*S))
 d.polygon(p,fill=c)
def bunny(x,y):
 oval(x,y+8,108,108,'#564272');oval(x+24,y,15,69,'#f4e9ff');oval(x+65,y-4,15,65,'#f4e9ff');oval(x+27,y+8,8,42,'#ffcbdc');oval(x+69,y+5,8,43,'#ffcbdc');oval(x+10,y+51,91,65,'#fff9ff');d.polygon([((x+16)*S,(y+57)*S),((x+62)*S,(y-16)*S),((x+86)*S,(y+57)*S)],fill='#8f77cb');oval(x+5,y+47,100,15,'#8f77cb');star(x+61,y+26,8,'#ffe5a1');oval(x+34,y+77,6,6,'#49345f');oval(x+71,y+77,6,6,'#49345f');oval(x+23,y+88,17,9,'#ffcbdc');oval(x+75,y+88,17,9,'#ffcbdc');d.arc(((x+48)*S,(y+80)*S,(x+60)*S,(y+92)*S),0,180,fill='#49345f',width=2*S);star(x-4,y+1,6);star(x+111,y+90,6)
def button(x,y,w,s,primary=False):
 box(x,y,w,48,'#b0dfd9' if primary else '#302b50',16);f=font(14);bw=d.textbbox((0,0),s,font=f)[2]/S;txt(x+(w-bw)/2,y+11,s,14,'#182039' if primary else '#ddd1ff')
def phone(x,title,sub):
 box(x-7,178,418,965,'#45416a',32);box(x,185,404,950,'#151b32',27)
 for i in range(22):star(x+16+(i*41%377),232+i*37,2,'#485a85')
 txt(x+18,209,'‹',24,'#bca8ff');txt(x+62,211,title,19);txt(x+62,243,sub,11,'#bab4d3')
def nav(x):
 box(x+1,1072,402,62,'#151b32',0)
 for i,s in enumerate(['✦ 魔法岛','♡ 收藏','◷ 足迹','英语']):txt(x+12+i*100,1094,s,12,'#bca8ff' if i==0 else '#bab4d3')
for i in range(120):star((i*127%1320),(i*97%1240),2,'#263956')
txt(66,38,'语言魔法岛 4.4',36);txt(66,99,'跟读字样核对 · 记忆星图 · 系统语音停止后，继续把话说完',17,'#bab4d3')
x=39;phone(x,'粤语 · 小小练声室','音高与节奏之外，再核对听到的字样')
box(x+16,281,372,130,'#302b50',24);txt(x+33,298,'茶餐厅的小咒语',13,'#bab4d3');txt(x+33,331,'唔該，我想飲凍奶茶。',24);txt(x+33,376,'先完整读一遍，再对照听。',13,'#bca8ff')
box(x+16,429,372,163);txt(x+33,445,'④ 小精灵的鼓励',17);txt(x+33,482,'音高与节奏仍按录音曲线反馈',13,'#bab4d3');
for i in range(27):
 yy=535+math.sin(i*.24)*12;yy2=541+math.sin(i*.24+.2)*11
 if i:line(x+33+(i-1)*11,535+math.sin((i-1)*.24)*12,x+33+i*11,yy);line(x+33+(i-1)*11,541+math.sin((i-1)*.24+.2)*11,x+33+i*11,yy2,'#9ceedd')
box(x+16,610,372,340,'#263046',24);txt(x+33,629,'⑤ 小精灵听到了什么？',18);txt(x+33,674,'课程：唔該，我想飲凍奶茶。',14);txt(x+33,711,'Whisper 听到：',13,'#bab4d3');txt(x+33,743,'唔該，我想飲凍奶茶。',20);txt(x+33,792,'识别字样与课程一致 ✦',17,'#9ceedd');txt(x+33,836,'字样一致不代表发音完全正确。',12,'#bab4d3');txt(x+33,867,'粤语可能被转成普通话字样；',12,'#bab4d3');txt(x+33,890,'不匹配不代表你读错。',12,'#bab4d3')
button(x+16,969,372,'✧ 核对刚才的跟读 · 本地 Whisper');nav(x)
x=460;phone(x,'☾ 露娜的记忆星图','查找一起说过的话，再接着聊一小句');
button(x+16,280,112,'粤语',True);button(x+140,280,112,'日语');button(x+264,280,124,'普通话');box(x+16,344,372,53,'#1c243d',14);txt(x+33,360,'奶茶',15);button(x+16,411,372,'选择场景 · 全部 / 按场景查看');txt(x+22,477,'粤语 · 茶餐厅点餐',13,'#bab4d3');txt(x+22,506,'找到 1 轮 / 已保存 8 轮',13,'#bab4d3');box(x+16,555,372,138);txt(x+33,571,'学习偏好：慢一点，练点餐。',13,'#bab4d3');button(x+31,622,342,'想让露娜记住 · 编辑学习偏好')
box(x+16,713,372,305,'#292643',24);txt(x+33,731,'茶餐厅点餐 · 10-11 08:20',12,'#bab4d3');txt(x+33,777,'你：我想飲凍奶茶。',19);txt(x+33,829,'☾ 露娜：少甜定正常甜呀？',18);button(x+31,882,342,'接回这个小故事 ✦',True);button(x+31,944,342,'复制这一轮');txt(x+24,1047,'每次展开 20 轮 · 每门最多 300 轮',12,'#bca8ff')
x=881;phone(x,'英语 · 连续对话','系统语音停止时，重播当前段并继续');
box(x+10,275,384,836,'#eff5f1',20);txt(x+27,294,'全双工 · AI 外教',25,'#173d35');txt(x+27,352,'切换到内置离线语音，继续说完…',14,'#173d35');box(x+26,397,352,56,'#d4dcda',5);txt(x+82,415,'暂停收听与回复',15,'#173d35');box(x+26,469,352,56,'#d4dcda',5);txt(x+100,487,'立即打断外教',15,'#173d35');txt(x+27,558,'你说',13,'#173d35');txt(x+27,599,"I'd like a coffee.",25,'#173d35');txt(x+27,660,'外教',13,'#173d35');txt(x+27,704,'Sure! Hot or iced?',25,'#173d35');txt(x+27,765,'系统语音失败后，由内置语音接力。',12,'#57796f');txt(x+27,795,'当前短句可能重复，后续继续朗读。',12,'#57796f');box(x+26,849,352,56,'#d4dcda',5);txt(x+82,867,'重新朗读完整回复',15,'#173d35');txt(x+27,953,'耳机可边听边说；外放可按按钮打断。',12,'#57796f');txt(x+27,999,'系统 / 蓝牙 / 回声仍需手机验证。',12,'#57796f')
txt(61,1170,'依据原生布局绘制的设计预览 · 非实机截图 · 转写、记忆与回复均为示意数据',13,'#bab4d3')
txt(61,1205,'204 三语关卡 · Jyut Dojo 学习 · Whisper / Qwen / DeepSeek · 不承诺零延迟或完整音素评分',12,'#bca8ff')
im.resize((W,H),Image.Resampling.LANCZOS).save(sys.argv[2])
