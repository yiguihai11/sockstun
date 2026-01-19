# ProGuard rules for hev.sockstun

# 保持 native 方法的类名和方法名（JNI 调用需要）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保持 MainActivity 等主要类
-keep public class hev.sockstun.** {
    public *;
}

# 保持 VPN Service 相关
-keep class hev.sockstun.TProxyService {
    public *;
}

# 保持 Broadcast Receiver
-keep class hev.sockstun.ServiceReceiver {
    public *;
}

# 保持 Activity 子类
-keep public class * extends android.app.Activity
-keep public class * extends android.preference.PreferenceActivity
-keep public class * extends android.app.Service

# 保持 Serializable 和 Parcelable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# SharedPreferences 可能被反射访问
-keepclassmembers class * extends android.preference.Preference {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# 保持枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保持日志相关（如果需要调试信息）
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# 保留 native 库的类
-keep class hev.sockstun.** {
    native <methods>;
}

# 不警告缺少的类（第三方库）
-dontwarn android.support.**
-dontwarn androidx.**
