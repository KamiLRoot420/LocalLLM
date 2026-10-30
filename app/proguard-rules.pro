# llama.cpp / ggml
-keep class com.rootslab.localllm.LlamaManager { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.rootslab.localllm.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.rootslab.localllm.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Okio / Ktor CIO
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
