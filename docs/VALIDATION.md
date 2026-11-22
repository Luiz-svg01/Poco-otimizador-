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

A execução final da CI, os resultados de Android 15/API 35 e Android 16/API 36 e os links de download serão registrados aqui após sua conclusão. A existência deste plano não comprova execução. O APK público só é promovido após as verificações em ambos os emuladores passarem.

## Falha anterior (0.1.0)

A execução [44988335393](https://github.com/Luiz-svg01/Poco-otimizador-/actions/runs/44988335393) passou no build/JVM/lint, mas falhou no job device-test antes de executar os testes instrumentados: processo caiu em DocumentsProvider.attachInfo com “Provider must be exported”. O job de pré-release foi bloqueado. Essa falha foi investigada; não foi tratada como sucesso de testes só porque o APK compilou.

## Testes ainda necessários

Validar o novo APK no Poco X6 5G real com Android 16/HyperOS 3, além de provedores/pastas reais, sensores, TalkBack e política de energia Xiaomi. Use TESTING.md. Se continuar fechando, siga COLLECT_CRASH_LOG.md e envie apenas a exceção do processo do Poco Optimizer.
