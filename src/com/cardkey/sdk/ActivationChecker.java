package com.cardkey.sdk;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivationChecker {

    private static final String ACTIVATION_URL =
            "https://sharechain.qq.com/8a6f6fefa3dbd6588fb816a22d3bddcf?qq_aio_chat_type=2";
    private static final String TIME_URL = "https://time.is/China";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;
    private static final int MAX_RETRIES = 3;

    public ActivationResult verifyActivation(String deviceId) {
        String content = fetchUrlWithRetry(ACTIVATION_URL);
        if (content == null) {
            return new ActivationResult(false, "无法连接激活服务器");
        }

        String expiryStr = parseActivationData(content, deviceId);
        if (expiryStr == null) {
            return new ActivationResult(false, "未找到激活记录");
        }

        Date expiryDate = parseExpiryTime(expiryStr);
        if (expiryDate == null) {
            return new ActivationResult(false, "时间格式无效: " + expiryStr);
        }

        Date currentChinaTime = fetchCurrentChinaTime();
        if (currentChinaTime == null) {
            currentChinaTime = new Date();
        }

        if (currentChinaTime.before(expiryDate)) {
            return new ActivationResult(true, "验证通过", expiryDate);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return new ActivationResult(false, "卡密已过期 (" + sdf.format(expiryDate) + ")", expiryDate);
        }
    }

    private String parseActivationData(String content, String deviceId) {
        Pattern p = Pattern.compile("\"html_content\"\\s*:\\s*\"(" + Pattern.quote(deviceId) + ")-(\\d{12})\"");
        Matcher m = p.matcher(content);
        if (m.find()) return m.group(2);

        p = Pattern.compile("<span[^>]*class=\"tit\"[^>]*>\\s*(" + Pattern.quote(deviceId) + ")-(\\d{12})\\s*</span>");
        m = p.matcher(content);
        if (m.find()) return m.group(2);

        String cleaned = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
        p = Pattern.compile("(" + Pattern.quote(deviceId) + ")-(\\d{12})");
        m = p.matcher(cleaned);
        if (m.find()) return m.group(2);

        return null;
    }

    private Date parseExpiryTime(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault());
            sdf.setLenient(false);
            return sdf.parse(timeStr);
        } catch (Exception e) {
            return null;
        }
    }

    private Date fetchCurrentChinaTime() {
        String html = fetchUrlWithRetry(TIME_URL);
        if (html == null) return null;
        try {
            Pattern p = Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2})");
            Matcher m = p.matcher(html);
            if (m.find()) {
                String[] parts = m.group(1).split(":");
                java.util.Calendar cal = java.util.Calendar.getInstance(
                        java.util.TimeZone.getTimeZone("Asia/Shanghai"));
                cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                cal.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1]));
                cal.set(java.util.Calendar.SECOND, Integer.parseInt(parts[2]));
                return cal.getTime();
            }
        } catch (Exception ignored) {}
        return new Date();
    }

    private String fetchUrlWithRetry(String urlStr) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String result = fetchUrl(urlStr);
            if (result != null && !result.isEmpty()) return result;
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    private String fetchUrl(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static class ActivationResult {
        public boolean success;
        public String message;
        public Date expiryDate;

        public ActivationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public ActivationResult(boolean success, String message, Date expiryDate) {
            this.success = success;
            this.message = message;
            this.expiryDate = expiryDate;
        }
    }
}
