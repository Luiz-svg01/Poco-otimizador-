# Coletar o erro no Poco X6 5G (Android 16 / HyperOS 3)

Se a versão nova ainda fechar, estes registros permitem distinguir o erro corrigido de outro problema específico do dispositivo.

## Com computador e ADB (sem root)

1. Instale Android SDK Platform Tools a partir do site oficial do Android no computador.
2. No Poco, ative as opções do desenvolvedor e **Depuração USB**. Conecte por USB e autorize somente o seu computador quando o aparelho solicitar. Não é necessário habilitar root nem alterar Play Protect.
3. Abra o terminal na pasta do Platform Tools. No Windows PowerShell, pode ser necessário usar `./adb` no lugar de `adb`.
4. Execute:

```sh
adb devices
adb shell am force-stop br.com.pocooptimizer.test
adb shell am start -W -n br.com.pocooptimizer.test/br.com.pocooptimizer.MainActivity
adb logcat -b crash -d -v threadtime > poco-crash.txt
adb shell dumpsys package br.com.pocooptimizer.test > poco-package.txt
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

O pacote da versão de teste é `br.com.pocooptimizer.test`, não o pacote `.validation`. `poco-crash.txt` contém o buffer de crashes; procure o bloco `Process: br.com.pocooptimizer.test`, `FATAL EXCEPTION` e `Caused by`. Envie somente esse bloco, o versionName/versionCode de `poco-package.txt` e as versões Android/HyperOS. O buffer pode conter falhas de outros apps; remova esses trechos antes de compartilhar. Não é necessário enviar um bugreport completo.

Se o buffer de crash estiver vazio, capture somente o processo enquanto ele ainda existe:

```sh
adb shell pidof br.com.pocooptimizer.test
# Substitua PID pelo número retornado; inicie a captura antes de reproduzir a falha:
adb logcat --pid=PID -v threadtime > poco-processo.txt
```

Pare a captura com Ctrl+C após reproduzir o problema. Desative a depuração USB e revogue a autorização do computador quando terminar.

## Sem computador

Se o HyperOS exibir “Poco Optimizer parou de funcionar” com uma opção de detalhes, copie o trecho da exceção e a versão do APK. Se não mostrar detalhes, uma gravação da tela ajuda a identificar o momento da falha, mas não substitui o log de crash.

## Atualização do APK de teste

A chave debug pode mudar entre execuções do GitHub. Se aparecer conflito de assinatura ao instalar 0.1.1, desinstale a versão 0.1.0 e instale novamente. Isso apaga histórico/preferências do Poco Optimizer e revoga suas concessões; não apaga arquivos pessoais nas pastas selecionadas. A futura distribuição definitiva usará uma chave estável protegida por secrets.
