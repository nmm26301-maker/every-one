"""Curated starter pathway; levels are practice bands, never awarded certificates."""
import json,pathlib
levels=[
('零基础 · Pre-A1','建立声音与意义的联系；能借助提示完成简短回应。','能不看文字问候、介绍自己、表达一个需要；没听懂时会请求重复。'),
('起步 · A1','用常用句型处理个人信息、点餐、时间与方向。','能完成自我介绍和简单交易；能听懂慢速、清楚的提问。'),
('生活 · A2','连接多句表达，在常见生活场景中追问、说明情况。','能在旅行、购物、预约中交换信息，描述经历和计划。'),
('独立 · B1','讲述经历、表达理由，遇到意外能澄清并继续交流。','能无脚本进行熟悉话题对话，叙述经历并说明观点。'),
('深入 · B2','表达细微差异、协商、讨论抽象问题并回应不同观点。','能持续交换意见，给出论据、确认隐含意思和修复误解。'),
('进阶交流','扩展话题、语域、语速与口音适应；练习真实互动。','能根据对象调整表达、处理打断和含蓄意见，准确复述对方立场。')]
# title | reusable structure and explanation | sound focus | three paired expressions
rows='''
初次见面|I am + 名字，介绍自己；you 用来指对方。|句中重读名字；Hi 的 /aɪ/ 从开口滑向高位。|Hi, I'm Mei.~你好，我是梅。|What's your name?~你叫什么名字？|Nice to meet you.~很高兴认识你。
我来自哪里|I am from + 地点，from 表示来自。|from 轻读，重读城市名称；词尾 m 闭唇收尾。|I'm from China.~我来自中国。|Where are you from?~你来自哪里？|I live in Beijing.~我住在北京。
表达需要|I need + 名词；please 让请求更礼貌。|need 词尾 d 收住，不额外加元音。|I need some water.~我需要一些水。|Water, please.~请给我水。|Thank you very much.~非常感谢你。
是与不是|Yes / No 后加一个完整短句确认信息。|this 的 /ð/ 轻咬舌尖并振动声带。|Yes, that's right.~是的，没错。|No, thank you.~不用了，谢谢。|Is this yours?~这是你的吗？
听不懂也能沟通|Could you + 动词，礼貌请对方帮忙；不要假装听懂。|please 里的 /pl/ 连续发音，不插入元音。|Sorry, I don't understand.~抱歉，我没听懂。|Could you say that again?~你能再说一遍吗？|Please speak more slowly.~请说慢一些。
数字与价格|How much is + 物品，询问价格；数字要确认。|three 的 /θ/ 不振动声带，与 tree 区分。|One, two, three.~一、二、三。|How much is this?~这个多少钱？|It's ten dollars.~十美元。
找东西|Where is + 单个东西；here / there 表示位置。|where 的 /w/ 收圆双唇；here 的 h 轻送气。|Where is my phone?~我的手机在哪里？|It's right here.~就在这里。|Is it over there?~是在那边吗？
第一段小对话|用 and 把两个想法连起来；用简单句完成一个来回。|一句一个意群；不要每个单词都一样重。|Hi, I'm Mei, and I'm a student.~你好，我是梅，是一名学生。|I speak a little English.~我会说一点英语。|Can you help me, please?~你能帮帮我吗？
介绍自己|I work / I study 描述日常；问句用 What do you do?。|work 的 /w/ 与 v 不同；student 重读第一音节。|I work in a shop.~我在商店工作。|What do you do?~你是做什么工作的？|I study English every day.~我每天学习英语。
家人和朋友|This is + 人，介绍别人；my / your 表示所属。|brother 的 th 是浊音 /ð/；词尾 r 不卷得过重。|This is my sister.~这是我的姐姐或妹妹。|She lives near me.~她住在我附近。|Do you have any brothers?~你有兄弟吗？
一天的安排|一般现在时描述习惯；at + 时间。|get up 连读，保留节奏；eight 词尾 t 不加元音。|I get up at seven.~我七点起床。|I go to work at eight.~我八点去上班。|What time do you finish?~你几点结束？
在咖啡店|I'd like + 物品，比 I want 更适合点单。|coffee 重读第一音节；please 词尾 /z/ 带振动。|I'd like a coffee, please.~请给我一杯咖啡。|Can I have it without sugar?~可以不加糖吗？|That's all, thank you.~就这些，谢谢。
问路与确认|How do I get to + 地点；确认左右和地标。|right / light 区分 r 和 l；left 词尾 ft 连续收尾。|How do I get to the station?~去车站怎么走？|Turn left at the next street.~在下一条街左转。|Is it far from here?~离这里远吗？
购买物品|Do you have + 物品；this one 代替刚指过的东西。|size 词尾 /z/；this 和 these 元音不同。|Do you have this in blue?~这个有蓝色的吗？|Can I try it on?~我能试穿吗？|I'll take this one.~我要这一个。
约时间|Are you free + 时间；用具体时间避免含糊。|Tuesday 重读第一音节；fifteen / fifty 重音不同。|Are you free on Friday?~你星期五有空吗？|Let's meet at three.~我们三点见吧。|See you then.~到时候见。
爱好与追问|like + 动词 ing；追问 How about you? 保持互动。|swimming 的 /ŋ/ 不添加 g；like 词尾 k 收住。|I like reading and swimming.~我喜欢阅读和游泳。|What do you do for fun?~你平时喜欢做什么？|How about you?~你呢？
昨天发生了什么|过去式描述已结束的事情；注意时间词 yesterday。|worked 的 ed 是 /t/，visited 结尾是 /ɪd/。|I visited a friend yesterday.~我昨天拜访了一位朋友。|We had lunch together.~我们一起吃了午饭。|What did you do yesterday?~你昨天做了什么？
周末计划|going to + 动词，说明已有计划。|going to 可自然轻读，但先练清楚完整形式。|I'm going to visit my parents.~我打算去看父母。|What are your plans for the weekend?~你周末有什么计划？|We might go for a walk.~我们可能去散步。
酒店入住|I have a reservation；用 under + 姓名说明预订人。|reservation 主重音在 va；连读 check in。|I have a reservation under Chen.~我有预订，姓陈。|What time is breakfast?~早餐几点开始？|Could I have a quiet room?~能给我一间安静的房间吗？
餐厅里说明需求|Could I have + 需求；先说清楚不能吃什么。|allergic 重读第二音节；nuts 词尾 ts 收清。|I'm allergic to peanuts.~我对花生过敏。|Does this dish contain nuts?~这道菜含坚果吗？|Could we have the bill, please?~请给我们结账。
交通与意外|missed / next / delayed 描述交通变化；确认目的地。|train 的 /tr/ 连续发音；missed 词尾 /st/。|I think I missed my stop.~我想我坐过站了。|When is the next train?~下一班火车是什么时候？|Does this bus go to the airport?~这辆公交车去机场吗？
预约和改期|I'd like to book；Could we move it to + 新时间。|appointment 重读 point；日期与时间放慢确认。|I'd like to book an appointment.~我想预约。|Could we move it to Monday?~我们能改到星期一吗？|Is there anything available in the afternoon?~下午还有空位吗？
描述身体不适|I've had + 症状 + for + 持续时间；表达症状而非自诊。|throat 的 /θ/ 不变成 s；pain 双元音 /eɪ/。|I've had a headache since yesterday.~我从昨天开始头痛。|My throat hurts when I swallow.~我吞咽时喉咙痛。|Where is the nearest pharmacy?~最近的药店在哪里？
说明问题和退换|It doesn't work；请求解决办法并保留礼貌。|refund 作名词重读首音节；broken 重读 bro。|This doesn't seem to work.~这个好像不能正常使用。|Could I exchange it for another one?~我能换一个吗？|I bought it here yesterday.~我昨天在这里买的。
讲一段经历|先背景，再事件，最后感受；用 while / when 连接。|意群之间稍停顿，不在每个词后停。|I was walking home when it started raining.~我走回家时开始下雨。|Luckily, a friend offered me a ride.~幸运的是，一位朋友捎了我一程。|It turned out to be a memorable day.~那天结果很难忘。
表达观点和理由|I think + 观点，because + 理由，并给一个例子。|重读观点关键词；because 通常轻读。|I think working from home can save time.~我认为在家工作能节省时间。|It depends on the kind of work you do.~这取决于你做什么工作。|For example, I spend less time commuting.~例如，我花在通勤上的时间更少了。
提出建议|You could / Why don't we 提供选择，不替对方做决定。|could 的 l 不发音；建议语气保持友好。|You could try taking a short break.~你可以试着休息一会儿。|Why don't we ask someone for advice?~我们为什么不问问别人的建议呢？|What would you suggest?~你有什么建议？
澄清误会|What I meant was + 原意；用复述确认理解。|meant 的元音 /ɛ/；mean 是 /iː/。|That's not quite what I meant.~我不完全是那个意思。|Let me explain it another way.~我换一种方式解释。|Do you mean we should wait until tomorrow?~你的意思是我们应该等到明天吗？
工作进度|已完成、正在做、下一步三部分；用 by + 截止时间。|finished 的词尾 /t/；project 名词重读首音节。|I've finished the first part of the project.~我已经完成项目的第一部分。|I'm still waiting for some information.~我还在等一些信息。|I'll send you an update by Friday.~我会在星期五之前给你发进展。
自然地闲聊|从共同情境开始，追问一个细节，并分享相关经历。|how's 的 /z/ 接 your，先清楚再练自然连读。|How's your week been so far?~你这周过得怎么样？|That sounds interesting. How did you get into it?~听起来很有趣。你怎么开始接触它的？|I've always wanted to try that.~我一直想试试那个。
陌生词也能表达|用用途、外观或例子解释不会的词，避免直接停止。|thing 的 /θ/ 与 /ŋ/；停顿用于组织思路。|I don't know the word, but it's used for cooking.~我不知道这个词，但它是做饭用的。|It's similar to a small oven.~它有点像小烤箱。|What do you call that in English?~那个用英语怎么说？
面对突发变化|说明变化、影响、可行替代；明确请求确认。|changed 结尾 /dʒd/ 不增加音节。|Our plans have changed at the last minute.~我们的计划在最后一刻变了。|Would it be possible to meet online instead?~我们改成线上见面可以吗？|Let me know what works best for you.~告诉我怎样安排最适合你。
比较与权衡|On the one hand / on the other hand；比较成本和收益。|重读对比词，连接词轻读；costs 词尾不要漏掉。|The cheaper option may cost more in the long run.~便宜的方案长期来看可能更贵。|We need to weigh the benefits against the risks.~我们需要权衡收益和风险。|What matters most is whether it meets our needs.~最重要的是它是否符合我们的需求。
有分寸地不同意|先承认合理部分，再提出疑问和理由。|语调平稳，避免每句都像命令。|I see your point, but I'm not entirely convinced.~我理解你的观点，但还没有完全被说服。|My main concern is how this would work in practice.~我主要担心实际操作会怎样。|Could we look at it from another perspective?~我们能换个角度看吗？
协商与达成一致|If + 条件，we could + 方案；复述双方承诺。|条件和结论分意群；重读条件中的数量或日期。|If we extend the deadline, we could improve the quality.~如果延长截止时间，我们就能提高质量。|Would you be willing to consider a compromise?~你愿意考虑折中方案吗？|Let's agree on the next steps before we finish.~结束前我们来确认下一步。
说明复杂流程|先概述目标，再按步骤解释，检查对方是否跟上。|process 美式常读 /prɑːses/；重点是清楚的意群。|The process consists of three main stages.~这个流程包括三个主要阶段。|Once you've completed this step, you can move on.~完成这一步后，就可以继续了。|Would it help if I walked you through an example?~我带你过一个例子会有帮助吗？
媒体与信息判断|区分事实、观点与不确定性；询问信息来源。|evidence 重读首音节；claim 结尾 m 闭唇。|I'm not sure how reliable that source is.~我不确定那个来源有多可靠。|The evidence seems to support a different conclusion.~证据似乎支持另一个结论。|We should distinguish between facts and opinions.~我们应该区分事实和观点。
谈文化差异|描述个人经验，避免把一群人都说成同一种情况。|culture 重读首音节；不同口音都可以清晰可懂。|In my experience, expectations vary from place to place.~依我看，不同地方的期望有所不同。|I wouldn't want to make a generalization.~我不想一概而论。|How is that usually handled where you live?~你住的地方通常怎么处理那种情况？
反馈与建设性表达|描述具体行为和影响，再提出可执行的改变。|反馈重读行为而非指责对方的人格。|I appreciated how clearly you explained the problem.~我很欣赏你把问题解释得那么清楚。|One thing we could improve is the handover process.~我们可以改进的一点是交接流程。|Could we set aside some time to discuss this?~我们能留点时间讨论一下吗？
深入追问|用具体细节、原因和反例把对话推进一层。|问句不一定都升调，开放式提问可自然降调。|What led you to that conclusion?~是什么让你得出了那个结论？|Could you give me a concrete example?~你能给我一个具体的例子吗？|How would your view change if the situation were different?~如果情况不同，你的看法会怎样改变？
听懂隐含意思|先提出你理解的意思，让对方确认，避免猜测当事实。|重音会改变暗示；同一句换重音再听。|Are you suggesting that we should reconsider?~你是在建议我们重新考虑吗？|I get the impression that you have some reservations.~我感觉你有些顾虑。|Please correct me if I've misunderstood.~如果我理解错了，请纠正我。
插话和重新接续|礼貌进入话轮；被打断后用 returning to 回到重点。|插话用短句，自然停顿给对方回应空间。|Sorry to jump in, but could I clarify one point?~抱歉插一句，我能澄清一点吗？|Please go ahead. I'll come back to that later.~请继续。我稍后再回到那一点。|Returning to what we were discussing earlier...~回到我们刚才讨论的内容……
精确表达不确定性|区分可能、较可能与已知；不要用绝对词掩盖不确定。|potentially 主重音在 ten；probably 可自然弱化。|As far as I can tell, the situation hasn't changed.~据我判断，情况还没有变化。|That's plausible, though we don't have enough evidence yet.~那是有可能的，不过我们还没有足够证据。|I'd be hesitant to draw a firm conclusion at this stage.~我不愿在这个阶段就下定论。
讲故事的节奏|铺垫、转折、结果；省掉不必要细节并观察对方反应。|转折前稍停顿，用重音突出意外信息。|What I hadn't realized was that we'd met before.~我没想到的是我们以前见过。|Just when I thought it was over, something unexpected happened.~就在我以为结束时，意想不到的事发生了。|Looking back, that's what made the experience worthwhile.~回头看，那正是这段经历值得的原因。
调整正式程度|朋友、同事、客户用不同措辞；意思相同但语气有别。|礼貌来自措辞与语调，不必把每个词读得很慢。|Could you send me the details when you have a moment?~你方便时能把详情发给我吗？|I'd appreciate it if you could confirm receipt.~如果你能确认收到，我将不胜感激。|Just checking whether you got my message.~只是想确认你是否收到我的消息。
适应不同表达和口音|先抓关键词和意群；请求重复具体部分，而非整段重来。|清晰可懂优先于模仿单一口音；自然弱读需要反复听。|I caught the first part, but missed the last detail.~前半部分我听懂了，但漏掉了最后一个细节。|Could you rephrase the bit about the schedule?~关于日程的那一部分，你能换个说法吗？|So, if I've understood correctly, we're meeting at noon.~所以，如果我理解对了，我们中午见面。
敏感分歧中的沟通|说明自己的理解，承认差异，询问对方在意的事。|语速略放慢，留回应空间；不要靠提高音量加强观点。|I can see why this matters to you.~我理解为什么这对你很重要。|We may disagree on the approach, but we share the same goal.~我们的方法可能不同，但目标相同。|What would a fair outcome look like to you?~在你看来，什么结果才算公平？
真实交流综合任务|不看脚本：提出话题、追问、澄清、总结、约定下一步。|回听一段完整对话，只选择最影响理解的一处改进。|Let me make sure we're on the same page.~让我确认一下我们理解一致。|Is there anything important that we haven't covered?~还有什么重要的内容我们没有谈到吗？|It's been great talking with you. Let's stay in touch.~和你聊天很愉快。我们保持联系吧。
'''.strip().splitlines()
assert len(rows)==48
units=[]
for i,row in enumerate(rows):
 title,note,focus,*pairs=row.split('|')
 assert len(pairs)==3
 sentences=[dict(en=p.split('~')[0],zh=p.split('~')[1]) for p in pairs]
 units.append(dict(id='u%02d'%(i+1),level=i//8,title=title,note=note,focus=focus,sentences=sentences,task='不看英文，用自己的信息完成“'+title+'”场景；至少回应三轮，追问一次。如果没听懂，主动澄清。',scene='Role-play '+title+'. Practise these communicative functions: '+'; '.join(s['en'] for s in sentences)+'. Change names, numbers and details; do not make the learner merely repeat a script.'))
out=dict(schema=1,levels=[dict(name=n,goal=g,canDo=c) for n,g,c in levels],lessons=units)
pathlib.Path('app/src/main/assets/journey/course.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print(len(units),'lessons;',sum(len(x['sentences']) for x in units),'bilingual utterances')
