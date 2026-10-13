# Everyone 4.7.0

修复多语言流式朗读的长词截断：中文、日文或韩文前缀之后的英文词不会被按中文长度切开。长度达到阈值时，仅在 CJK 字符或空白边界分段；纯英文的完整词规则、纯中文 28 字分段及最终回调去重继续保留。

新增 12 项检查覆盖中英、日英、韩英混排、emoji、逐字累积回调和最终重复回调。55 项流式朗读检查及其余录音、取消、数据库提交边界、精确备份检查，共 220 项主机检查通过。尚未进行真实手机声学或端到端延迟测试。

当前 APK 仅替换 `StreamingSpeechChunks`，其余 1001 个编译类及 347 个模型、内容、资源和本地库项与 4.6.9 一致。系统版本为 4.7.0，versionCode 56；沿用原签名，可覆盖安装，保留学习和聊天数据。部分旧诊断标题仍可能显示 4.6.3。

源码增量同步到 `nmm26301-maker/every-one`；完整 APK、逐字节精确恢复包、原签名及恢复说明使用已授权的 Zizai 私有发布流程备份。4.6.9 发布位于 `everyone-v4.6.9`。

源码仍由 4.6.2 完整基线与恢复出的增量组成，中间版本完整 Java 源码尚未恢复。当前 APK 使用校验过的编译快照保留现有功能，普通 `build.sh` 不能视作当前完整 APK 的构建入口。`scripts/DexClassPatch.java` 要求替换 DEX 只含目标类，且基线恰含一个同名类；本轮目标为 `Lorg/openenglish/reader/StreamingSpeechChunks;`。配合 `scripts/patch-apk-version.py`、原密钥签名和 `scripts/apk-exact.py` 保存可精确恢复的已签名 APK。详见 `qa/voice470-verification.json`。
