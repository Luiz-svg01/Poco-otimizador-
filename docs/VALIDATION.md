# Registro de validação — 22/11/2026

## Versão 0.1.1-test-debug (versionCode 2)

Correção do crash de inicialização descrita em [STARTUP_FIX_0_1_1.md](STARTUP_FIX_0_1_1.md). Sem teste físico no Poco X6 5G/HyperOS 3 nesta sessão.

### Local

- JDK 21, Gradle 8.11.1, SDK/build-tools 35.
- 20 testes JVM: 20 passaram, zero falhas/erros/ignorados.
- Build debug, validation e APK de instrumentação: passaram.
- Lint debug e validation: zero erros. Há avisos de versões mais recentes de dependências e sugestões KTX.
- Auditoria do APK real: sem FixtureDocumentsProvider no Manifest ou DEX; sem runner de instrumentação; sem permissões desnecessárias. Assinatura v2 válida; alinhamento ZIP e ELF arm64-v8a/x86_64 de pelo menos 16 KiB.
- Regressão negativa: o mesmo auditor rejeitou o APK 0.1.0 distribuído pela CI por conter o provider de testes. Resultado esperado, que comprova a detecção desse vazamento.
- Sintaxe dos scripts Python/Bash verificada.

### Emuladores e distribuição

A execução [45040746740](https://github.com/Luiz-svg01/Poco-otimizador-/actions/runs/45040746740) terminou com **sucesso** em todos os jobs (`build`, `device-test (API 35)`, `device-test (API 36)`, `validated-apk`, `publish-test`). Código do aplicativo testado: `1d54534283509a0a5ed17909218e700e878ff7fa`.

| Verificação | Android 15 / API 35 | Android 16 / API 36 |
| --- | --- | --- |
| APK exato da distribuição: três aberturas a frio e processo mantido | Passou | Passou |
| Análise real, navegação por todas as centrais e retorno do segundo plano | Passou | Passou |
| Quatro testes instrumentados: recriação da Activity, navegação/análise, progresso conhecido/desconhecido e exclusão SAF com hash/cópia preservada | 4 passaram | 4 passaram |

20 testes JVM passaram; lint debug/validation sem erros. Não houve falhas na execução final. A matriz executa quatro testes instrumentados em cada versão (oito execuções), além do teste externo do APK. A versão usa target/compile SDK 35 e foi executada também no Android 16; isso não certifica particularidades do HyperOS 3.

- [Pré-release 0.1.1](https://github.com/Luiz-svg01/Poco-otimizador-/releases/tag/test-45040746740)
- [APK verificado](https://github.com/Luiz-svg01/Poco-otimizador-/releases/download/test-45040746740/Poco-Optimizer-0.1.1-test.apk)
- Artefato `Poco-Optimizer-test-apk` na execução acima, criado somente após a matriz passar.
- SHA-256: `a55e4999b78793955d2cae683082360e5cf8de338c814d774060628726c57de9`
- APK publicado: 60.140.221 bytes, baixado novamente da URL pública e validado com o checksum, apksigner, auditoria do Manifest/DEX e alinhamento ZIP/ELF de 16 KiB.

Os artefatos `device-test-reports-api-35` e `device-test-reports-api-36` contêm relatórios, XML de resultados, logcat e screenshots. O buffer de crashes da validação final do Android 16 está vazio. Alterações posteriores apenas nesta documentação não modificam o APK validado.

## Falha anterior (0.1.0)

A execução [44988335393](https://github.com/Luiz-svg01/Poco-otimizador-/actions/runs/44988335393) passou no build/JVM/lint, mas falhou no job device-test antes de executar os testes instrumentados: processo caiu em DocumentsProvider.attachInfo com “Provider must be exported”. O job de pré-release foi bloqueado. Essa falha foi investigada; não foi tratada como sucesso de testes só porque o APK compilou.

## Iteração de execução que detectou outro crash

[45039745246](https://github.com/Luiz-svg01/Poco-otimizador-/actions/runs/45039745246): build, auditoria e três aberturas a frio passaram em cada emulador. A análise falhou nas APIs 35 e 36 com NullPointerException na leitura adiada de `total!!` pelo progresso Compose. Corrigida com captura de valores imutáveis; teste de regressão adicionado. A promoção do APK e a publicação foram bloqueadas nessa execução.

## Testes ainda necessários

Validar o novo APK no Poco X6 5G real com Android 16/HyperOS 3, além de provedores/pastas reais, sensores, TalkBack e política de energia Xiaomi. Use TESTING.md. Se continuar fechando, siga COLLECT_CRASH_LOG.md e envie apenas a exceção do processo do Poco Optimizer.
