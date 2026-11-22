# Poco Optimizer

Aplicativo **Android nativo** em Kotlin e Jetpack Compose/Material 3, inicialmente destinado ao Poco X6 5G com HyperOS. Sem root, anúncios, telemetria ou permissão de internet. **0.1.0 é versão de teste**, não um antivírus certificado nem um “RAM booster”. Não publicado na Play Store.

## Instalar o APK de teste

1. Abra **Actions → Android APK → execução concluída → Artifacts → Poco-Optimizer-test-apk** e extraia o ZIP. Artefatos exigem login no GitHub e expiram em 30 dias.
2. Quando houver pré-release, o APK e o SHA-256 também estarão em **Releases**. Não instale arquivos de outras fontes como se fossem oficiais.
3. No Poco, abra `Poco-Optimizer-0.1.0-test.apk`. Se o HyperOS pedir, permita temporariamente “Instalar apps desconhecidos” **somente para o navegador/gerenciador usado**. Revogue essa permissão após instalar. Não é necessário desativar Play Protect.
4. Abra o app, escolha **Limpeza → Autorizar pasta** e selecione uma subpasta com arquivos de teste. Android 11+ pode impedir acesso à raiz de Downloads e a Android/data/obb.
5. Execute a análise e revise os relatórios. Para excluir, selecione arquivos, toque em revisar e confirme a lista nominal. Comece com cópias descartáveis.

O APK debug tem pacote `br.com.pocooptimizer.test`. O definitivo usa `br.com.pocooptimizer`. A chave debug pode mudar entre runners: uma atualização pode exigir desinstalar o APK anterior (perde dados locais e concessões), ou usar a futura assinatura estável. Não use uma chave debug para distribuição definitiva.

## Funcionalidades reais

- Dashboard: modelo, Android, armazenamento do volume de dados, RAM, bateria, carregamento, temperatura quando disponível, economia e última análise.
- Limpeza: leitura recursiva via SAF das pastas autorizadas, categorias por tamanho/idade/tipo, duplicatas por mesmo tamanho e SHA-256, seleção e confirmação explícita. Nenhum arquivo é classificado como lixo só por ser antigo.
- Exclusão: revalida metadados e conteúdo, impede selecionar todas as cópias de um grupo, confirma uma cópia preservada antes de apagar e registra resultados do provedor. Não atravessa armazenamento privado de outros apps.
- Segurança: apps iniciáveis visíveis, ícones, pacotes, instalador quando informado, permissões declaradas/concedidas e certificados SHA-256; bloqueio seguro e depuração USB; heurísticas identificadas como suspeitas, nunca como vírus confirmado.
- Inspeção local de arquivo/APK: metadados, cabeçalho e SHA-256; parser público do Android para APKs de até 256 MiB. Sem instalação, execução ou envio externo.
- Play Protect: instruções e abertura da Play Store; não finge integração com API inexistente.
- Desempenho: dados públicos, alertas de baixo espaço, pressão de memória, calor e bateria; atalhos oficiais e registro de leituras após o retorno, sem atribuir melhorias automaticamente.
- Ajustes: limites de idade/tamanho, opção de hashes, concessão/revogação de pastas, histórico local limitado a 200 registros, política de privacidade e limpeza dos próprios dados.

## Compilar

Android Studio compatível com AGP 8.9.2, **JDK 21**, Android SDK 35/build-tools 35.0.0. Gradle Wrapper 8.11.1 com SHA-256 fixado; Kotlin 2.1.20; Compose BOM 2025.04.01. São versões estáveis compatíveis, deliberadamente fixadas. Android mínimo 9/API 28, target 35.

```sh
./gradlew :core:test :app:lintDebug :app:assembleDebug
# Emulador ou dispositivo conectado:
./gradlew :app:connectedDebugAndroidTest
# APK:
# app/build/outputs/apk/debug/app-debug.apk
```

Configure `sdk.dir` em `local.properties` ou `ANDROID_HOME`. Abra a raiz no Android Studio. O arquivo local.properties não é versionado.

## Arquitetura

`core` contém modelos, análise, hashing em streaming e exclusão validada com interface FileAccess, testáveis na JVM. `app` contém repositories, SAF, scanner de segurança, DataStore e ViewModel com StateFlow. Composables nos pacotes `ui/dashboard`, `ui/cleaner`, `ui/security`, `ui/performance`, `ui/settings` consomem estados e ações, sem I/O direto. Coroutines usam Dispatchers.IO para arquivos. Indicadores são atualizados apenas com a Activity em RESUMED. Análises manuais são canceláveis; não há serviço permanente.

DataStore armazena preferências e histórico pequeno e limitado; Room não é necessário para esse volume. WorkManager não é usado porque não há tarefas periódicas ou limpeza automática. A análise não precisa nem pede contatos, SMS, câmera, microfone, localização, QUERY_ALL_PACKAGES ou MANAGE_EXTERNAL_STORAGE.

## GitHub Actions e releases

`android.yml` testa a lógica, roda Android Lint, compila os APKs e publica um artefato. Outro job roda testes instrumentados em emulador API 35. Uma pré-release só é publicada após os dois jobs passarem, por `workflow_dispatch` com `publish_test=true` ou tag `test-*`.

`release.yml` é manual e exige declaração de teste físico, versão sem sufixo de teste e assinatura válida. Antes de usar, configure o environment **release-production com required reviewers** e os secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. O YAML sozinho não configura aprovadores no GitHub. Proteja main e exija os checks Android. Gere/guarde a chave definitiva fora do repositório, com backup seguro; nunca inclua senhas ou arquivos de chave em commits. Incremente versionCode/versionName ao distribuir atualizações.

Consulte [limitações e segurança](docs/ANDROID_LIMITS.md), [privacidade](docs/PRIVACY.md) e [plano de testes físicos](docs/TESTING.md). Uma compilação bem-sucedida não substitui teste no Poco real.
