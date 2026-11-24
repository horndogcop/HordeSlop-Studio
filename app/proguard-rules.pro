-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class net.hordecraft.android.data.remote.**$$serializer { *; }
-keep,includedescriptorclasses class net.hordecraft.android.domain.**$$serializer { *; }
-keepclassmembers class net.hordecraft.android.data.remote.** {
    *** Companion;
}
-keepclassmembers class net.hordecraft.android.domain.** {
    *** Companion;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
