# CardKey SDK - 卡密弹窗集成指南

给你的 Android 应用添加卡密验证弹窗。

## 效果

应用启动 → 弹出卡密验证窗口 → 验证通过后弹窗消失，进入应用  
验证失败则弹窗保持，无法进入。

## 方式一：DEX 集成（推荐）

### 1. 复制 DEX 文件

将 `classes.dex` 复制到你的项目 `app/src/main/assets/` 目录下。

### 2. 在你的 Application 或主 Activity 中加载 DEX

```java
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 加载卡密弹窗 SDK
        loadCardKeySDK();
    }

    private void loadCardKeySDK() {
        try {
            // 将 assets 中的 dex 复制到内部存储
            File dexDir = getDir("dex", MODE_PRIVATE);
            File dexFile = new File(dexDir, "cardkey.dex");
            if (!dexFile.exists()) {
                InputStream in = getAssets().open("classes.dex");
                FileOutputStream out = new FileOutputStream(dexFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                out.close();
            }

            // 加载 DEX
            DexClassLoader loader = new DexClassLoader(
                dexFile.getAbsolutePath(),
                dexDir.getAbsolutePath(),
                null,
                getClassLoader()
            );

            // 反射调用 CardKeySDK.init()
            Class<?> clz = loader.loadClass("com.cardkey.sdk.CardKeySDK");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                clz.getClassLoader(),
                new Class[]{loader.loadClass("com.cardkey.sdk.CardKeySDK$Callback")},
                (proxy, method, args) -> {
                    if ("onVerified".equals(method.getName())) {
                        // 验证通过，在这里写你的正常启动逻辑
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "验证通过", Toast.LENGTH_SHORT).show();
                            initApp(); // 你的正常初始化
                        });
                    }
                    return null;
                }
            );

            Method init = clz.getMethod("init", android.app.Activity.class,
                loader.loadClass("com.cardkey.sdk.CardKeySDK$Callback"));
            init.invoke(null, this, callback);

        } catch (Exception e) {
            e.printStackTrace();
            // 加载失败，直接放行（或阻止进入，按你的需求）
            initApp();
        }
    }

    private void initApp() {
        // 你的正常应用初始化代码
    }
}
```

## 方式二：源码集成

### 1. 复制源码文件

将以下两个 Java 文件复制到你的项目中：
- `src/com/cardkey/sdk/CardKeySDK.java`
- `src/com/cardkey/sdk/ActivationChecker.java`

放入 `app/src/main/java/com/cardkey/sdk/` 目录。

### 2. 添加网络权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application android:usesCleartextTraffic="true" ...>
```

### 3. 在主 Activity 中调用

```java
import com.cardkey.sdk.CardKeySDK;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 一行代码接入卡密弹窗
        CardKeySDK.init(this, () -> {
            // 验证通过后执行的代码
            initApp();
        });
    }

    private void initApp() {
        // 你的正常应用初始化
    }
}
```

## 配置激活链接

在 https://sharechain.qq.com/8a6f6fefa3dbd6588fb816a22d3bddcf 的微云笔记中，按以下格式写入：

```
设备标识-过期时间
```

例如：`47139662-202709201830` 表示此设备在 2027年9月20日18:30 前有效。

设备标识是应用运行时自动生成的 8 位数字。

## 验证逻辑

| 条件 | 结果 |
|------|------|
| 当前时间 < 过期时间 | 卡密有效，弹窗消失 |
| 当前时间 >= 过期时间 | 卡密过期，弹窗保持 |
| 未找到设备标识 | 弹窗保持，显示"未找到激活记录" |

## 依赖

- minSdk: 26
- 无需额外第三方库

## QQ 联系方式

https://qm.qq.com/q/CC8NGfmTJY
