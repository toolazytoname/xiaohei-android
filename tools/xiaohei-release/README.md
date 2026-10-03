# 小黑 APK / AAB 静态检查

```sh
python3 -m unittest discover -s tools/xiaohei-release/tests
python3 tools/xiaohei-release/verify_candidate.py \
  --apk app/build/outputs/apk/commonRelease/app-common-release.apk \
  --aab app/build/outputs/bundle/commonRelease/app-commonRelease.aab \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" --profile store --out /tmp/store.json
```

增强版用 `--profile enhanced`、对应 `app-common.apk`，不传商店 AAB。普通版要求名称“小黑”、包名 `studio.weichao.xiaohei`；增强版要求“小黑·增强”、包名 `studio.weichao.xiaohei.common`。签名工具需要Java。

检查签名、包身份、权限、备份、资源告知、原生依赖闭包。**只有store profile要求全部16KB LOAD/RELRO静态条件、非debug及商店裁剪；增强通过不等于16KB通过。** 两者都不代表设备运行、语音、数据升级、商店资格或许可完整性通过。
