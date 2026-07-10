# mthugo-sdk - Android 卡密弹窗 DEX (MT管理器专用)

## 集成方式

### 1. 将 DEX 添加到 APK

用 MT 管理器打开你的 APK，将 `classes.dex` 添加到 APK 根目录。

### 2. 在 MainActivity 的 onCreate 中插入调用代码

用 MT 管理器的 DEX 编辑器打开 `MainActivity.smali`，在 `onCreate` 方法中 `invoke-super` 之后插入：

```smali
invoke-static {p0}, Lcom/mthugo/secretkey5/mthugosctc;->initializeDevice(Landroid/app/Activity;)V
```

### 3. 修改链接地址

用 MT 管理器的 DEX 字符串搜索功能，修改以下两个占位字符串：

| 搜索内容 | 替换为 |
|---------|--------|
| `填写你的QQ收藏地址` | 你的 sharechain.qq.com 激活链接 |
| `填写你的QQ链接地址` | 你的 QQ 联系方式链接 |

### 4. 在微云笔记中配置激活数据

在 sharechain.qq.com 的微云笔记中写入以下格式：

```
设备标识-过期时间(YYYYMMDDHHmm)
```

例如：`47139662-202709201830`

设备标识是应用首次运行时自动生成的 8 位数字（10000000-99999999）。

## 功能说明

| 按钮 | 功能 |
|------|------|
| 复制 | 复制设备ID到剪贴板 |
| QQ | 打开 QQ 联系方式链接 |
| 激活 | 联网验证卡密/获取过期时间 |
| 退出 | 关闭应用 |

## 验证逻辑

1. 应用启动 → 检查本地是否已有有效过期时间
2. 有且未过期 → 不弹窗
3. 无或已过期 → 弹出激活窗口
4. 点击"激活"→ 联网获取过期时间并验证
5. 有效 → 弹窗消失；无效 → 提示"激活失败：请QQ联系获取激活权限"

## 包名

```
com.mthugo.secretkey5.mthugosctc
```

## 调用方法

```smali
invoke-static {p0}, Lcom/mthugo/secretkey5/mthugosctc;->initializeDevice(Landroid/app/Activity;)V
```
