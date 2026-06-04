# Release obfuscation policy. The default optimize file enables R8 shrinking
# and optimization; these rules make naming and debug metadata stricter.
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-adaptclassstrings

# JSch resolves SSH algorithms by class name strings. Keep only the bundled
# JSch library readable; application SSH wrappers and all other code still
# follow the normal release obfuscation policy.
-keep class com.jcraft.jsch.** { *; }

# Optional JSch integrations that are not packaged on Android.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

-obfuscationdictionary build/generated/r8/obfuscation-dictionary.txt
-classobfuscationdictionary build/generated/r8/obfuscation-dictionary.txt
-packageobfuscationdictionary build/generated/r8/obfuscation-dictionary.txt

# Do not keep SourceFile or LineNumberTable. Keep only metadata commonly needed
# by Java libraries and runtime annotations.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
