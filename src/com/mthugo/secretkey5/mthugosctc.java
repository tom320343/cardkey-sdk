package com.mthugo.secretkey5;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class mthugosctc {

    static {
        try {
            System.loadLibrary("mthugo");
        } catch (UnsatisfiedLinkError e) {
            Log.e("mthugo", "Failed to load native library", e);
        }
    }

    public static long cachedBeijingTime;

    // ===== Native methods =====
    private static native String nativeDetectEnvironment(Activity activity);
    private static native String nativeGenerateDeviceId();
    private static native boolean nativeFetchBeijingTime();
    private static native boolean nativeCheckExpiry(String expiryStr, long beijingTime);
    private static native String nativeFetchValidation(String deviceId);

    // ===== Placeholder strings (replace via MT Manager) =====
    static String _validationUrl = "填写你的QQ收藏地址";
    static String _contactUrl = "填写你的QQ链接地址";
    static String _expectedSignature = "填写你的签名SHA256";

    private static String getValidationUrl() {
        return _validationUrl;
    }

    private static String getContactUrl() {
        return _contactUrl;
    }

    private static String getExpectedSignature() {
        return _expectedSignature;
    }

    // ===== Public API =====
    public static void initializeDevice(final Activity activity) {
        if (_validationUrl == null || _contactUrl == null || _expectedSignature == null) return;

        try {
            String detection = nativeDetectEnvironment(activity);
            if (detection != null) {
                showBlockDialog(activity, detection);
                return;
            }
        } catch (Exception e) {
            Log.e("mthugo", "native detection failed", e);
        }

        final SharedPreferences prefs = activity.getSharedPreferences("device_prefs", 0);
        final String deviceId = getOrCreateDeviceId(prefs);
        new CheckExpiryTask(activity, prefs, deviceId).execute();
    }

    // ===== Helper methods =====
    private static String getOrCreateDeviceId(SharedPreferences prefs) {
        String deviceId = prefs.getString("device_id", null);
        if (deviceId == null || deviceId.isEmpty()) {
            try {
                deviceId = nativeGenerateDeviceId();
            } catch (Exception e) {
                deviceId = String.valueOf(10000000 + (int)(Math.random() * 90000000));
            }
            prefs.edit().putString("device_id", deviceId).apply();
        }
        return deviceId;
    }

    // ===== Blocking dialog (Xposed/VM/VPN/Signature) =====
    private static void showBlockDialog(final Activity activity, final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Dialog dialog = new Dialog(activity);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);

                LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dp(activity, 24), dp(activity, 24),
                        dp(activity, 24), dp(activity, 20));

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dp(activity, 12));
                bg.setColor(Color.WHITE);
                bg.setStroke(dp(activity, 2), Color.parseColor("#E0E0E0"));
                layout.setBackground(bg);

                TextView title = new TextView(activity);
                title.setText("安全警告");
                title.setTextSize(20);
                title.setTextColor(Color.parseColor("#FF4D4F"));
                title.setGravity(Gravity.CENTER);
                layout.addView(title);

                TextView msg = new TextView(activity);
                msg.setText(message + "，应用无法运行。");
                msg.setTextSize(15);
                msg.setTextColor(Color.parseColor("#333333"));
                msg.setGravity(Gravity.CENTER);
                msg.setPadding(0, dp(activity, 12), 0, dp(activity, 16));
                layout.addView(msg);

                Button exitBtn = new Button(activity);
                exitBtn.setText("退出");
                exitBtn.setTextSize(15);
                exitBtn.setTextColor(Color.WHITE);
                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setShape(GradientDrawable.RECTANGLE);
                btnBg.setCornerRadius(dp(activity, 8));
                btnBg.setColor(Color.parseColor("#FF4D4F"));
                exitBtn.setBackground(btnBg);
                exitBtn.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
                exitBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        activity.finishAffinity();
                        System.exit(0);
                    }
                });
                layout.addView(exitBtn);

                dialog.setContentView(layout);
                Window window = dialog.getWindow();
                if (window != null) {
                    DisplayMetrics metrics = new DisplayMetrics();
                    activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                    WindowManager.LayoutParams params = window.getAttributes();
                    params.width = (int) (metrics.widthPixels * 0.85);
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    window.setAttributes(params);
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                }
                dialog.show();
            }
        });
    }

    private static int dp(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ===== Check expiry task =====
    private static class CheckExpiryTask extends AsyncTask<Void, Void, Boolean> {
        private final Activity activity;
        private final SharedPreferences prefs;
        private final String deviceId;

        CheckExpiryTask(Activity a, SharedPreferences p, String d) {
            this.activity = a; this.prefs = p; this.deviceId = d;
        }

        @Override
        protected Boolean doInBackground(Void... v) {
            String fullExpiry = null;
            try {
                fullExpiry = nativeFetchValidation(deviceId);
            } catch (Exception e) {
                Log.e("mthugo", "native fetch failed", e);
            }
            if (fullExpiry != null) {
                prefs.edit().putString("expiry_date", fullExpiry).apply();
                try {
                    if (nativeFetchBeijingTime()) {
                        if (nativeCheckExpiry(fullExpiry, cachedBeijingTime)) return true;
                    }
                } catch (Exception e) {
                    Log.e("mthugo", "native time check failed", e);
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean valid) {
            if (!valid) new ActivationDialog(activity, prefs, deviceId).show();
        }
    }

    // ===== Activation dialog =====
    private static class ActivationDialog {
        private final Activity context;
        private final SharedPreferences prefs;
        private final String deviceId;
        private Dialog dialog;
        private Handler autoCheckHandler;
        private Runnable autoCheckRunnable;
        private TextView statusText;
        private boolean dismissed;

        ActivationDialog(Activity c, SharedPreferences p, String d) {
            this.context = c; this.prefs = p; this.deviceId = d;
        }

        void show() {
            createDialog();
            setupMainLayout();
            configureDialogWindow();
            dialog.show();
            startAutoCheck();
        }

        private void dismiss() {
            if (!dismissed && dialog != null && dialog.isShowing()) {
                dismissed = true;
                stopAutoCheck();
                dialog.dismiss();
            }
        }

        private void startAutoCheck() {
            autoCheckHandler = new Handler();
            autoCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    if (dismissed) return;
                    new AsyncTask<Void, Void, Boolean>() {
                        @Override
                        protected Boolean doInBackground(Void... v) {
                            String saved = prefs.getString("expiry_date", null);
                            if (saved != null) {
                                try {
                                    if (nativeFetchBeijingTime()) {
                                        return nativeCheckExpiry(saved, cachedBeijingTime);
                                    }
                                } catch (Exception e) {}
                                return false;
                            }
                            String exp;
                            try {
                                exp = nativeFetchValidation(deviceId);
                            } catch (Exception e) { return false; }
                            if (exp != null) {
                                prefs.edit().putString("expiry_date", exp).apply();
                                try {
                                    if (nativeFetchBeijingTime()) {
                                        return nativeCheckExpiry(exp, cachedBeijingTime);
                                    }
                                } catch (Exception e) {}
                            }
                            return false;
                        }

                        @Override
                        protected void onPostExecute(Boolean valid) {
                            if (valid) {
                                if (statusText != null) {
                                    statusText.setText("验证通过，激活成功");
                                    statusText.setTextColor(Color.parseColor("#4CAF50"));
                                }
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() { dismiss(); }
                                }, 800);
                            } else {
                                if (autoCheckHandler != null && !dismissed) {
                                    autoCheckHandler.postDelayed(autoCheckRunnable, 30000);
                                }
                            }
                        }
                    }.execute();
                }
            };
            autoCheckHandler.postDelayed(autoCheckRunnable, 30000);
        }

        private void stopAutoCheck() {
            if (autoCheckHandler != null && autoCheckRunnable != null) {
                autoCheckHandler.removeCallbacks(autoCheckRunnable);
            }
        }

        private void createDialog() {
            dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
        }

        private void configureDialogWindow() {
            Window window = dialog.getWindow();
            if (window != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                context.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = (int) (metrics.widthPixels * 0.9);
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.y = -dp(context, 15);
                window.setAttributes(params);
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }

        private GradientDrawable roundRect(String color, int radius, int strokeW, String strokeC) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.RECTANGLE);
            d.setCornerRadius(dp(context, radius));
            d.setColor(Color.parseColor(color));
            if (strokeW > 0) d.setStroke(dp(context, strokeW), Color.parseColor(strokeC));
            return d;
        }

        private Button createButton(String text, String colorHex, int radius) {
            Button b = new Button(context);
            b.setText(text); b.setTextSize(16); b.setTextColor(Color.WHITE);
            b.setBackground(roundRect(colorHex, radius, 0, null));
            b.setPadding(0, dp(context, 12), 0, dp(context, 12));
            return b;
        }

        private void setupMainLayout() {
            LinearLayout main = new LinearLayout(context);
            main.setOrientation(LinearLayout.VERTICAL);
            main.setBackground(roundRect("#FFFFFF", 16, 2, "#E0E0E0"));
            main.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 16));

            TextView title = new TextView(context);
            title.setText("软件激活");
            title.setTextSize(22);
            title.setTextColor(Color.parseColor("#1A1A1A"));
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(0, 0, 0, dp(context, 6));
            main.addView(title, tp);

            TextView sub = new TextView(context);
            sub.setText("请复制设备ID发送给管理员进行激活");
            sub.setTextSize(14);
            sub.setTextColor(Color.parseColor("#888888"));
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, 0, 0, dp(context, 10));
            main.addView(sub);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(roundRect("#F5F7FA", 12, 0, null));
            card.setPadding(dp(context, 14), dp(context, 12), dp(context, 10), dp(context, 12));

            TextView label = new TextView(context);
            label.setText("设备ID");
            label.setTextSize(13);
            label.setTextColor(Color.parseColor("#999999"));
            card.addView(label);

            TextView val = new TextView(context);
            val.setText(deviceId);
            val.setTextSize(18);
            val.setTextColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            vp.setMargins(dp(context, 8), 0, dp(context, 8), 0);
            card.addView(val, vp);

            Button copy = new Button(context);
            copy.setText("复制");
            copy.setTextSize(13);
            copy.setTextColor(Color.WHITE);
            copy.setGravity(Gravity.CENTER);
            copy.setBackground(roundRect("#1890FF", 8, 0, null));
            copy.setPadding(dp(context, 14), dp(context, 5), dp(context, 14), dp(context, 5));
            copy.setMinHeight(0);
            copy.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("device_code", deviceId));
                    Toast.makeText(context, "设备ID已复制", Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(copy);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, dp(context, 8));
            main.addView(card, cp);

            statusText = new TextView(context);
            statusText.setText("等待激活...");
            statusText.setTextSize(12);
            statusText.setTextColor(Color.parseColor("#FF9800"));
            statusText.setGravity(Gravity.CENTER);
            statusText.setPadding(0, 0, 0, dp(context, 12));
            main.addView(statusText);

            LinearLayout btns = new LinearLayout(context);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            btns.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            bp.setMargins(dp(context, 6), 0, dp(context, 6), 0);

            Button qqBtn = createButton("联系客服", "#1890FF", 8);
            qqBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(getContactUrl()));
                        context.startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            btns.addView(qqBtn, bp);

            Button actBtn = createButton("手动验证", "#52C41A", 8);
            actBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (statusText != null) {
                        statusText.setText("正在验证...");
                        statusText.setTextColor(Color.parseColor("#1890FF"));
                    }
                    new ActivationTask().execute();
                }
            });
            btns.addView(actBtn, bp);

            Button exitBtn = createButton("退出", "#FF4D4F", 8);
            exitBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    context.finishAffinity();
                    System.exit(0);
                }
            });
            btns.addView(exitBtn, bp);

            main.addView(btns);
            dialog.setContentView(main);
        }

        private class ActivationTask extends AsyncTask<Void, Void, Boolean> {
            @Override
            protected Boolean doInBackground(Void... v) {
                String exp;
                try {
                    exp = nativeFetchValidation(deviceId);
                } catch (Exception e) { return false; }
                if (exp != null) {
                    try {
                        if (nativeFetchBeijingTime()) {
                            if (nativeCheckExpiry(exp, cachedBeijingTime)) {
                                prefs.edit().putString("expiry_date", exp).apply();
                                new CheckExpiryTask(context, prefs, deviceId).execute();
                                return true;
                            }
                        }
                    } catch (Exception e) {}
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean ok) {
                if (ok) {
                    stopAutoCheck();
                    if (statusText != null) {
                        statusText.setText("验证通过，激活成功");
                        statusText.setTextColor(Color.parseColor("#52C41A"));
                    }
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() { dismiss(); }
                    }, 800);
                } else {
                    if (statusText != null) {
                        statusText.setText("激活失败，请QQ联系管理员");
                        statusText.setTextColor(Color.parseColor("#FF4D4F"));
                    }
                }
            }
        }
    }
}
