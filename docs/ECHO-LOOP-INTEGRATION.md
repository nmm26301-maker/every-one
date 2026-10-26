# 五步听说训练（Everyone 5.0.23）

首页点 **英语 · 五步听说训练**。当前英语阶段按 8 单元、每单元 3 句推进；也可从学习路线自由选课。

1. **精听**：完整听内置示范，再查看文本核对词义。支持 0.78 倍速。
2. **跟读**：看原句录音，完整回听自己的声音后继续。
3. **盲听**：英文和中文均隐藏，完整听完后先回想意思，再显示文本核对；没听懂可返回精听。
4. **复述**：按中文和场景提示脱稿录音，回听后自评。想不起来会安排 10 分钟后复习并回到精听。
5. **复习**：成功回忆按现有 1 / 3 / 7 / 14 / 30 天间隔安排；首页和复习页显示到期句子。提前重复不会跳级。

只保存已完成的步骤；退出、切句、旋转重建后，当前未完成步骤需重新播放或录音。临时录音离开页面即清除，进度和复习日期包含在学习记录备份中。跟读和复述强制使用本地录音回听，不改动用户原有纠音服务设置，不上传录音，也不需要账号、模型或 API。录音有声音、自己认为听懂均不代表自动发音测评通过。自动音素纠音仍是本单元的独立选项。

内置 48 单元共 144 条合成示范，约 1.53 MB。它们帮助基本课程在缺少系统英语语音时仍可练习；更多说话人、不同口音和真实语速仍需额外音视频输入。自由文本、AI 回复、粤语及日语示范继续使用相应原有功能和语音服务。

## 上游核对与接入范围

核对日期：2026-10-26。参考 [echo-loop/Echo-Loop](https://github.com/echo-loop/Echo-Loop)，读取时 main 为 `68334adab38adbc52c23e6638a7e0c92ef10012e`，最近推送 2026-10-25，pubspec 版本 1.0.43。

| 项目 | 参考设计 | 依赖 / 许可证 | 当前接入决策 |
| --- | --- | --- | --- |
| Echo-Loop | 精听、跟读、盲听、复述、间隔复习；播放会话代次隔离，取消后忽略迟到回调 | AGPL-3.0；Flutter/Dart、Riverpod、just_audio/media_kit、Drift/SQLite、FSRS、Sherpa-ONNX，另有账号及分析 SDK | 借鉴公开训练流程，在现有 Java Activity 和学习记录中独立实现，未复制上游代码、资源、品牌图标或引入 Flutter / 账号 / 分析 SDK |
| Piper | 构建时生成确定课程文本的离线示范 | piper-tts 1.8.0 / GPL-3.0；ONNX Runtime；LJ Speech 高质量英语单人声音 | 仅在构建环境合成 MP3；引擎和约 114 MB 声音模型不装入 APK。个别语音模型需单独核查数据集许可证，本次 LJ Speech 模型卡标为 public domain |

具体阅读：[README](https://github.com/echo-loop/Echo-Loop/blob/68334adab38adbc52c23e6638a7e0c92ef10012e/README.md)、[LICENSE](https://github.com/echo-loop/Echo-Loop/blob/68334adab38adbc52c23e6638a7e0c92ef10012e/LICENSE)、[pubspec](https://github.com/echo-loop/Echo-Loop/blob/68334adab38adbc52c23e6638a7e0c92ef10012e/pubspec.yaml)、`lib/models/study_stage.dart`、`lib/models/retell_settings.dart` 和 `lib/providers/learning_session/retell_player_provider.dart`。

上游已知限制也纳入范围判断：[issue 54](https://github.com/echo-loop/Echo-Loop/issues/54) 请求长音频切分、[issue 56](https://github.com/echo-loop/Echo-Loop/issues/56) 反馈 YouTube 链接导入、[issue 59](https://github.com/echo-loop/Echo-Loop/issues/59) 请求超过一小时云端转录；[PR 57](https://github.com/echo-loop/Echo-Loop/pull/57) 的 Kokoro 文本转课功能当时尚未合并。本版未移植这些功能，现有影视精听入口仍保留。这里也没有移植上游 FSRS、通知提醒、视频导入、AI 复述评分或付费/账号体系；五步训练使用现有自评间隔算法。

## 音频来源与重建

声音：[en_US-ljspeech-high](https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/ljspeech/high)。各段文本、包名、音频 SHA256、声音模型/config SHA256 见 `app/src/main/assets/journey/course-audio/index.json`。模型卡和声明随包保留。生成音频在我们享有权利的范围内按 CC0-1.0 提供。

```sh
python3 -m venv voice-env
voice-env/bin/pip install piper-tts==1.8.0
# 下载声音模型、同名 .onnx.json 及 MODEL_CARD 后：
ORT_DISABLE_TELEMETRY=1 voice-env/bin/python scripts/generate-course-audio.py \
  --model /path/en_US-ljspeech-high.onnx --model-card /path/MODEL_CARD
python3 tests/test-course-audio.py --decode
```

依照 [ONNX Runtime 官方隐私文档](https://github.com/microsoft/onnxruntime/blob/main/docs/Privacy.md)，生成器在导入运行时之前设置 `ORT_DISABLE_TELEMETRY=1`，并调用 `disable_telemetry_events()`；生成阶段本地运行。模型合成可能存在细微随机差异，已提交音频和 index 是发布包的权威输入。
