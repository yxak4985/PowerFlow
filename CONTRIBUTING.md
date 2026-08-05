# 贡献指南

感谢你对「充放功率」感兴趣！无论是报 bug、提建议还是提交代码，都欢迎。

## 反馈问题

提 Issue 时请尽量包含：

- 手机型号与系统版本（设置 → 关于手机）
- ColorOS / Android 版本
- 问题现象描述（最好有截图）
- 是否开启了自启动 / 电池优化（ColorOS 后台限制会影响功能）

## 提交代码

1. Fork 本仓库并创建功能分支
2. 提交前请确保 `./gradlew assembleDebug` 构建通过
3. 提交 Pull Request，说明改动内容和测试情况

## 开发注意事项

- 悬浮胶囊必须使用原生 View（服务窗口没有 `ViewTreeLifecycleOwner`，ComposeView 会崩溃）
- ColorOS 对第三方应用限制较多：电流传感器返回噪声、电压广播可能异常、电量计更新慢，功率读取必须走多级兜底
- 新机型适配：在 `HealthStore` 的机型表中补充设计容量（mAh，典型值），或引导用户手动设置
- 真机测试优先在一加 / OPPO（ColorOS 16+）上进行
