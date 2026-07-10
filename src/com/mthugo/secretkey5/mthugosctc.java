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
            mainLayout.setBackground(createRoundRectNoStroke("#EBFFF6", 20));
            mainLayout.setPadding(dpToPx(15), dpToPx(12), dpToPx(15), dpToPx(15));

            TextView titleText = new TextView(context);
            titleText.setText("激活验证");
            titleText.setTextSize(20);
            titleText.setTextColor(Color.parseColor("#333333"));
            titleText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.setMargins(0, 0, 0, dpToPx(8));
            mainLayout.addView(titleText, titleParams);

            TextView subtitleText = new TextView(context);
            subtitleText.setText("但行好事，莫问前程");
            subtitleText.setTextSize(14);
            subtitleText.setTextColor(Color.parseColor("#666666"));
            subtitleText.setPadding(dpToPx(12), 0, 0, dpToPx(4));
            mainLayout.addView(subtitleText);

            LinearLayout deviceRow = new LinearLayout(context);
            deviceRow.setOrientation(LinearLayout.HORIZONTAL);
            deviceRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView deviceIdText = new TextView(context);
            deviceIdText.setText("设备ID：" + deviceId);
            deviceIdText.setTextSize(16);
            deviceIdText.setTextColor(Color.parseColor("#2196F3"));
            deviceIdText.setBackground(createRoundRectWithStroke("#484848", 12, 1, "#BBDEFB"));
            deviceIdText.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

            LinearLayout.LayoutParams deviceIdParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            deviceIdParams.setMargins(0, 0, dpToPx(10), 0);
            deviceRow.addView(deviceIdText, deviceIdParams);

            Button copyBtn = new Button(context);
            copyBtn.setText("复制");
            copyBtn.setTextSize(14);
            copyBtn.setTextColor(Color.WHITE);
            copyBtn.setGravity(Gravity.CENTER);
            copyBtn.setBackground(createRoundRectNoStroke("#90A4AE", 8));
            copyBtn.setPadding(dpToPx(15), 0, dpToPx(15), 0);
            copyBtn.setMinHeight(0);
            copyBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            copyBtn.setOnClickListener(new CopyClickListener(this));
            deviceRow.addView(copyBtn);
            mainLayout.addView(deviceRow);

            LinearLayout buttonRow = new LinearLayout(context);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);
            buttonRow.setPadding(0, dpToPx(15), 0, 0);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(dpToPx(10), 0, dpToPx(10), 0);

            Button qqBtn = createButton("QQ", "#2196F3", 8);
            qqBtn.setOnClickListener(new QQClickListener(this));
            buttonRow.addView(qqBtn, btnParams);

            Button activateBtn = createButton("激活", "#4CAF50", 8);
            activateBtn.setOnClickListener(new ActivateClickListener(this));
            buttonRow.addView(activateBtn, btnParams);

            Button exitBtn = createButton("退出", "#F44336", 8);
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
                new ActivationTask(dialog.getContext(), dialog.getPrefs(), dialog.getDeviceId(), dialog.getDialog()).execute();
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
            private final Activity context;
            private final SharedPreferences prefs;
            private final String deviceId;
            private final Dialog dialog;

            ActivationTask(Activity context, SharedPreferences prefs, String deviceId, Dialog dialog) {
                this.context = context;
                this.prefs = prefs;
                this.deviceId = deviceId;
                this.dialog = dialog;
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
                        prefs.edit().putString(getKeyExpiryDate(), fullExpiry).apply();
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    new CheckExpiryTask(context, prefs, deviceId).execute();
                    dialog.dismiss();
                    Toast.makeText(context, "激活成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "激活失败：请QQ联系获取激活权限", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
