package com.example.healthconnectandroid.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.example.healthconnectandroid.AppLanguagePreference

val LocalAppLanguage = compositionLocalOf { AppLanguagePreference.ENGLISH }

@Composable
fun uiText(text: String): String = translateUiText(text, LocalAppLanguage.current)

fun translateUiText(text: String, language: AppLanguagePreference): String {
    if (language == AppLanguagePreference.ENGLISH || text.isBlank()) return text
    exactChinese[text]?.let { return it }
    return translateDynamicChinese(text)
}

private fun translateDynamicChinese(text: String): String {
    exactChinese[text]?.let { return it }
    return when {
        text.startsWith("Last sync: ") ->
            text.replaceFirst("Last sync: ", "上次同步：")
        text.startsWith("Last sync ") ->
            text.replaceFirst("Last sync ", "上次同步 ")
        text.startsWith("Last synced data: ") ->
            text.replaceFirst("Last synced data: ", "上次同步数据：")
        text.startsWith("Range ") ->
            text.replaceFirst("Range ", "范围 ")
        text.startsWith("Range: ") ->
            text.replaceFirst("Range: ", "范围：")
        text.startsWith("Loaded: ") ->
            text.replaceFirst("Loaded: ", "已加载：")
        text.startsWith("Selected sync: ") ->
            text.replaceFirst("Selected sync: ", "所选同步：")
        text.startsWith("Chart view: ") ->
            text.replaceFirst("Chart view: ", "图表视图：")
        text.startsWith("Rows: ") ->
            text.replaceFirst("Rows: ", "行数：")
        text.startsWith("Chart points: ") ->
            text.replaceFirst("Chart points: ", "图表点数：")
        text.startsWith("Current: ") ->
            text.replaceFirst("Current: ", "当前：")
        text.startsWith("Device ") ->
            text.replaceFirst("Device ", "设备 ")
        text.startsWith("Record type: ") ->
            text.replaceFirst("Record type: ", "记录类型：")
        text.startsWith("Time: ") ->
            text.replaceFirst("Time: ", "时间：")
        text.startsWith("Recorded resting HR: ") ->
            text.replaceFirst("Recorded resting HR: ", "记录的静息心率：")
        text.startsWith("Bucket value for ") ->
            text.replaceFirst("Bucket value for ", "分组值：")
        text.startsWith("Age ") ->
            text.replaceFirst("Age ", "年龄 ")
                .replace(" from DOB", "，来自出生日期")
        text.startsWith("Last upload: ") ->
            text.replaceFirst("Last upload: ", "上次上传：")
                .replace("never", "从未")
        text.startsWith("Periodic sync is ") ->
            text.replaceFirst("Periodic sync is ", "周期同步")
                .replace("enabled. Last run: ", "已启用。上次运行：")
                .replace("disabled. Last run: ", "已关闭。上次运行：")
                .replace("never", "从未")
                .replace("no run yet", "尚未运行")
        text.startsWith("Load failed: ") ->
            text.replaceFirst("Load failed: ", "加载失败：")
        text.startsWith("Sync failed: ") ->
            text.replaceFirst("Sync failed: ", "同步失败：")
        text.startsWith("Selected sync failed: ") ->
            text.replaceFirst("Selected sync failed: ", "所选同步失败：")
        text.startsWith("Selected sync inserted data") ->
            text.replaceFirst("Selected sync inserted data", "所选同步已写入数据")
        text.startsWith("Selected sync found no source data") ->
            text.replaceFirst("Selected sync found no source data", "所选同步未读取到源数据")
        text.startsWith("Selected sync found no new data") ->
            text.replaceFirst("Selected sync found no new data", "所选同步没有新数据")
        text.startsWith("Selected sync skipped") ->
            text.replaceFirst("Selected sync skipped", "所选同步已跳过")
        text.startsWith("Selected sync timed out") ->
            text.replaceFirst("Selected sync timed out", "所选同步超时")
        text.startsWith("Selected sync cancelled") ->
            text.replaceFirst("Selected sync cancelled", "所选同步已取消")
        text.startsWith("Syncing ") ->
            text.replaceFirst("Syncing ", "正在同步 ")
        text.startsWith("Fetching ") ->
            text.replaceFirst("Fetching ", "正在读取 ")
        text.startsWith("Fetched ") ->
            text.replaceFirst("Fetched ", "已读取 ")
        text.startsWith("Preparing ") ->
            text.replaceFirst("Preparing ", "正在准备 ")
        text.startsWith("Choose ") ->
            text.replaceFirst("Choose ", "选择 ")
        text.startsWith("Testing ") ->
            text.replaceFirst("Testing ", "正在测试 ")
        text.startsWith("Uploading ") ->
            text.replaceFirst("Uploading ", "正在上传 ")
        text.contains(" complete: types ") ->
            replaceCommonTokens(text)
                .replace("Smart sync complete", "智能同步完成")
                .replace("Full resync complete", "完整重同步完成")
                .replace("Background sync now complete", "后台同步完成")
                .replace("Sync complete", "同步完成")
        text.matches(Regex("\\d+ of \\d+ supported data permissions granted; \\d+ missing")) ->
            text.replace(" of ", "/")
                .replace(" supported data permissions granted; ", " 个受支持数据权限已授予；")
                .replace(" missing", " 个缺失")
        text.matches(Regex("\\d+/\\d+ access")) ->
            text.replace(" access", " 权限")
        text.matches(Regex("\\d+ types")) ->
            text.replace(" types", " 个类型")
        text.matches(Regex("\\d+ records")) ->
            text.replace(" records", " 条记录")
        text.matches(Regex("\\d+ values")) ->
            text.replace(" values", " 个值")
        text.matches(Regex("\\d+ summaries")) ->
            text.replace(" summaries", " 个汇总")
        text.matches(Regex("[\\d,]+ rows in this range")) ->
            text.replace(" rows in this range", " 行在此范围内")
        text.matches(Regex("[\\d,]+ visible samples")) ->
            text.replace(" visible samples", " 个可见样本")
        text.matches(Regex("\\d+/\\d+ rows")) ->
            text.replace(" rows", " 行")
        else -> replaceCommonTokens(text)
    }
}

private fun replaceCommonTokens(text: String): String =
    commonChineseTokens.entries.fold(text) { acc, entry ->
        acc.replace(entry.key, entry.value)
    }

private val commonChineseTokens = linkedMapOf(
    "Local date" to "本地日期",
    "Source" to "来源",
    "Sleep stage" to "睡眠阶段",
    "Sleep session" to "睡眠",
    "Record type" to "记录类型",
    "Time" to "时间",
    "rows" to "行",
    "records" to "条记录",
    "record" to "条记录",
    "types" to "个类型",
    "type" to "类型",
    "sessions" to "次睡眠",
    "session" to "次睡眠",
    "samples" to "个样本",
    "inserted" to "写入",
    "updated" to "更新",
    "duplicates" to "重复",
    "errors" to "错误",
    "failed" to "失败",
    "success" to "成功",
    "Never" to "从未",
    "Awake" to "清醒",
    "REM" to "快速眼动",
    "Light sleep" to "浅睡",
    "Deep sleep" to "深睡",
    "Sleeping" to "睡眠中",
    "Out of bed" to "离床",
    "Unknown" to "未知"
)

private val exactChinese = mapOf(
    "Dashboard" to "仪表盘",
    "Local Data" to "本地数据",
    "Data" to "数据",
    "Settings" to "设置",
    "Profile" to "个人资料",
    "Preferences" to "偏好设置",
    "Permissions" to "权限",
    "Sync" to "同步",
    "Upload" to "上传",
    "Data Settings" to "数据设置",
    "Appearance" to "外观",
    "Debug" to "调试",
    "Units, week, timezone" to "单位、周起始日、时区",
    "Health Connect access" to "Health Connect 访问权限",
    "Periodic on" to "周期同步开启",
    "Periodic off" to "周期同步关闭",
    "Server upload" to "服务器上传",
    "Exports and local data" to "导出和本地数据",
    "Mode and palette" to "模式和配色",
    "Legacy tools" to "旧版工具",
    "Periodic, smart, and full" to "周期、智能与完整同步",
    "Server destination" to "服务器目标",
    "Counts and ranges only" to "仅显示计数和范围",
    "Kept for compatibility with the original demo" to "保留用于兼容原始演示",
    "Placeholder visual guide" to "占位视觉规则",
    "Developer-only local database action" to "仅开发调试的本地数据库操作",
    "Health Connect Data Sync" to "Health Connect 数据同步",
    "Local health data viewer, CSV exporter, and sync demo." to "本地健康数据查看、CSV 导出和同步工具。",
    "Background read permission is granted." to "后台读取权限已授予。",
    "Background read can be enabled in Settings." to "可在设置中启用后台读取。",
    "Manual sync is available; background read is unavailable on this device." to "可使用手动同步；此设备不支持后台读取。",
    "Use the bottom tabs for Data and Settings. Full resync and exports live in Settings." to "使用底部标签进入数据和设置。完整重同步和导出位于设置中。",
    "Local records are loading." to "本地记录加载中。",
    "no sync yet" to "尚未同步",
    "Data access ready" to "数据访问就绪",
    "Records" to "记录",
    "Types" to "类型",
    "Local SQLite" to "本地 SQLite",
    "With local data" to "已有本地数据",
    "Current Status" to "当前状态",
    "Last sync and access health" to "同步与访问状态",
    "Actions" to "操作",
    "Scheduled" to "已计划",
    "Off" to "关闭",
    "Background ready" to "后台就绪",
    "Manual only" to "仅手动",
    "not available on this device" to "此设备不可用",
    "available and granted" to "可用且已授权",
    "available, permission missing" to "可用，但缺少权限",
    "Working..." to "处理中...",
    "Disable Periodic" to "关闭周期同步",
    "Enable Periodic" to "开启周期同步",
    "Run Now" to "立即运行",
    "Cancel Full Resync" to "取消完整重同步",
    "Full resync reads from the full historical floor to now and can be slow. Periodic sync uses WorkManager smart sync; Android may delay it, so it is not real-time." to "完整重同步会从历史起点读取到现在，可能较慢。周期同步使用 WorkManager 的智能同步；Android 可能延后执行，因此不是实时同步。",
    "Smart Sync" to "智能同步",
    "Syncing..." to "同步中...",
    "Full Resync" to "完整重同步",
    "Full history" to "完整历史",
    "In Range" to "范围内",
    "Total Local" to "本地总数",
    "All ranges" to "全部范围",
    "Overview" to "概览",
    "Current selected range" to "当前选择范围",
    "Time Range" to "时间范围",
    "Date" to "日期",
    "Raw" to "原始",
    "Hourly" to "每小时",
    "Daily" to "每日",
    "Weekly" to "每周",
    "Monthly" to "每月",
    "Y" to "Y",
    "Showing" to "显示范围",
    "Range debug" to "范围调试",
    "Sync This Data Type" to "同步此数据类型",
    "Quick Sync" to "快速同步",
    "Sync all" to "同步全部",
    "Cancel Sync" to "取消同步",
    "Export" to "导出",
    "Loading" to "加载中",
    "Loading local data..." to "正在加载本地数据...",
    "Loading local cache" to "正在加载本地缓存",
    "Local cache" to "本地缓存",
    "Refresh local data" to "刷新本地数据",
    "Back" to "返回",
    "No data" to "无数据",
    "No data points available for this chart." to "此图表没有可用数据点。",
    "No numeric measurements available for this chart." to "此图表没有可用的数值测量。",
    "No chart for this type." to "此类型没有图表。",
    "No local rows in selected range" to "所选范围内没有本地行",
    "No additional fields for this record" to "此记录没有更多字段",
    "No sessions in this range" to "此范围内没有睡眠记录",
    "No sleep sessions in this range" to "此范围内没有睡眠记录",
    "No stage blocks available for this sleep session" to "此睡眠记录没有可用阶段块",
    "No recent data" to "近期无数据",
    "Has data" to "有数据",
    "Needs access" to "需要授权",
    "Grant in Settings" to "在设置中授权",
    "Ready" to "就绪",
    "Planned" to "计划中",
    "Granted" to "已授权",
    "Missing" to "缺失",
    "Unsupported" to "不支持",
    "Platform HR ready" to "平台心率已就绪",
    "Platform HR missing" to "缺少平台心率权限",
    "Platform HR Ready" to "平台心率已就绪",
    "Request Platform HR" to "请求平台心率权限",
    "Grant Background" to "授予后台权限",
    "App Settings" to "应用设置",
    "Time-series chart" to "时间序列图",
    "Trend chart" to "趋势图",
    "Daily totals" to "每日合计",
    "Sleep sessions" to "睡眠记录",
    "Measurement list" to "测量列表",
    "Raw table" to "原始表",
    "Heart rate" to "心率",
    "Sleep session" to "睡眠",
    "Steps" to "步数",
    "Distance" to "距离",
    "Total calories" to "总热量",
    "Active calories" to "活动热量",
    "Weight" to "体重",
    "Body fat" to "体脂",
    "Resting heart rate" to "静息心率",
    "Oxygen saturation" to "血氧",
    "Blood pressure" to "血压",
    "Body temperature" to "体温",
    "Respiratory rate" to "呼吸频率",
    "Vitals" to "生命体征",
    "Activity" to "活动",
    "Body" to "身体",
    "Sleep" to "睡眠",
    "Other" to "其它",
    "Not set" to "未设置",
    "Female" to "女性",
    "Male" to "男性",
    "Other / Prefer not to say" to "其它 / 不愿透露",
    "Date of birth" to "出生日期",
    "Sex" to "性别",
    "Age" to "年龄",
    "Weight (kg)" to "体重 (kg)",
    "Optional" to "可选",
    "Use YYYY-MM-DD, not a future date." to "使用 YYYY-MM-DD，不能是未来日期。",
    "Enter 20-350 kg." to "请输入 20-350 kg。",
    "Saved locally" to "保存在本地",
    "Edit" to "编辑",
    "Cancel" to "取消",
    "Review Access" to "查看权限",
    "Grant Access" to "授予权限",
    "Local read access" to "本地读取权限",
    "Local viewing and export" to "本地查看与导出",
    "Why We Access Health Data" to "为什么访问健康数据",
    "Health Connect read permissions are used only for this app's demo data flow" to "Health Connect 读取权限仅用于此应用的数据演示流程",
    "The app can read supported Health Connect records such as heart rate, sleep, steps, weight, body fat, oxygen saturation, calories, distance, blood pressure, temperature, respiratory rate, and resting heart rate." to "应用可以读取受支持的 Health Connect 记录，例如心率、睡眠、步数、体重、体脂、血氧、热量、距离、血压、体温、呼吸频率和静息心率。",
    "Data is stored locally in this app's database for inspection, charting, CSV export, and manual or periodic sync demos. The app does not write Health Connect data." to "数据会存储在此应用的本地数据库中，用于查看、绘图、CSV 导出以及手动或周期同步演示。应用不会写入 Health Connect 数据。",
    "Exports and uploads are user-controlled app actions. Health data is not shared automatically from this disclosure screen." to "导出和上传都由用户主动控制。健康数据不会从此说明页面自动共享。",
    "Production URL" to "生产 URL",
    "Local URL" to "本地 URL",
    "API key" to "API 密钥",
    "Upload Status" to "上传状态",
    "Pending local rows" to "待上传本地行",
    "All" to "全部",
    "Past month" to "过去一个月",
    "Past week" to "过去一周",
    "Endpoint ready" to "端点就绪",
    "Production" to "生产",
    "Local debug" to "本地调试",
    "Test" to "测试",
    "Uploading..." to "上传中...",
    "Testing..." to "测试中...",
    "Mode" to "模式",
    "Palette" to "配色",
    "Paper" to "纸面",
    "Rain" to "雨幕",
    "Milk" to "奶茶",
    "Hoodie" to "连帽衫",
    "Sage" to "鼠尾草",
    "Soft paper and teal" to "柔和纸面与青绿色",
    "Blue-gray glass" to "蓝灰玻璃感",
    "Cream and tea warmth" to "奶油与茶色暖感",
    "Soft gray-brown" to "柔和灰棕",
    "Oatmeal and green" to "燕麦色与绿色",
    "Export CSV" to "导出 CSV",
    "Export ZIP" to "导出 ZIP",
    "Export HR CSV" to "导出心率 CSV",
    "Sync 6h" to "同步 6 小时",
    "Sync 24h" to "同步 24 小时",
    "Remove Local Data" to "移除本地数据",
    "Matrix Gesture" to "矩阵手势",
    "Detail Query" to "详情查询",
    "No event" to "无事件",
    "Type" to "类型",
    "Action" to "操作",
    "Delta / snap" to "位移 / 吸附",
    "Selected" to "已选择",
    "Scroll preserved" to "滚动已保持",
    "Yes" to "是",
    "No" to "否",
    "Legacy Heart-Rate Tools" to "旧版心率工具",
    "Smoke-Test Diagnostics" to "真机冒烟测试诊断",
    "Sleep Scoring" to "睡眠评分",
    "Cache Management" to "缓存管理",
    "Hide Diagnostics" to "隐藏诊断",
    "Show Diagnostics" to "显示诊断",
    "Query heart rate at a specific time" to "查询指定时间的心率",
    "Get HR" to "获取心率",
    "Requires platform and Health Connect heart-rate access." to "需要平台和 Health Connect 心率访问权限。",
    "Sleep tags and quality colors currently use simple duration, nap, and extreme stage-churn rules only. They are not medical advice or a validated sleep score." to "睡眠标签和质量颜色目前只使用简单的时长、小睡和极端阶段变动规则。它们不是医疗建议，也不是经过验证的睡眠评分。",
    "Removing local data clears app rows and sync history. Health Connect data is not deleted." to "移除本地数据会清除应用行和同步历史。Health Connect 数据不会被删除。",
    "Remove Local Data clears this app's cached records, summaries, and sync history. Health Connect data is not deleted." to "移除本地数据会清除此应用缓存的记录、汇总和同步历史。Health Connect 数据不会被删除。",
    "Remove local data?" to "移除本地数据？",
    "This removes cached records, aggregates, legacy heart-rate rows, and sync history from this app. Health Connect data itself is not deleted." to "这会从应用中移除缓存记录、聚合、旧版心率行和同步历史。Health Connect 数据本身不会被删除。",
    "Remove" to "移除",
    "Week" to "周",
    "Month" to "月",
    "Today" to "今天",
    "Latest" to "最新",
    "Last" to "上次",
    "Save" to "保存",
    "System" to "系统",
    "Custom" to "自定义",
    "English" to "English",
    "Simplified Chinese" to "简体中文",
    "Display only" to "仅影响显示",
    "Week starts on" to "每周开始于",
    "Units" to "单位",
    "Timezone" to "时区",
    "Language" to "语言",
    "Timezone ID" to "时区 ID",
    "System timezone" to "系统时区",
    "Custom timezone" to "自定义时区",
    "Sunday" to "周日",
    "Monday" to "周一",
    "Metric" to "公制",
    "Imperial" to "英制",
    "Use a valid IANA timezone." to "请输入有效的 IANA 时区。",
    "Preferences saved" to "偏好设置已保存",
    "Profile saved" to "个人资料已保存",
    "DOB not set" to "未设置出生日期",
    "No upload yet" to "尚未上传",
    "Preparing" to "准备中",
    "Fetching" to "读取中",
    "Storing" to "写入中",
    "Aggregating" to "汇总中",
    "Inserted data" to "已写入数据",
    "No new data" to "无新数据",
    "Complete" to "完成",
    "Complete data is available in Records." to "完整数据可在记录中查看。",
    "Fields" to "字段",
    "Values" to "值",
    "Metadata" to "元数据",
    "Raw fields" to "原始字段",
    "Show Records" to "显示记录",
    "Hide Records" to "隐藏记录",
    "Load More" to "加载更多",
    "Record list failed" to "记录列表加载失败",
    "Record load failed" to "记录加载失败",
    "Weekly summaries" to "每周汇总",
    "Estimated Resting Heart Rate (RHR)" to "估算静息心率 (RHR)",
    "Median of the lowest 10% of valid data" to "有效数据中最低 10% 的中位数",
    "Average sleep" to "平均睡眠",
    "Sessions" to "睡眠次数",
    "Typical quality" to "典型质量",
    "Last session" to "最近睡眠",
    "Most recent" to "最近",
    "Good" to "良好",
    "Fair" to "尚可",
    "Nap" to "小睡",
    "Short" to "过短",
    "Fragmented" to "碎片化",
    "Failed" to "失败",
    "Skipped" to "已跳过",
    "Cancelled" to "已取消",
    "Timed out" to "超时",
    "Running" to "运行中",
    "Selected Sync" to "所选同步",
    "Smart Sync" to "智能同步",
    "Periodic Sync" to "周期同步",
    "Legacy HR Debug" to "旧版心率调试"
)
