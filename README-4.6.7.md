# Everyone 4.6.7

持续收听使用固定的 8 帧预录缓冲，不再为每个静音帧创建数组。前 256 ms 音频、短回答提交、18 秒容量和静音结束规则保持逐样本一致。主机一万帧静音测试中预录环节临时分配从 10,400,000 字节降到 0；这不是手机整体速度或耗电实测。

录音空读退出、18 秒容量提醒、朗读缩写和小数保护、重复语音回调超时保护等 4.6.4–4.6.6 修复已经合并。170 项主机检查通过；真实手机声学与端到端延迟仍需实测。

## 源码和构建范围

仓库保留 4.6.2 的完整源码基线，以及恢复出的录音和流式朗读源码。4.6.3 的界面、重试和模型上下文等完整 Java 修改未从临时工作区恢复；4.6.7 保留这些功能的方式是使用已验证安装包的编译代码快照。不要把常规 `build.sh` 的基线构建结果当作本次安装包。

当前签名发行由私有备份仓库 `nmm26301-maker/Zizai` 的 `backup/everyone-4.6.7-20261013` 分支构建，保存代码覆盖包、原签名 APK、签名恢复资料、SHA256SUMS 和本仓库的确切提交。公开仓库不包含签名密钥或密码。

`python3 scripts/build-release-overlay.py BASE_APK CODE_OVERLAY_ZIP OUTPUT_APK` 会核对每个模型、音频和本地库的 SHA-256，然后恢复当前代码和 Android 资源。覆盖包保存在私有备份中。基线资产可取自已保存的 4.6.2–4.6.6 APK，必须通过逐文件校验。随后使用 Android 35 build-tools 的 zipalign 和 `scripts/sign-release.py` 生成覆盖安装包。

## 主机检查

```bash
bash tests/test-voice-buffer.sh
bash tests/test-conversation-boundary.sh
bash tests/test-mic-capture.sh
bash tests/test-deadline-recovery.sh
bash tests/test-speech-streaming.sh
```

新增 `.github/workflows/voice-recovery.yml` 在推送和 PR 上执行这些检查。

系统安装版本为 4.6.7，versionCode 为 53。部分旧诊断标题仍使用原 4.6.3 标签；判断版本请使用系统应用信息。沿用原证书，可直接覆盖安装并保留学习及聊天数据。
