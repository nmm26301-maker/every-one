import json,pathlib
rows='''日常开口|Good morning!|早上好！|morning|早晨
日常开口|How are you today?|你今天怎么样？|today|今天
日常开口|Could you say that again?|你能再说一遍吗？|again|再次
日常开口|I don't understand yet.|我还没听懂。|understand|理解
日常开口|Let me try again.|让我再试一次。|try|尝试
日常开口|Nice to meet you.|很高兴认识你。|meet|见面
日常开口|What does this word mean?|这个词是什么意思？|mean|意思是
日常开口|Please speak a little slower.|请说慢一点。|slower|更慢地
日常开口|How do you spell your name?|你的名字怎么拼？|spell|拼写
日常开口|I'm still learning English.|我还在学英语。|still|仍然
通勤出行|I'm on my way to work.|我正在去上班的路上。|way|路；方式
通勤出行|I take the bus to work.|我坐公交去上班。|bus|公交车
通勤出行|The bus is coming.|公交车来了。|coming|即将到来
通勤出行|Is this seat taken?|这个座位有人吗？|seat|座位
通勤出行|I get off at the next stop.|我下一站下车。|stop|车站；停止
通勤出行|Where is the train station?|火车站在哪里？|station|车站
通勤出行|How long does it take?|需要多长时间？|take|花费；拿
通勤出行|I'd like a return ticket.|我想要一张往返票。|ticket|票
通勤出行|Which platform should I go to?|我应该去哪个站台？|platform|站台
通勤出行|I think I'm lost.|我想我迷路了。|lost|迷路的
咖啡点单|I'd like a coffee, please.|我想要一杯咖啡。|coffee|咖啡
咖啡点单|Can I have it without sugar?|可以不加糖吗？|sugar|糖
咖啡点单|For here or to go?|在这里用还是带走？|go|走；去
咖啡点单|To go, please.|请帮我打包。|please|请
咖啡点单|Could I see the menu?|可以给我看一下菜单吗？|menu|菜单
咖啡点单|What do you recommend?|你推荐什么？|recommend|推荐
咖啡点单|I'm allergic to nuts.|我对坚果过敏。|allergic|过敏的
咖啡点单|Could we have the bill, please?|请给我们账单好吗？|bill|账单
咖啡点单|This tastes really good.|这个很好吃。|taste|尝起来
咖啡点单|Can I pay by card?|可以刷卡吗？|pay|付款
旅行住宿|I have a reservation.|我有预订。|reservation|预订
旅行住宿|I'd like to check in.|我想办理入住。|check in|办理入住
旅行住宿|What time is check-out?|几点退房？|check-out|退房
旅行住宿|Is breakfast included?|包含早餐吗？|included|包含在内的
旅行住宿|Could I have another towel?|可以再给我一条毛巾吗？|towel|毛巾
旅行住宿|The Wi-Fi isn't working.|无线网络不能用。|working|正常运转的
旅行住宿|Could you help me with this?|可以帮我处理一下这个吗？|help|帮助
旅行住宿|I'd like a quiet room.|我想要一个安静的房间。|quiet|安静的
旅行住宿|Where can I leave my luggage?|我可以把行李寄存在哪里？|luggage|行李
旅行住宿|Could you call a taxi for me?|可以帮我叫一辆出租车吗？|taxi|出租车
我的生活|I like listening to music.|我喜欢听音乐。|music|音乐
我的生活|I'm learning little by little.|我在一点一点地学习。|little by little|逐渐地
我的生活|I can practice for five minutes.|我可以练五分钟。|practice|练习
我的生活|I want to tell my own story.|我想讲自己的故事。|story|故事
我的生活|I usually cook at home.|我通常在家做饭。|usually|通常
我的生活|I went for a walk yesterday.|我昨天散步了。|yesterday|昨天
我的生活|I'm a little tired today.|我今天有点累。|tired|疲倦的
我的生活|I feel better now.|我现在感觉好些了。|better|更好的
我的生活|I have a cat at home.|我家里养了一只猫。|cat|猫
我的生活|I enjoy taking photos.|我喜欢拍照。|enjoy|喜欢；享受
学习工作|Could you explain this part?|你能解释一下这一部分吗？|explain|解释
学习工作|I have a question.|我有一个问题。|question|问题
学习工作|Let me write that down.|让我把它记下来。|write down|记下
学习工作|When is the deadline?|截止日期是什么时候？|deadline|截止日期
学习工作|I'll send you the file.|我会把文件发给你。|file|文件
学习工作|Can we discuss it tomorrow?|我们可以明天讨论吗？|discuss|讨论
学习工作|I agree with your idea.|我赞同你的想法。|agree|赞同
学习工作|Could you give me an example?|你能举个例子吗？|example|例子
学习工作|I need a little more time.|我需要多一点时间。|time|时间
学习工作|Thank you for your feedback.|谢谢你的反馈。|feedback|反馈'''
items=[]
for r in rows.splitlines():
 scene,en,zh,word,meaning=r.split('|');items.append(dict(scene=scene,en=en,zh=zh,word=word,meaning=meaning))
p=pathlib.Path('app/src/main/assets/lessons.json');p.write_text(json.dumps(items,ensure_ascii=False,indent=2),encoding='utf8')
