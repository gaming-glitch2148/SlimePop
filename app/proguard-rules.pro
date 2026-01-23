# Slime Pop ProGuard Rules

# 1. Keep Play Games Services v2
-keep class com.google.android.gms.games.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# 2. Keep Billing Library
-keep class com.android.billingclient.api.** { *; }

# 3. Keep AdMob (Play Services Ads)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# 4. Keep Play Review Library
-keep class com.google.android.play.core.review.** { *; }
-keep class com.google.android.play.core.tasks.** { *; }

# 5. Keep ViewBinding classes (to avoid crashes if layout names are obfuscated)
-keep class com.slimepop.asmr.databinding.** { *; }

# 6. Keep our App's core logic that interacts with external SDKs
-keep class com.slimepop.asmr.Prefs { *; }
-keep class com.slimepop.asmr.Catalog { *; }
-keep class com.slimepop.asmr.Entitlements { *; }
-keep class com.slimepop.asmr.QuestState { *; }

# General optimization settings
-keepattributes SourceFile,LineNumberTable,*Annotation*
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
