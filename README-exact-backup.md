# 原签名 APK 精确恢复

`scripts/apk-exact.py` 保存 APK 的本地 ZIP 头、压缩内容校验、目录、原签名封装和非共享内容。模型、音频与本地库从已有基线 APK 取回。恢复完成后必须通过整个文件的 SHA-256 检查，才能原子替换输出文件。

这使备份 APK 与实际交付安装包逐字节一致，而不仅是代码和版本号相同。压缩结果不同、模型改变、代码或签名尾部损坏都会停止恢复，并保留既有输出。

```bash
python3 scripts/apk-exact.py pack DELIVERED_APK RESTORE_KIT_ZIP
python3 scripts/apk-exact.py restore BASELINE_APK RESTORE_KIT_ZIP OUTPUT_APK
python3 tests/test-exact-apk.py
```

当前 4.6.8 交付文件 SHA-256：`3f3da240f6d5716ac751d42bb49ff032863d9d7fd08ec4c07ee68481c48099f0`。

基线文件和恢复包不会被覆盖。恢复包不含签名私钥或密码；原签名封装来自已签名 APK。密钥和密码恢复资料仅保存在 Zizai 私有备份。

精确恢复验证是额外的备份优化，没有新增手机端功能。中间版本的完整 Java 源码仍未恢复，相关代码通过编译快照和精确恢复包保留。
