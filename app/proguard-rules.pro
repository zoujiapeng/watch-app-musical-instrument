-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Activity entry points are preserved by the Android Gradle plugin. Keep the
# recording model names because saved JSON files intentionally use enum names.
-keepclassmembers enum com.zoujiapeng.watchinstrument.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
