# ---------------------------------------------------------------------------
# CS50 Mobile - regras do R8 para o build de release
# O app não usa serialização por reflexão, JNI próprio nem bibliotecas de rede;
# as bibliotecas AndroidX/Kotlin/Compose já trazem as próprias regras (consumer rules).
# Estas regras cobrem o que é do app.
# ---------------------------------------------------------------------------

# Stack traces legíveis nos relatórios de erro
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Application, Activity e Service são instanciados pelo sistema pelo nome do manifesto
-keep class com.natam.cs50mobile.CS50App { *; }
-keep class com.natam.cs50mobile.MainActivity { *; }
-keep class com.natam.cs50mobile.service.TerminalService { *; }

# ViewModels são criados por reflexão (construtor com Application) pelo ViewModelProvider
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }

# Obtém o PID do processo por reflexão (Process.pid() só é público a partir do Android 13).
# Os nomes "pid" ficam em classes do sistema, mas mantemos a classe para não mudar a assinatura.
-keep class com.natam.cs50mobile.core.process.ProcessIds { *; }

# Enums usados por nome (valueOf/values) em estados e resultados
-keepclassmembers enum com.natam.cs50mobile.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Os executáveis do PRoot são arquivos nativos prontos (jniLibs); o R8 não mexe neles.
# Nenhuma classe do app é chamada de código nativo.

# Corrotinas: pontos de entrada carregados por ServiceLoader
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.debug.**

# Avisos de anotações de bibliotecas que o app não usa
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.jetbrains.annotations.**
