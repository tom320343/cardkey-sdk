# ProGuard rules for mthugo-sdk (R8 compatible)

# Keep main class name (JNI requires exact class name)
-keep class com.mthugo.secretkey5.mthugosctc {
    public static void initializeDevice(android.app.Activity);
    public static long cachedBeijingTime;
    static java.lang.String _validationUrl;
    static java.lang.String _contactUrl;
    static java.lang.String _expectedSignature;
}

# Keep methods called from native code via JNI
-keepclassmembers class com.mthugo.secretkey5.mthugosctc {
    private static java.lang.String getValidationUrl();
    private static java.lang.String getContactUrl();
    private static java.lang.String getExpectedSignature();
}

# Obfuscate inner classes and everything else
-repackageclasses 'a'
-allowaccessmodification
-overloadaggressively
