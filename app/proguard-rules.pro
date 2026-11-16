# Preserve source file names and line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable

# Retrofit service interface in pure-JVM :nga-sdk module.
-keep,allowobfuscation interface dev.nagi.sdk.NgaApi {
    <methods>;
}
