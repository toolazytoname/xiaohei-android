import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GRADLE = ROOT / 'app' / 'build.gradle.kts'
STORE_POLICY = ROOT / 'app' / 'src' / 'common' / 'assets' / 'xiaohei-privacy-policy.zh-CN.md'
ENHANCED_POLICY = ROOT / 'app' / 'src' / 'common' / 'assets' / 'xiaohei-privacy-policy-enhanced.zh-CN.md'
PREVIEW_DOC = ROOT / 'docs' / 'xiaohei-public-preview.md'


class PublicPreviewSourceTests(unittest.TestCase):
    def test_common_enhanced_release_variant_wiring(self):
        script = GRADLE.read_text(encoding='utf-8')
        self.assertIn('create("commonEnhancedRelease")', script)
        self.assertIn('listOf("common", "commonRelease", "commonEnhancedRelease")', script)
        self.assertIn('outputFileName = "app-common-enhanced-release.apk"', script)
        self.assertIn('applyXiaoheiEnhancedBrand()', script)
        self.assertIn('COMMON_STORE", "false"', script)
        self.assertIn('getByName("commonEnhancedRelease")', script)
        self.assertIn('useXiaoheiCommonOverlay()', script)
        self.assertIn('signingConfig = signingConfigs.findByName("commonStore")', script)
        self.assertIn('matchingFallbacks += listOf("release")', script)
        self.assertIn('listOf("commonRelease", "commonEnhancedRelease")', script)
        self.assertIn('xiaoheiPublicPreviewBuildTypes.forEach', script)
        self.assertIn('variant.packaging.jniLibs.excludes.add(path)', script)
        for name in ['libbusybox.so', 'libbash.so', 'liboperit_proot.so', 'libffmpegkit.so']:
            self.assertIn(name, script)
        self.assertIn('COMMON_PUBLIC_PREVIEW", "true"', script)
        common_block = script.split('create("common")', 1)[1].split('create("commonRelease")', 1)[0]
        self.assertIn('initWith(getByName("debug"))', common_block)
        self.assertNotIn('signingConfig', common_block)
        enhanced_block = script.split('create("commonEnhancedRelease")', 1)[1].split('create("nightly")', 1)[0]
        self.assertIn('initWith(getByName("release"))', enhanced_block)
        self.assertIn('isDebuggable = false', enhanced_block)
        self.assertIn('findByName("commonStore")', enhanced_block)
        self.assertNotIn('getByName("debug")', enhanced_block)

    def test_privacy_policies_have_confirmed_operator_and_no_placeholders(self):
        store = STORE_POLICY.read_text(encoding='utf-8')
        enhanced = ENHANCED_POLICY.read_text(encoding='utf-8')
        for document, title in (
            (store, '# 小黑普通版隐私政策（公开预览）'),
            (enhanced, '# 小黑增强版隐私政策（公开预览）'),
        ):
            self.assertIn(title, document)
            self.assertIn('韦超', document)
            self.assertIn('lazywc@gmail.com', document)
            self.assertIn('2026-10-03', document)
            self.assertIn('按明文写入', document)
            self.assertNotIn('待填', document)
            self.assertNotIn('2026-09-19', document)
            self.assertNotIn('计划排除', document)
            self.assertNotIn('已加密', document)
        self.assertIn('填写 `http://` 地址会被网络安全配置拒绝', store)
        self.assertIn('本文件不适用于增强版', store)
        self.assertIn('usesCleartextTraffic` 为 true', enhanced)
        self.assertIn('普通版政策不适用于本增强包', enhanced)
        self.assertIn('get_device_status', enhanced)
        self.assertNotEqual(store, enhanced)

    def test_preview_notes_do_not_instruct_blind_uninstall(self):
        text = PREVIEW_DOC.read_text(encoding='utf-8')
        self.assertIn('公开预览', text)
        self.assertIn('普通', text)
        self.assertIn('OnePlus 8T', text)
        self.assertIn('commonEnhancedRelease', text)
        self.assertIn('不要盲目卸载', text)
        self.assertIn('先备份', text)
        self.assertIn('不能覆盖安装', text)
        self.assertNotIn('请直接卸载', text)
        self.assertIn('麦克风', text)
        self.assertIn('DSP', text)
        self.assertIn('16KB', text)
        self.assertNotIn('sk-', text)
        self.assertNotRegex(text, r'https?://[^\s]+api[^\s]*')


if __name__ == '__main__':
    unittest.main()
