# 开源离线纠音：来源、接入与边界

## 使用方法

安装 Everyone 5.0 后进入「设置、模型与备份 → 下载并安装开源纠音模型」。下载约 357 MB，页面显示进度；校验完成自动启用开源纠音。保持在本页，离开或切后台会取消下载，重试从头开始。也可从 [模型发布页](https://github.com/nmm26301-maker/every-one/releases/tag/openpronounce-en-v1) 下载 `openpronounce-en-int8.onnx`，在应用中选择「从文件导入纠音模型」。导入同样校验固定 SHA-256；模型不同版本不可混用。

在课程的跟读页面录一句，或在纠音训练室直接练 think/sink、ship/sheep 等易混音。流程是听示范 → 录音 → 看音素对照和口型提示 → 回听对应词附近录音 → 再录同一句。录音限定 0.5–12 秒。内置 144 句课程与 20 个单词/短语有固定音素参考；当前不支持任意文本离线评测。AI 对话使用独立的 Whisper/聊天模型设置。

录音只在手机本地分析，不依赖 Azure 或付费纠音 API。下载模型需要网络；手机系统 TTS 的联网行为由系统语音引擎决定。需要 Android 7+、64 位 ARM。模型推理内存需求较高，建议 6 GB 以上内存；没有真机性能实测，不承诺特定手机速度。

## 实际复用内容

| 组件 | 固定来源 | 许可证与用途 |
|---|---|---|
| OpenPronounce | [Halleck45/OpenPronounce](https://github.com/Halleck45/OpenPronounce/tree/74bc17ea406e6f057f15eea0e70951e1931af868)，commit `74bc17ea406e6f057f15eea0e70951e1931af868` | MIT；原始 `phones.py`、版权和许可证保存在 `third_party/openpronounce/` |
| 音素识别模型 | [facebook/wav2vec2-lv-60-espeak-cv-ft](https://huggingface.co/facebook/wav2vec2-lv-60-espeak-cv-ft/tree/ae45363bf3413b374fecd9dc8bc1df0e24c3b7f4) | 模型卡标注 Apache-2.0；模型卡、许可证随源码与公开模型发布保存 |
| Android 推理 | 现有 ONNX Runtime 1.22.0 | 复用旧 APK 的 Java 类和 ARM64 库，不再引入另一套运行时 |
| 参考音素 | 上游 OpenPronounce + espeak-ng | 构建时为固定练习生成；手机无需运行 Python 或 espeak |

没有把 Whisper 转写结果转换成“实际发音”。Wav2Vec2 直接读取真实 16 kHz 波形并输出帧级音素 logits。`JourneyPhones` 是 `phones.py` 的 Java 适配：保留 CTC 解码、音素规范化、允许的发音变体、近似音成本、基于后验的可信度折减和词级提示阈值。规则 JSON 从上游实际常量生成；练习参考通过上游实际函数生成。

适配差异：Java 使用有长度上限的动态规划编辑对齐，平局选择可能与 python-Levenshtein 不同；不复刻 OpenPronounce 的 ASR/TTS/DTW 综合分，也不显示未经验证的总发音分。短词只提供对照和练习建议，不把低于上游阈值的差异宣称为错误。词片段时间是 CTC 对齐估计，带前后缓冲，不能当作精确人工标注。

`JourneyPhoneEngine` 仅处理手机输入校验、模型安装、原有 ONNX Runtime 调用和取消清理。模型、识别训练和核心纠音规则均来自以上开源来源。

## 导出与验证

`scripts/export-openpronounce.py` 使用固定模型版本，导出动态长度 ONNX opset 17，对 MatMul/Gemm 做 int8 动态量化，卷积保留 float32。先保证评测模式关闭 dropout，再用真实人声比较转换前后输出。公开模型为 356,716,900 字节，SHA-256：

`fd3aa2a754a03d0693425a54fee1797ed77e64cc8c1493891949c48d6170dcac`

- `qa/MODEL-VERIFICATION.json`：8 秒真实英语人声，原模型与 int8 模型的 CTC 音素序列一致；另检查动态输入长度和有限值。
- `qa/PHONE-JAVA-VERIFICATION.json`：生产 Java 推理代码调用 ORT 1.22.0，真实音频的规范化音素序列与 Python 一致；取消后可重新运行，无效录音拒绝。
- `tests/test-phones.sh`：31 项兼容性检查；固定上游生成的参考覆盖 CTC、近似音、漏音、额外音、规范化、可信度权重、短词口型提示和不伪造分数。
- 学习流程测试继续覆盖原有课程、复习、备份与对话；原 APK 类与资源通过字节级校验保留。

模型导出环境见 `scripts/requirements-phone-export.txt`；参考生成入口为 `scripts/create-phone-references.py`。不要把主机推理耗时当作 Android 实测。

## 准确性限制

上游明确说明它是启发式方法，识别器本身会出错。在其 SpeechOcean762 基准里，被标记的词只有约五分之一同时被人工标为错音，约能发现七成被人工判错的词；这不是本应用的实测结果，也不能外推到每个中国学习者。来源：上游 README 的 limitations 和 benchmarks。

因此界面只提示“疑似差异”，不宣称确定错音、专家级评分或达到母语水平。合理口音、弱读、连读、噪声和短词都可能影响输出；无提示不等于完全正确。暂不评重音、语调和流利度。模型不确定时提示重录，不用文本相似度或人工捏造的分数替代。

最有效的使用方式是把提示当作一次有针对性的听辨和模仿练习，再用真实交流对象是否听懂来检验进步。
