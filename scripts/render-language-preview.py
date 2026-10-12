"""Illustrative native-layout preview, explicitly not a device screenshot.
Usage: python3 scripts/render-language-preview.py /path/NotoSansCJKsc-Regular.otf /path/preview.png
"""
from PIL import Image,ImageDraw,ImageFont
import sys,math
S=2
im=Image.new('RGB',(1200*S,1230*S),'#ede6fa');d=ImageDraw.Draw(im)
def font(n):return ImageFont.truetype(sys.argv[1],int(n*S))
def txt(x,y,s,n=16,c='#342951'):
 d.text((x*S,y*S),s,font=font(n),fill=c,spacing=4*S)
def box(x,y,w,h,c='#ffffff',r=22):
 d.rounded_rectangle((x*S,y*S,(x+w)*S,(y+h)*S),radius=r*S,fill=c)
def line(x,y,x2,y2,c,width=2):d.line((x*S,y*S,x2*S,y2*S),fill=c,width=width*S)
def oval(x,y,w,h,c):d.ellipse((x*S,y*S,(x+w)*S,(y+h)*S),fill=c)
def star(x,y,r,c='#ffe5a1'):
 pts=[]
 for i in range(8):
  a=-math.pi/2+i*math.pi/4;rad=r if i%2==0 else r*.35;pts.append(((x+math.cos(a)*rad)*S,(y+math.sin(a)*rad)*S))
 d.polygon(pts,fill=c)
def bunny(x,y):
 oval(x+5,y+10,104,104,'#baa7ee');oval(x+24,y+3,15,68,'#f4e9ff');oval(x+65,y,15,65,'#f4e9ff');oval(x+28,y+12,7,38,'#ffcbdc');oval(x+69,y+9,7,40,'#ffcbdc');oval(x+12,y+54,87,65,'#fff9ff');d.polygon([((x+17)*S,(y+61)*S),((x+63)*S,(y-8)*S),((x+86)*S,(y+61)*S)],fill='#674eac');oval(x+7,y+52,95,14,'#674eac');star(x+61,y+31,8);oval(x+35,y+78,6,6,'#49345f');oval(x+70,y+78,6,6,'#49345f');oval(x+22,y+86,17,9,'#ffcbdc');oval(x+74,y+86,17,9,'#ffcbdc');d.arc(((x+49)*S,(y+82)*S,(x+60)*S,(y+94)*S),0,180,fill='#49345f',width=2*S);star(x,y+8,7);star(x+108,y+106,6)
def phone(x,title,subtitle):
 box(x-8,139,446,1016,'#cdc0e1',38);box(x,147,430,1000,'#f6f0ff',32)
 txt(x+20,169,'‹',28,'#7960ce');txt(x+66,170,title,20);txt(x+66,204,subtitle,11,'#75678b')
def button(x,y,w,s,primary=False):
 box(x,y,w,51,'#7960ce' if primary else '#eee7ff',17);f=font(14);bw=d.textbbox((0,0),s,font=f)[2]/S;txt(x+(w-bw)/2,y+13,s,14,'#ffffff' if primary else '#7960ce')
def nav(x,y):
 box(x+2,y,426,63,'#faf7ff',0)
 for i,s in enumerate(['魔法岛','♡ 收藏','足迹','英语']):txt(x+18+i*108,y+20,s,12,'#7960ce' if i==0 else '#75678b')

txt(88,42,'Everyone 4.0 · 语言魔法岛',34)
txt(89,97,'40 个离线关卡  /  魔法兔陪练  /  一次练好一点点',16,'#75678b')
x=112;phone(x,'语言魔法岛','每天三小关，让开口变得轻松')
box(x+18,242,394,240,'#9780df',26)
txt(x+37,261,'YOUR LITTLE VOICE SPELL',10,'#f0e7ff');txt(x+37,292,'用声音\n点亮小宇宙',28,'#ffffff');txt(x+37,384,'不赶进度，一次练好一点点',12,'#f7efff');bunny(x+277,295);button(x+36,414,358,'继续小冒险 · 粤语')
box(x+18,496,394,116);txt(x+36,514,'★ 今日星光  0 / 3',18);txt(x+36,550,'听一遍、跟一遍，就是一次小进步。',12,'#75678b');box(x+36,580,354,7,'#e9ddfa',3)
txt(x+18,632,'选一座喜欢的岛',21)
for yy,title,sub,count,color in [(679,'粤语 · 星月森林','六声调 → 入声 → 茶餐厅短句',12,'#e7defc'),(808,'日语 · 樱花结界','元音 → 长音 / 促音 → 生活问候',12,'#ffe3ef'),(937,'普通话 · 云朵城堡','四声 → 平翘舌 / n、l → 词句',16,'#dcefee')]:
 box(x+18,yy,394,116,color);txt(x+36,yy+16,title,20);txt(x+36,yy+52,sub,12,'#75678b');txt(x+36,yy+82,str(count)+' 个小关卡 · 已练 0  →',12,'#7960ce')
nav(x,1082)

x=657;phone(x,'粤语 · 小小练声室','01 · 高中低')
box(x+18,242,394,178,'#eee7ff');txt(x+36,259,'中平调',13,'#75678b');txt(x+36,291,'試',36);txt(x+36,346,'si3',19,'#7960ce');txt(x+36,383,'尝试',13,'#75678b')
box(x+18,434,394,161);txt(x+36,451,'① 先听小精灵示范',17);button(x+36,489,170,'▶ 听示范');button(x+216,489,178,'慢一点 · 0.8×');txt(x+36,557,'已内置 · 无需联网 · 合成示范',11,'#75678b')
box(x+18,609,394,145);txt(x+36,626,'② 这次只注意一件事',17);txt(x+36,666,'先用舒服的中音定位置，平稳送气，\n不要拖出升降。舌尖靠近下齿后，\ns 的气流细而连续。',13,'#342951')
box(x+18,768,394,239);txt(x+36,785,'③ 跟读，让曲线发光',17);box(x+36,824,358,119,'#f8f5ff',17)
for yy in [853,884,914]:line(x+53,yy,x+377,yy,'#e4daf8',1)
line(x+61,883,x+154,887,'#7960ce',3);line(x+154,887,x+235,881,'#7960ce',3);line(x+235,881,x+369,884,'#7960ce',3)
txt(x+36,954,'紫色：示范 · 薄荷色：你',11,'#75678b');txt(x+36,980,'曲线看走势；平调高低需中音校准。',11,'#75678b')
box(x+2,1014,426,67,'#faf7ff',0);button(x+18,1022,394,'● 点一下开始跟读',True);nav(x,1082)
txt(110,1178,'界面设计预览 · 根据本版原生界面绘制，非实机截图',14,'#75678b')
im.resize((1200,1230),Image.Resampling.LANCZOS).save(sys.argv[2])
