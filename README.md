# 充放功率 (PowerFlow)

> 实时显示手机充电 / 放电功率的 Android 应用，界面采用 ColorOS 16「Liquid Glass」液态玻璃风格。
>
> English: A real-time battery charge/discharge power monitor for Android, with a ColorOS 16 "Liquid Glass" UI. Works best on OPPO / OnePlus / realme devices (ColorOS 16+, Android 16), and fully supports the ColorOS Fluid Cloud / Lock Screen Island via the official Android 16 Live Updates API.

## 功能特性

- **功率实时显示**：主界面大字显示当前功率（W），同时展示电量、电压、电流、温度
- **状态栏实时显示**：Android 16+ 通过官方「实时活动」（Live Updates）把功率显示为状态栏胶囊文本（如 `1.2W`）
- **ColorOS 流体云 / 锁屏岛适配**：ColorOS 16 的流体云与 16.7+ 的锁屏岛基于 Android 16 Live Updates API，本应用按官方规范接入（`ProgressStyle` + `setRequestPromotedOngoing` + `setShortCriticalText`）
- **锁屏显示**：前台通知以公开可见度显示在锁屏；可选「锁屏胶囊」（悬浮窗带 `FLAG_SHOW_WHEN_LOCKED`）
- **悬浮胶囊**：屏幕顶部液态玻璃胶囊，可拖动、点击打开应用
- **电池健康**：通过大量采样取平均值估算当前满电容量，健康度 = 满电容量 ÷ 设计容量（采样越多越准确）
- **启动自动请求权限**：通知、悬浮窗、电池优化一键引导
- **开机自启**：开启监控后重启手机自动恢复

## 截图

> 截图待补充，欢迎贡献真机截图（放到 `docs/screenshots/` 并在本处引用）。

## 工作原理

### 功率读取（多级兜底）

ColorOS 不向第三方应用开放实时电流传感器，本应用按以下顺序读取功率：

1. **电流传感器**（`BATTERY_PROPERTY_CURRENT_NOW`）— 部分机型可直接读到
2. **电流平均值**（`BATTERY_PROPERTY_CURRENT_AVERAGE`）
3. **电量计差分估算** — 用电量计（charge counter）在一段时间内的变化量估算平均功率
4. **能量计差分估算**（`BATTERY_PROPERTY_ENERGY_COUNTER`）

功率 = 电压 × 电流。部分机型（如 OPPO Pad 5 / OPD2506）的电压广播长期不更新且单位异常，应用会使用兜底电压（充电 4.4V / 按电量查典型锂电曲线），并在界面标注「估」。

### 实时活动（Live Updates）

Android 16（API 36）+ 的通知按官方规范提升为 `PROMOTED_ONGOING`，系统（含 ColorOS 流体云引擎）会将其渲染为状态栏胶囊 / 流体云 / 锁屏岛。旧版本自动退化为普通常驻通知。

### 电池健康

满电容量通过两种方法估算，每次测量值都累加取平均（相近值去重）：

- 电量计 ÷ 当前电量 × 100（即时估算）
- 电量差分法：电量每变化 1% 用 Δ电量 ÷ Δ百分比 反推

设计容量优先级：用户手动设置 → 内置机型表 → 观测到的最大满电容量。

## 已知限制

- 部分机型放电时不提供电流数据，功率显示为「估算」，刷新周期约 1~2 分钟（受系统电量计更新频率限制）
- 应用运行时读不到系统日志（Android 对第三方应用的日志隔离），因此无法使用部分厂商私有数据源
- OPPO 流体云「卡片」形态对第三方有白名单要求；本应用走官方公开的 Live Updates 通道，状态栏胶囊、锁屏通知、悬浮胶囊不受影响，锁屏岛是否展示由系统按实时活动规则决定
- 应用无法直接绘制到系统状态栏内部；状态栏胶囊由系统实时活动渲染

## 构建

环境要求：

- JDK 17+（推荐使用 Android Studio 自带 JBR）
- Android SDK Platform 37

```bash
# Debug 包（直接安装）
./gradlew assembleDebug

# Release 包（R8 压缩 + 资源瘦身，约 1.3MB，debug key 签名）
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/`。

## 安装与 ColorOS 权限设置（OPPO / 一加 / 真我）

1. 安装 APK（Debug 签名，可直接安装）
2. 打开应用 → 按启动引导授予**通知权限**
3. 开启「悬浮胶囊」→ 授予**悬浮窗权限**
4. ColorOS 必做：允许**自启动**、关闭**电池优化**（应用内弹窗一键跳转）
5. 「锁屏显示胶囊」按需开启（部分系统会拦截锁屏悬浮窗，锁屏信息仍由通知提供）

## 项目结构

```
app/src/main/java/com/powerflow/battery/
├── MainActivity.kt           入口（启动自动请求权限）
├── PowerFlowApp.kt           Application（崩溃日志记录）
├── battery/PowerData.kt      电池采样、多级功率估算、电量计校准
├── battery/HealthStore.kt    电池健康（满电容量平均值估算）
├── service/PowerMonitorService.kt  前台服务：实时活动通知 + 悬浮胶囊 + 锁屏
├── service/BootReceiver.kt   开机自启
├── ui/MainScreen.kt          主界面（Liquid Glass 仪表盘 + 设置 + 健康卡片）
├── ui/Components.kt          极光背景 / 玻璃卡片等组件
├── ui/theme/                 Liquid Glass 主题
└── util/                     偏好设置、格式化、OPPO 设置跳转
```

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Android 16 Live Updates（`android.app.ProgressStyle` 等平台 API）
- 前台服务（`specialUse`）+ 悬浮窗 + 开机广播

## 贡献

欢迎提交 Issue 和 Pull Request，详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 开源许可

[MIT License](LICENSE)

## 致谢

- 真机适配与测试：OnePlus（ColorOS 16）与 OPPO Pad 5（OPD2506 / ColorOS 16.1）
