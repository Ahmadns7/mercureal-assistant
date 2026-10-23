# no custom rules yet

# --- MercuReal release rules ---
# JNI: native methods and the classes that call them must keep their names.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.ahmadns7.mercureal.assistant.engine.** { *; }
# sherpa-onnx ships JNI bindings that look classes up by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# DataStore/Room are reflection-safe with their shipped rules; keep ours explicit.
# Keep line numbers for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
