package com.example.healthconnectandroid.ui.medicine

import androidx.compose.runtime.Composable
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage

@Composable
internal fun medicineUiText(text: String): String =
    translateMedicineUiText(text, LocalAppLanguage.current)

fun translateMedicineUiText(text: String, language: AppLanguagePreference): String {
    if (language == AppLanguagePreference.ENGLISH || text.isBlank()) return text
    MedicineChineseText[text]?.let { return it }
    return translateMedicineStatusText(text)
}

private fun translateMedicineStatusText(text: String): String =
    when {
        text == "Medicine ready" -> "用药就绪"
        text == "Select at least one medicine" -> "至少选择一个药物"
        text == "No medicine log selected" -> "没有选择用药记录"
        text.startsWith("Medicine check failed: ") ->
            text.replaceFirst("Medicine check failed: ", "用药确认失败：")
        text.startsWith("Selected medicines: ") ->
            text.replaceFirst("Selected medicines: ", "已选药物：")
        text.startsWith("Medicine load failed: ") ->
            text.replaceFirst("Medicine load failed: ", "用药数据加载失败：")
        text.startsWith("Medicine reminder schedule failed: ") ->
            text.replaceFirst("Medicine reminder schedule failed: ", "用药提醒计划失败：")
        text == "Overlay popup enabled" -> "悬浮弹窗已开启"
        text == "Overlay popup off" -> "悬浮弹窗已关闭"
        text == "Overlay permission is needed for direct popups." -> "直接弹窗需要悬浮窗权限。"
        text.startsWith("Logged ") -> text
            .replaceFirst("Logged ", "已记录 ")
            .replace("Morning", "早上")
            .replace("Midday", "中午")
            .replace("Evening", "晚上")
            .replace("Bedtime", "睡前")
            .replace("As needed", "按需")
            .replace("taken", "已服用")
            .replace("missed", "未服用")
            .replace("skipped", "跳过")
            .replace(" at ", "，时间 ")
            .replace(" medicines", " 个药物")
            .replace(" medicine", " 个药物")
        text.startsWith("Deleted ") -> text
            .replaceFirst("Deleted ", "已删除 ")
            .replace(" medicine log rows", " 条用药记录")
            .replace(" medicine log row", " 条用药记录")
        else -> text
    }

private val MedicineChineseText = mapOf(
    "No medicine yet" to "还没有药物",
    "Add medicines from Settings." to "请从设置中添加药物。",
    "Add a medicine above." to "请在上方添加药物。",
    "Stored locally" to "保存在本地",
    "Manual" to "手动",
    "Reminder" to "提醒",
    "Medicine" to "用药",
    "Medicine schedule and quick log" to "用药计划和快速记录",
    "Today" to "今天",
    "Quick Log" to "快速记录",
    "Records the actual time you answer" to "记录你确认时的实际时间",
    "One-off medicine" to "临时药物",
    "Taken" to "已服用",
    "Cancel" to "取消",
    "Missed" to "未服用",
    "Skipped" to "跳过",
    "Current Medicines" to "当前药物",
    "Active schedule" to "当前计划",
    "Daily medicines" to "每日药物",
    "As needed medicines" to "按需药物",
    "No daily medicines" to "没有每日药物",
    "No as needed medicines" to "没有按需药物",
    "Medicine Calendar" to "用药日历",
    "Green days include at least one taken log" to "绿色日期表示至少有一次已服用记录",
    "Today Log" to "今天记录",
    "Yesterday Log" to "昨天记录",
    "Recorded locally" to "保存在本地",
    "No logs" to "没有记录",
    "No medicine log entries for this day." to "这一天没有用药记录。",
    "Delete log" to "删除记录",
    "Hide selected medicines" to "收起已选药物",
    "Adjust selected medicines" to "调整已选药物",
    "No scheduled medicine" to "没有计划药物",
    "Add a medicine in Settings, or enter a one-off medicine below." to "请在设置里添加药物，或在下方输入临时药物。",
    "Notification permission is needed for medicine checks." to "用药确认需要通知权限。",
    "Enable notifications" to "开启通知",
    "Show medicine details" to "显示药物详情",
    "Hide medicine details" to "隐藏药物详情",
    "Collapse" to "收起",
    "Expand" to "展开",
    "Add Medicine" to "添加药物",
    "Choose when this medicine is usually taken" to "选择通常服用时间",
    "Medicine name" to "药物名称",
    "Add medicine" to "添加药物",
    "Reminder Times" to "提醒时间",
    "The notification asks whether you already took it" to "通知会询问是否已经服用",
    "Manual log only" to "仅手动记录",
    "Reminder enabled" to "提醒已开启",
    "Reminder off" to "提醒已关闭",
    "Alarm mode" to "闹钟模式",
    "Uses a system alarm for this slot" to "这个时段使用系统闹钟提醒",
    "Strong popup" to "强弹窗",
    "Overlay popup enabled" to "悬浮弹窗已开启",
    "Overlay popup off" to "悬浮弹窗已关闭",
    "Overlay permission is needed for direct popups." to "直接弹窗需要悬浮窗权限。",
    "Allow overlay popup" to "允许悬浮弹窗",
    "Medicine check" to "用药确认",
    "Loading scheduled medicines" to "正在加载计划药物",
    "No scheduled medicine for this reminder." to "这个提醒没有计划药物。",
    "Review medicines" to "查看药物",
    "Archive" to "归档",
    "Archived" to "已归档",
    "Taken" to "已服用",
    "Missed" to "未服用",
    "Skipped" to "跳过",
    "Prev" to "上月",
    "Next" to "下月"
)
