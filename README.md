# 课表小岛（Kebiao Island）

> 大湾区大学课表安卓客户端。Kotlin + Jetpack Compose + Material 3 构建，专注"看课表 / 提醒 / 教务同步 / 学分统计"。

一个服务于大湾区大学（GBU）教务系统的课表工具：通过统一身份认证（SSO）同步**本人**课表，支持多学期历史课表查看、课程库驱动的通识选修学分统计、精确到上课时间的全屏提醒与桌面小组件。所有教务凭据仅存本机（Android Keystore 加密），不上传任何服务器。

## ✨ 功能

### 📅 周课表
- 自绘 Canvas 课表网格：列头（星期 + 日期）、行头（节次号 + 起止时间）
- 单双周过滤、今天所在列高亮、窄块字号自适应
- 点击课程色块查看详情：教师 / 教室 / 学分 / 通识选修类别（A–F）/ 周次 / 来源 / 体育项目
- 上一周 / 下一周滑动切换（带动画），手动添加课程
- **多学期切换下拉**：历史学期课表也已入库，随时切回查看

### 📆 今日视图
- 下一节课卡片（含"正在上课"高亮）与当天课程时间线
- 日期左右切换、一键回到今天；开学前可预览开学后某一天的课

### 🔐 教务同步（仅同步本人课表）
- 统一身份认证 SSO 登录（教务系统地址 + 统一认证地址均由学生手动填写，不在 App 内置任何学校域名），免验证码一键通过
- **多学期遍历同步**：自动拉取全部教学学期（秋季 / 春季），无数据的学期自动丢弃
- 以教务任务号（RWH）做变更对比，仅课表真正变化时落库并提示
- **课程库同步**：一次性拉取全校本科课程库（课程代码 → 学分 / 必修选修 / 通识类别）

### 🎓 学分统计（课程库驱动）
- 通识选修六大类（A 数学与自然科学 / B 社会科学 / C 人文科学 / D 语言与艺术 / E 个人能力与职业发展 / F 交叉学科与前沿技术）
- 本科要求：**累计选修 ≥ 12 学分且覆盖 ≥ 3 类**，自动展示进度与达标状态
- 按学期统计"本学期学分"与"本科累计学分"，跨年级跨学期通用（A–F 类别取教务课程库权威字段）

### 🔔 上课提醒
- AlarmManager 精确闹钟（Android 12+ 支持精确闹钟授权引导）
- 提前 X 分钟提醒：通知 + 声音 + 震动（普通通知铃声，非闹钟声）
- **灵动岛（Android 16 Live Updates）**：上课前后状态栏胶囊倒计时 + 上课进度条（品牌配色、可手动关闭），替代旧的全屏闹钟

### 🧩 桌面小组件
- 显示今天日期 / 教学周 / 当前·下一节课，点击跳回 App

### 💾 数据管理
- 导出 iCal / CSV 到系统文档，作为备份与双重提醒

### 🛡 隐私与安全
- 教务账号密码经 Android Keystore（AES/GCM）加密存储，仅存本机
- 教务系统地址 / 统一认证地址由学生手动填写，仅存本机，App 不内置任何学校域名
- 首次进入集中引导权限（通知 / 灵动岛常驻 / 精确闹钟 / 电池 / 安装未知来源 / 自启动）

## 🛠 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 构建 | Kotlin 2.1.20 · AGP 8.9.1 · Gradle 8.11.1（minSdk 26 / targetSdk 36） |
| UI | Jetpack Compose（BOM 2025.06.01）+ Material 3 + Navigation Compose |
| 存储 | Room 2.7.1（课表 `courses` + 课程库 `library_course`，含迁移）、DataStore Preferences |
| 网络 | OkHttp 4.12 + kotlinx.serialization（JSON） |
| 后台 | WorkManager（每日自动同步）、AlarmManager（上课提醒）、AppWidgetProvider |
| 安全 | Android Keystore AES/GCM（SecureStore） |

## 📁 项目结构

```
app/src/main/kotlin/com/gbu/classisland/
├── data/            # Room 实体/DAO/数据库、学分目录、导出、SecureStore、Settings
│   ├── credits/     # 通识选修类别与学分汇总（课程库驱动）
│   └── settings/    # DataStore 应用设置
├── edu/             # 教务接口（SSO + 课表 + 课程库）、同步仓库、课表解析
├── model/           # 节次时间表
├── navigation/      # 顶层导航（周课表 / 今日 / 设置）
├── notification/    # 通知渠道、上课提醒通知、当前课常驻通知
├── receiver/        # 开机自启重排提醒
├── reminder/        # 精确闹钟调度 + 全屏提醒界面
├── sync/            # WorkManager 每日自动同步
├── ui/
│   ├── screens/     # 周课表 / 今日 / 设置 / 新手引导
│   └── theme/       # Material 3 主题
├── util/            # 课表引擎（周次 / 节次时间 / 排序）
└── widget/          # 桌面小组件
```

## 🚀 构建

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## ⚠️ 说明

- 本项目仅用于同步与展示**本人**课表，不涉及任何批量爬取或他人数据。
- 教务接口字段为个人逆向结果，如教务系统变更可能失效，欢迎提交 Issue / PR。
- 本项目与任何同名第三方项目无关。

## 📄 开源协议

[MIT License](LICENSE)

Copyright (C) 2026 影 / Shadow / xiaole1173
