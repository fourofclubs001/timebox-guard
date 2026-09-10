# Timebox Guard — R8 rules for the release build.
#
# The app uses no reflection, serialization libraries or dynamic class
# loading, so the defaults from proguard-android-optimize.txt cover almost
# everything. The rules below are belt-and-braces.

# The accessibility service's class name is compared against the value in
# Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES (see SettingsActivity), so
# it must not be renamed. Manifest components are kept by AGP already; this
# makes the intent explicit.
-keep class com.timebox.guard.AppMonitorService { *; }

# Custom View inflated only from code — keep the (Context) / (Context,
# AttributeSet) constructors R8 would otherwise consider unused.
-keep class com.timebox.guard.BarChartView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep line numbers for readable crash reports; hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
