# Everyone — 可选 SenseVoiceSmall 粤语转写服务

Everyone 5.0.6 可以**选择性**调用你自己部署的 SenseVoiceSmall 服务。默认关闭，App **不包含模型权重**、不连接任何预设服务器。此服务把录好的 AAC/M4A 转写成粤语文字，不是声母、韵母、六声调的正确率评分。

开源上游：[SenseVoice](https://github.com/QwenAudio/SenseVoice) / [FunASR](https://github.com/modelscope/FunASR)。SenseVoiceSmall 可用 \`language="yue"\` 转写港式粤语口语；模型性能与商用/再分发规则以**权重模型卡**为准，不能把仓库源码的 MIT 授权当作权重许可证。本项目不再分发这些权重。

## 在个人 Windows/Linux 电脑运行

1. 安装 Python 3.10+ 和系统 FFmpeg，终端确认 \`ffmpeg -version\` 可运行。创建 Python 虚拟环境。
2. 安装上游推理依赖：

   \`\`\`bash
   python -m pip install -U funasr torch torchaudio
   \`\`\`

3. 创建随机访问令牌，保存到服务器环境变量 \`EVERYONE_ASR_TOKEN\`；至少 16 字符。**不要上传到 GitHub、发给别人或写入公开 App。**
4. 在仓库根目录运行 \`python server/sensevoice_yue.py\`。默认仅监听 \`127.0.0.1:8765\`；首次真实识别会从上游下载 SenseVoiceSmall 权重（需网络、存储、合适 CPU/RAM），之后模型可从本机缓存载入。
5. 用受你控制的 HTTPS 反向代理（如 Caddy / Nginx + 正常 TLS 证书）把 \`https://你的域名/v1/yue/transcribe\` 安全地反代到本机 \`http://127.0.0.1:8765/v1/yue/transcribe\`。服务不允许直接绑定公共地址。服务器应再配合访问控制与访问速率限制，不能仅靠公开 URL。**必须 HTTPS**，不支持明文公网 HTTP，也不支持在手机里直接填本机 \`localhost\`。
6. 手机进入「粤语入门 → 开源粤语识别」，先点「配置我的识别服务」，输入完整 HTTPS 地址和同一个令牌，然后录一次粤语句子，点「用 SenseVoice 识别」，再次确认才会发送**本次 M4A**。

Windows PowerShell 示例：

\`\`\`powershell
$env:EVERYONE_ASR_TOKEN = "在此自行生成至少16位的随机密码"
python server/sensevoice_yue.py
\`\`\`

> 只运行以上进程，手机仍无法跨设备访问电脑的 \`127.0.0.1\`；需要你自己配置 HTTPS 入口。无需云端 GPU 的配置可以尝试 CPU，但首轮加载与推理速度会因硬件差异显著变化，未做硬件性能承诺。

## API 与隐私约束

| 项目 | 行为 |
| --- | --- |
| GET /health | 返回进程健康状态，不代表模型已加载或可正常识别 |
| POST /v1/yue/transcribe | \`Content-Type: audio/mp4\`、\`Authorization: Bearer <token>\`、raw M4A body |
| 限制 | 录音 256 字节至 2 MiB，解码后 0.1 至 30 秒，FFmpeg 转码 35 秒超时 |
| 返回 | \`{"language":"yue","model":"SenseVoiceSmall","text":"..."}\` |
| 音频处理 | 原始录音和 16 kHz WAV 仅存于服务器临时目录，处理完成时移除 |
| 上传权限 | **每一次**由用户在手机主动确认，不上传原视频、字幕库或历史音频 |
| 配置存储 | 服务 URL 存本地 SharedPreferences；令牌只存在 App 当前进程内存，不落盘 |
| 异常处理 | 未经认证、超限、不支持的格式、模型不可用返回 4xx/503，App 保留原录音和本地学习功能 |

系统内置 ASR 的 \`zh-HK\` 入口依然保留，默认视频与音高走势不访问上述服务。运行 **SenseVoiceSmall 只能得到转写字词**。即使转写和参考字幕相同，也不能判定粤语声母、韵母和六声调准确；而当转写不同时，也可能是模型误识、繁简差异、说话口语变体而非用户发音错误。下一阶段的声学音素对齐应使用有参考真值、经过标注验证的专用模型。

## 开发回归验证

\`\`\`bash
python -m unittest discover -s tests -p 'test_sensevoice_yue.py' -v
bash tests/test-journey-subtitles.sh
\`\`\`

单元测试使用 mock 模型，不会下载权重、不调用云端。Android CI 单独验证 APK 编译和原签名打包；**没有完成真实部署/真机-HTTPS-服务端贯通实验**。
