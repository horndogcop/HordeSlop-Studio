-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class net.hordecraft.android.data.remote.**$$serializer { *; }
-keepclassmembers class net.hordecraft.android.data.remote.** {
    *** Companion;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
