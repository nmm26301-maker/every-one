# Everyone 开发状态

当前实现：5.0.13 / 72，基于完整覆盖版 5.0.12。

已完成：三语速练、影视双语字幕、粤拼听辨、英语 OpenPronounce、跟读录音导出、视频/字幕/位置恢复、中断录音次数修复。当前构建与检查结果以对应私有发布的 VERIFICATION.json、UI-CHECKS.json、INTEGRATION-CHECKS.txt 为准。

待完成：
- 真实 ARM 安卓手机上的播放、麦克风、蓝牙路由及纠音效果验收。
- 粤语声母、韵母和六声调的可靠声学纠音；SenseVoice 转写与 F0 曲线均不能替代它。
- 自动生成/翻译字幕与上下文 AI 台词解析。

后续以本文件和 GitHub 最新提交为起点，无需重复历史功能。完整更新需原签名，构建入口 scripts/build-5.0.1-immersion-snapshot.sh；不得使用预览证书覆盖原版。
