package com.cardkey.sdk;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.cardkey.sdk.ActivationChecker.ActivationResult;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CardKeySDK {

    private static final String QQ_URL = "https://qm.qq.com/q/CC8NGfmTJY";
    private static Dialog dialog;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final ActivationChecker checker = new ActivationChecker();

    public interface Callback {
        void onVerified();
    }

    public static void init(Activity activity, Callback callback) {
        String deviceId = generateDeviceId(activity);
        showDialog(activity, deviceId, callback);
    }

    private static String generateDeviceId(Activity activity) {
        String androidId = Settings.Secure.getString(
                activity.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = "0000000000000000";
        long hash = 0;
        for (char c : androidId.toCharArray()) hash = hash * 31 + c;
        return String.format(Locale.US, "%08d", Math.abs(hash) % 100000000L);
    }

    private static void showDialog(Activity activity, String deviceId, Callback callback) {
        int dp8 = dp(activity, 8);
        int dp12 = dp(activity, 12);
        int dp16 = dp(activity, 16);
        int dp24 = dp(activity, 24);
        int dp300 = dp(activity, 300);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp24, dp24, dp24, dp24);
        root.setBackground(createDialogBg(activity));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("卡密验证");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        View divider1 = new View(activity);
        divider1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider1.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        dp1.setMargins(0, dp12, 0, dp12);
        divider1.setLayoutParams(dp1);
        root.addView(divider1);

        TextView tvDeviceId = new TextView(activity);
        tvDeviceId.setText("设备标识: " + deviceId);
        tvDeviceId.setTextColor(0xFFB0B0B0);
        tvDeviceId.setTextSize(14);
        tvDeviceId.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(tvDeviceId);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText("正在验证...");
        tvStatus.setTextColor(Color.WHITE);
        tvStatus.setTextSize(15);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp12, 0, 0);
        tvStatus.setLayoutParams(sp);
        root.addView(tvStatus);

        ProgressBar progress = new ProgressBar(activity);
        progress.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));
        ((LinearLayout.LayoutParams) progress.getLayoutParams()).setMargins(0, dp12, 0, 0);
        root.addView(progress);

        View divider2 = new View(activity);
        divider2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider2.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dp2.setMargins(0, dp16, 0, dp12);
        divider2.setLayoutParams(dp2);
        root.addView(divider2);

        TextView tvQq = new TextView(activity);
        tvQq.setText("联系QQ获取卡密");
        tvQq.setTextColor(0xFF12B7F5);
        tvQq.setTextSize(14);
        tvQq.setPadding(dp8, dp8, dp8, dp8);
        tvQq.setClickable(true);
        tvQq.setOnClickListener(v -> {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(QQ_URL)));
        });
        root.addView(tvQq);

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(dp300, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        executor.execute(() -> {
            ActivationResult result = checker.verifyActivation(deviceId);
            handler.post(() -> {
                progress.setVisibility(View.GONE);
                if (result.success) {
                    tvStatus.setText("验证通过，正在进入...");
                    tvStatus.setTextColor(0xFF4CAF50);
                    handler.postDelayed(() -> {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        callback.onVerified();
                    }, 1500);
                } else {
                    tvStatus.setText(result.message);
                    tvStatus.setTextColor(0xFFF44336);
                }
            });
        });
    }

    private static android.graphics.drawable.Drawable createDialogBg(Activity activity) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF16213E);
        bg.setCornerRadius(dp(activity, 16));
        bg.setStroke(dp(activity, 1), 0x33FFFFFF);
        return bg;
    }

    private static int dp(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
