# Common-base system backup policy

共同版默认不参与系统云备份和设备迁移。用户主动导出/导入不在本政策内，保持现有实现。

## Manifest（仅 `src/common`）

- `android:allowBackup="false"`
- `android:fullBackupContent="@xml/common_backup_rules"`（API ≤ 30）
- `android:dataExtractionRules="@xml/common_data_extraction_rules"`（API ≥ 31）
- 上述三项用 `tools:replace` 覆盖 main 的 `true` / `@xml/backup_rules` / `@xml/data_extraction_rules`

仅 `allowBackup=false` 在部分 OEM 上仍可能做 D2D；因此 cloud-backup 与 device-transfer 两段都必须写排除规则。缺一段则该模式按系统默认全量启用。

## 排除域

官方合法 domain（[Auto Backup](https://developer.android.com/identity/data/autobackup)，2026-02-26）：`root`、`file`、`database`、`sharedpref`、`external`，以及 device-protected 的 `device_root`、`device_file`、`device_database`、`device_sharedpref`。没有 `device_external`。两份 XML 均对以上 9 项 `path="."` 排除。

main 的空样本 `backup_rules.xml` / `data_extraction_rules.xml` 不改。

## 边界

- 不覆盖应用内导出/导入。
- 不配置 Android 16 QPR2 `cross-platform-transfer`（官方要求平台匹配参数 bundleId/teamId/contentVersion，本产品没有对应配置；本轮不对该跨平台通道作运行时承诺）。
- 未做 Gradle 合并检查、未做备份/还原/OEM 实测。配置存在 ≠ 厂商备份通道已关闭。
