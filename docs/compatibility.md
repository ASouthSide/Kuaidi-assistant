# Android 设备兼容性

适用版本为[快递助手 2.0.1 正式侧载版](https://github.com/ASouthSide/Kuaidi-assistant/releases/tag/v2.0.1)。

## 通用功能

- APK 最低要求 Android 8.0（API 26），包含 ARM 和 x86 的 32 位与 64 位架构。包内 64 位 OCR 动态库及 APK 内对应文件偏移通过 16 KB 静态对齐检查。16 KB 设备上的运行表现待真机验证。 [Android 16 KB 兼容说明](https://developer.android.com/guide/practices/page-sizes)
- 取件管理、手动录入、粘贴短信和离线 OCR 使用通用 Android 接口。长按图标的四项快捷方式由支持 Android App Shortcuts 的桌面展示。 [Android App Shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)
- 自动收短信属于侧载版功能。安装器需允许短信权限，用户也需要授权。常驻和超期通知需要系统通知权限及对应渠道。 [Android 短信权限](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS) · [Android 通知权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)

## 九个品牌的验证状态

| 品牌 | 状态 | 实测结果或待测重点 |
| --- | --- | --- |
| 小米／Redmi | 部分真机验证 | REDMI K90 Max（HyperOS 4、Android 17）的安装包哈希与 GitHub 正式版一致，四项桌面快捷方式已注册。同源测试包曾在无运行进程时由真实短信唤起并正确入库，也观察到 HyperOS 跳过后台缓存进程的短信广播。 |
| vivo | APK 与源码审查，待真机 | 核对后台耗电管理、自启动，以及锁屏后的短信入库。 [vivo 用户手册](https://eu-exstatic-vivofs.vivo.com/8Xa6evfY85lu15Pb/1792553066674/e425de168164d225347c90e29870324a.pdf) |
| iQOO | APK 与源码审查，待真机 | OriginOS 机型沿用 vivo 的检查项，在 iQOO 上独立验证短信投递。 [iQOO 15 系统规格](https://www.iqoo.com/en/products/param/iqoo-15) |
| OPPO | APK 与源码审查，待真机 | 分别核对 ColorOS 普通模式和省电模式中的后台收信及通知。 [OPPO 省电模式说明](https://support.oppo.com/cn/answer/?aid=SI2105920) |
| 魅族 | APK 与源码审查，待真机 | 核对 Flyme 回收后台进程后的收信，以及重启解锁后恢复待取通知和超期检查。 [魅族后台耗电说明](https://mwx-page.meizu.com/1015.html) |
| 红魔 | APK 与源码审查，待真机 | 分别验证游戏空间内外的短信入库和提醒显示。游戏空间可隐藏状态栏或通知弹窗。 [红魔游戏空间说明](https://global.redmagic.gg/blogs/game-space/game-space-walkthrough-game-space-center-energy-cube-plugins-and-shoulder-trigger-customization) |
| 一加 | APK 与源码审查，待真机 | 国行 ColorOS 和海外 OxygenOS 分别检查自启动、后台收信和通知。 [一加 ColorOS](https://www.oneplus.com/cn/coloros) · [OxygenOS 用户手册](https://service.oneplus.com/content/dam/support/user-manuals/common/OxygenOS_14.0_User_Manual.pdf) |
| 真我 | APK 与源码审查，待真机 | 核对 realme UI 的后台活动、自启动、锁屏和通知权限。 [真我官方支持](https://www.realme.com/global/support/faq) |
| 三星 | APK 与源码审查，待真机 | 先检查 Auto Blocker 是否允许侧载，再测 One UI 睡眠和深度睡眠策略对后台收信的影响。 [三星 Auto Blocker](https://www.samsung.com/us/support/answer/ANS10003636/) · [三星应用休眠说明](https://www.samsung.com/us/support/answer/ANS10003442/) |

## 通知与后台收信排查

- 应用被系统标记为停止后，先打开应用一次，再用新短信复测。SMS 冷启动通过仅代表系统能从无运行进程的状态唤起接收器；后台缓存、锁屏、省电模式及重启后应分别验证。漏收时可复制短信到取件列表补录。 [Android 停止状态说明](https://developer.android.com/about/versions/15/behavior-changes-all)
- 通知不显示时，在 **设置 → 权限与超期提醒** 检查通知权限和待取通知渠道。锁屏公开摘要只显示待取件数。系统允许锁屏展示私密内容时，常驻卡片只显示件数，点开后查看完整内容。
- 当前版本保留小米超级岛接入，系统上屏验证暂缓。其他品牌使用 Android 普通通知。 [小米超级岛接入说明](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)
