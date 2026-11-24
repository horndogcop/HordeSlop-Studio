-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class net.hordeslop.studio.data.remote.**$$serializer { *; }
-keep,includedescriptorclasses class net.hordeslop.studio.data.local.**$$serializer { *; }
-keep,includedescriptorclasses class net.hordeslop.studio.domain.**$$serializer { *; }
-keepclassmembers class net.hordeslop.studio.data.remote.** {
    *** Companion;
}
-keepclassmembers class net.hordeslop.studio.data.local.** {
    *** Companion;
}
-keepclassmembers class net.hordeslop.studio.domain.** {
    *** Companion;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
