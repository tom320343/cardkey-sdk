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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class mthugosctc {

    private static String getPrefsName() {
        return "device_prefs";
    }

    private static String getKeyDeviceId() {
        return "device_id";
    }

    private static String getKeyExpiryDate() {
        return "expiry_date";
    }

    private static String getValidationUrl() {
        return "填写你的QQ收藏地址";
    }

    private static String getContactUrl() {
        return "填写你的QQ链接地址";
    }

    private static long fetchBeijingTime() {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL("https://time.is/China");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line);
            }
            String content = html.toString();

            Pattern p = Pattern.compile("\"Beijing\",\"([^\"]+)\"");
            Matcher m = p.matcher(content);
            if (m.find()) {
                String timeStr = m.group(1);
                String[] parts = timeStr.split(":");
                if (parts.length >= 2) {
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    return cal.getTimeInMillis();
                }
            }

            Pattern p2 = Pattern.compile("(\\d{1,2}):(\\d{2})");
            Matcher m2 = p2.matcher(content);
            int foundCount = 0;
            while (m2.find() && foundCount < 5) {
                int hour = Integer.parseInt(m2.group(1));
                int minute = Integer.parseInt(m2.group(2));
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    return cal.getTimeInMillis();
                }
                foundCount++;
            }
        } catch (Exception e) {
            Log.e("TimeCheck", "Failed to fetch Beijing time", e);
        } finally {
            try {
                if (reader != null) reader.close();
                if (conn != null) conn.disconnect();
            } catch (IOException ignored) {}
        }
        return -1;
    }

    private static boolean checkExpiryWithBeijingTime(String expiryStr, long beijingTimeMs) {
        if (expiryStr == null || expiryStr.isEmpty()) {
            return false;
        }
        try {
            String[] parts = expiryStr.split("-");
            if (parts.length != 2) {
                return false;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault());
            Date expiryDate = sdf.parse(parts[1]);
            long expiryTime = expiryDate.getTime();
            return beijingTimeMs > 0 && beijingTimeMs < expiryTime;
        } catch (ParseException e) {
            return false;
        }
    }

    private static String generateRandomId() {
        Random random = new Random();
        int id = random.nextInt(90000000) + 10000000;
        return String.valueOf(id);
    }

    private static String getOrCreateDeviceId(SharedPreferences prefs) {
        String deviceId = prefs.getString(getKeyDeviceId(), null);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = generateRandomId();
            prefs.edit().putString(getKeyDeviceId(), deviceId).apply();
        }
        return deviceId;
    }

    public static void initializeDevice(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(getPrefsName(), 0);
        String deviceId = getOrCreateDeviceId(prefs);
        new CheckExpiryTask(activity, prefs, deviceId).execute();
    }

    private static class CheckExpiryTask extends AsyncTask<Void, Void, Boolean> {
        private final Activity activity;
        private final SharedPreferences prefs;
        private final String deviceId;

        CheckExpiryTask(Activity activity, SharedPreferences prefs, String deviceId) {
            this.activity = activity;
            this.prefs = prefs;
            this.deviceId = deviceId;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            String fullExpiry = null;
            try {
                URL url = new URL(getValidationUrl());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                Pattern pattern = Pattern.compile(deviceId + "-(\\d{12})");
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String expiryPart = matcher.group(1);
                        fullExpiry = deviceId + "-" + expiryPart;
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("CheckExpiry", "Network Error", e);
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (conn != null) conn.disconnect();
                } catch (IOException e) {
                    Log.e("CheckExpiry", "Close Error", e);
                }
            }

            if (fullExpiry != null) {
                prefs.edit().putString(getKeyExpiryDate(), fullExpiry).apply();
                long beijingTime = fetchBeijingTime();
                if (beijingTime > 0 && checkExpiryWithBeijingTime(fullExpiry, beijingTime)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean valid) {
            if (!valid) {
                new ActivationDialog(activity, prefs, deviceId).show();
            }
        }
    }

    private static class ActivationDialog {
        private final Activity context;
        private final SharedPreferences prefs;
        private final String deviceId;
        private Dialog dialog;
        private Handler autoCheckHandler;
        private Runnable autoCheckRunnable;
        private TextView statusText;
        private boolean dismissed = false;

        ActivationDialog(Activity context, SharedPreferences prefs, String deviceId) {
            this.context = context;
            this.prefs = prefs;
            this.deviceId = deviceId;
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
                            String saved = prefs.getString(getKeyExpiryDate(), null);
                            if (saved != null) {
                                long beijingTime = fetchBeijingTime();
                                return beijingTime > 0 && checkExpiryWithBeijingTime(saved, beijingTime);
                            }
                            HttpURLConnection conn = null;
                            BufferedReader reader = null;
                            try {
                                URL url = new URL(getValidationUrl());
                                conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("GET");
                                conn.setConnectTimeout(8000);
                                conn.setReadTimeout(8000);
                                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                String line;
                                Pattern pattern = Pattern.compile(deviceId + "-(\\d{12})");
                                while ((line = reader.readLine()) != null) {
                                    Matcher matcher = pattern.matcher(line);
                                    if (matcher.find()) {
                                        String exp = deviceId + "-" + matcher.group(1);
                                        prefs.edit().putString(getKeyExpiryDate(), exp).apply();
                                        long bt = fetchBeijingTime();
                                        return bt > 0 && checkExpiryWithBeijingTime(exp, bt);
                                    }
                                }
                            } catch (Exception ignored) {
                            } finally {
                                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                                try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
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
                                    public void run() {
                                        dismiss();
                                    }
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

        private int dpToPx(int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (dp * density + 0.5f);
        }

        private GradientDrawable createRoundRectNoStroke(String colorHex, int radiusDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(dpToPx(radiusDp));
            drawable.setColor(Color.parseColor(colorHex));
            return drawable;
        }

        private GradientDrawable createRoundRectWithStroke(String colorHex, int radiusDp, int strokeWidthDp, String strokeColorHex) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(dpToPx(radiusDp));
            drawable.setColor(Color.parseColor(colorHex));
            if (strokeWidthDp > 0) {
                drawable.setStroke(dpToPx(strokeWidthDp), Color.parseColor(strokeColorHex));
            }
            return drawable;
        }

        private Button createButton(String text, String colorHex, int radiusDp) {
            Button button = new Button(context);
            button.setText(text);
            button.setTextSize(16);
            button.setTextColor(Color.WHITE);
            button.setBackground(createRoundRectNoStroke(colorHex, radiusDp));
            button.setPadding(0, dpToPx(12), 0, dpToPx(12));
            return button;
        }

        private Activity getContext() {
            return context;
        }

        private SharedPreferences getPrefs() {
            return prefs;
        }

        private String getDeviceId() {
            return deviceId;
        }

        private Dialog getDialog() {
            return dialog;
        }

        private void setupMainLayout() {
            LinearLayout mainLayout = new LinearLayout(context);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setBackground(createRoundRectWithStroke("#FFFFFF", 16, 2, "#E0E0E0"));
            mainLayout.setPadding(dpToPx(20), dpToPx(18), dpToPx(20), dpToPx(16));

            TextView titleText = new TextView(context);
            titleText.setText("软件激活");
            titleText.setTextSize(22);
            titleText.setTextColor(Color.parseColor("#1A1A1A"));
            titleText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.setMargins(0, 0, 0, dpToPx(6));
            mainLayout.addView(titleText, titleParams);

            TextView subtitleText = new TextView(context);
            subtitleText.setText("请复制设备ID发送给管理员进行激活");
            subtitleText.setTextSize(14);
            subtitleText.setTextColor(Color.parseColor("#888888"));
            subtitleText.setGravity(Gravity.CENTER);
            subtitleText.setPadding(0, 0, 0, dpToPx(10));
            mainLayout.addView(subtitleText);

            LinearLayout cardLayout = new LinearLayout(context);
            cardLayout.setOrientation(LinearLayout.HORIZONTAL);
            cardLayout.setGravity(Gravity.CENTER_VERTICAL);
            cardLayout.setBackground(createRoundRectNoStroke("#F5F7FA", 12));
            cardLayout.setPadding(dpToPx(14), dpToPx(12), dpToPx(10), dpToPx(12));

            TextView deviceLabel = new TextView(context);
            deviceLabel.setText("设备ID");
            deviceLabel.setTextSize(13);
            deviceLabel.setTextColor(Color.parseColor("#999999"));
            cardLayout.addView(deviceLabel);

            TextView deviceValue = new TextView(context);
            deviceValue.setText(deviceId);
            deviceValue.setTextSize(18);
            deviceValue.setTextColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams deviceValueParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            deviceValueParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);
            cardLayout.addView(deviceValue, deviceValueParams);

            Button copyBtn = new Button(context);
            copyBtn.setText("复制");
            copyBtn.setTextSize(13);
            copyBtn.setTextColor(Color.WHITE);
            copyBtn.setGravity(Gravity.CENTER);
            copyBtn.setBackground(createRoundRectNoStroke("#1890FF", 8));
            copyBtn.setPadding(dpToPx(14), dpToPx(5), dpToPx(14), dpToPx(5));
            copyBtn.setMinHeight(0);
            copyBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            copyBtn.setOnClickListener(new CopyClickListener(this));
            cardLayout.addView(copyBtn);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, dpToPx(8));
            mainLayout.addView(cardLayout, cardParams);

            statusText = new TextView(context);
            statusText.setText("等待激活...");
            statusText.setTextSize(12);
            statusText.setTextColor(Color.parseColor("#FF9800"));
            statusText.setGravity(Gravity.CENTER);
            statusText.setPadding(0, 0, 0, dpToPx(12));
            mainLayout.addView(statusText);

            LinearLayout buttonRow = new LinearLayout(context);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(dpToPx(6), 0, dpToPx(6), 0);

            Button qqBtn = createButton("联系客服", "#1890FF", 8);
            qqBtn.setOnClickListener(new QQClickListener(this));
            buttonRow.addView(qqBtn, btnParams);

            Button activateBtn = createButton("手动验证", "#52C41A", 8);
            activateBtn.setOnClickListener(new ActivateClickListener(this));
            buttonRow.addView(activateBtn, btnParams);

            Button exitBtn = createButton("退出", "#FF4D4F", 8);
            exitBtn.setOnClickListener(new ExitClickListener(this));
            buttonRow.addView(exitBtn, btnParams);

            mainLayout.addView(buttonRow);
            dialog.setContentView(mainLayout);
        }

        private void configureDialogWindow() {
            Window window = dialog.getWindow();
            if (window != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                context.getWindowManager().getDefaultDisplay().getMetrics(metrics);

                WindowManager.LayoutParams params = window.getAttributes();
                params.width = (int) (metrics.widthPixels * 0.9);
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.y = -dpToPx(15);
                window.setAttributes(params);
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }

        private static class CopyClickListener implements View.OnClickListener {
            private final ActivationDialog dialog;

            CopyClickListener(ActivationDialog dialog) {
                this.dialog = dialog;
            }

            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) dialog.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("device_code", dialog.getDeviceId());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(dialog.getContext(), "设备ID已复制", Toast.LENGTH_SHORT).show();
            }
        }

        private static class QQClickListener implements View.OnClickListener {
            private final ActivationDialog dialog;

            QQClickListener(ActivationDialog dialog) {
                this.dialog = dialog;
            }

            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(getContactUrl()));
                    dialog.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(dialog.getContext(), "无法打开链接", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private static class ActivateClickListener implements View.OnClickListener {
            private final ActivationDialog dialog;

            ActivateClickListener(ActivationDialog dialog) {
                this.dialog = dialog;
            }

            @Override
            public void onClick(View v) {
                if (dialog.statusText != null) {
                    dialog.statusText.setText("正在验证...");
                    dialog.statusText.setTextColor(Color.parseColor("#1890FF"));
                }
                new ActivationTask(dialog).execute();
            }
        }

        private static class ExitClickListener implements View.OnClickListener {
            private final ActivationDialog dialog;

            ExitClickListener(ActivationDialog dialog) {
                this.dialog = dialog;
            }

            @Override
            public void onClick(View v) {
                dialog.getContext().finishAffinity();
                System.exit(0);
            }
        }

        private static class ActivationTask extends AsyncTask<Void, Void, Boolean> {
            private final ActivationDialog activationDialog;

            ActivationTask(ActivationDialog dialog) {
                this.activationDialog = dialog;
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                HttpURLConnection conn = null;
                BufferedReader reader = null;
                String fullExpiry = null;
                try {
                    URL url = new URL(getValidationUrl());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    Pattern pattern = Pattern.compile(activationDialog.deviceId + "-(\\d{12})");
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            String expiryPart = matcher.group(1);
                            fullExpiry = activationDialog.deviceId + "-" + expiryPart;
                            break;
                        }
                    }
                } catch (IOException e) {
                    Log.e("Activation", "Network Error", e);
                } finally {
                    try {
                        if (reader != null) reader.close();
                        if (conn != null) conn.disconnect();
                    } catch (IOException e) {
                        Log.e("Activation", "Close Error", e);
                    }
                }

                if (fullExpiry != null) {
                    long beijingTime = fetchBeijingTime();
                    if (beijingTime > 0 && checkExpiryWithBeijingTime(fullExpiry, beijingTime)) {
                        activationDialog.prefs.edit().putString(getKeyExpiryDate(), fullExpiry).apply();
                        new CheckExpiryTask(activationDialog.context, activationDialog.prefs, activationDialog.deviceId).execute();
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    activationDialog.stopAutoCheck();
                    if (activationDialog.statusText != null) {
                        activationDialog.statusText.setText("验证通过，激活成功");
                        activationDialog.statusText.setTextColor(Color.parseColor("#52C41A"));
                    }
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            activationDialog.dismiss();
                        }
                    }, 800);
                } else {
                    if (activationDialog.statusText != null) {
                        activationDialog.statusText.setText("激活失败，请QQ联系管理员");
                        activationDialog.statusText.setTextColor(Color.parseColor("#FF4D4F"));
                    }
                }
            }
        }
    }
}
