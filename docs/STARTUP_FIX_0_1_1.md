# Correção de inicialização — 0.1.1-test

## Causa comprovada no emulador

Na execução [44988335393](https://github.com/Luiz-svg01/Poco-otimizador-/actions/runs/44988335393), o build e os testes JVM passaram, mas a instrumentação no Android 15 encerrou antes de executar os testes:

```text
Process: br.com.pocooptimizer.test
java.lang.RuntimeException: Unable to get provider
br.com.pocooptimizer.fixtures.FixtureDocumentsProvider:
java.lang.SecurityException: Provider must be exported
    at android.provider.DocumentsProvider.attachInfo(...)
    at android.app.ActivityThread.installProvider(...)
```

O componente de testes estava em `app/src/debug`, portanto entrava no APK debug oferecido como teste. Providers são inicializados antes da Activity: o Android lançava a exceção antes de qualquer tela Compose. O contrato público de DocumentsProvider exige `exported=true`, `grantUriPermissions=true` e proteção por MANAGE_DOCUMENTS. A configuração anterior era inválida. O sintoma informado no Poco X6 5G/Android 16/HyperOS 3 é compatível, mas não há log daquele aparelho para afirmar que foi a única causa nele.

## Correção

- O provedor e seu código foram movidos para `src/validation`, uma variante exclusivamente de testes, com pacote separado `br.com.pocooptimizer.validation`. Ela segue o contrato do Android e protege acesso externo com a permissão do sistema. Isso é uma restrição de acesso ao provedor de teste, **não** uma permissão solicitada pelo APK distribuído.
- `debug` (`br.com.pocooptimizer.test`) e `release` não incluem esse provedor. A CI inspeciona o Manifest e o DEX do APK para impedir sua reintrodução e verifica as permissões efetivas.
- A consulta de concessões de pastas saiu do construtor na thread principal e passou para Dispatchers.IO com tratamento de erros. Uma falha nessa consulta pode ser exibida sem derrubar a inicialização. Isso é proteção adicional, não outra causa comprovada do crash anterior.
- VersionCode passou para 2 e versionName para 0.1.1-test. Limpeza confirmada, hashes, segurança e desempenho foram preservados. Nenhuma limpeza automática, root ou permissão ampla foi adicionada.

Os screenshots do APK no emulador também mostraram ícones de sistema escuros sobre a interface escura. As barras de sistema agora usam explicitamente o estilo escuro, e os rótulos da navegação evitam quebra no meio da palavra em telas estreitas.

## Segundo crash reproduzido durante a análise

A execução [45039745246](https://github.com/Luiz-svg01/Poco-otimizador-/actions/runs/45039745246) comprovou três aberturas bem-sucedidas do APK distribuído em cada API (35 e 36). Ao tocar em analisar, os dois emuladores reproduziram uma NullPointerException em MainActivity.kt:81, na lambda de LinearProgressIndicator usada pelas semantics do Compose.

A expressão `s.total!!` lia novamente um estado delegado que podia mudar para null entre a composição e a avaliação adiada do indicador. A correção extrai AnalysisProgress, recebe os contadores como parâmetros imutáveis e captura uma fração não nula para aquela composição. Etapas sem total usam o indicador indeterminado, sem inventar percentuais. Um teste instrumentado alterna repetidamente totais conhecidos, zero e desconhecidos e consulta as semantics entre atualizações. A versão candidata que reproduziu essa falha não foi promovida nem publicada.

## Verificação de compatibilidade

O aplicativo continua compilado com SDK/target 35 e mínimo 28. Android 16/API 36 pode executar apps target 35; não é necessário aumentar targetSdk para corrigir esse crash. A CI testa a execução no Android 15 e 16, incluindo três aberturas a frio, permanência do processo, análise real sem concessões de pastas, todas as centrais e retorno do segundo plano. Esses testes usam exatamente o APK candidato que será distribuído. Separadamente, a variante validation executa testes Compose (incluindo recriação da Activity) e ContentResolver/DocumentsContract.

Também são verificados assinatura, alinhamento ZIP de 16 KiB e segmentos ELF das bibliotecas nativas arm64-v8a/x86_64. Essas verificações estáticas não substituem um teste em aparelho com páginas de 16 KiB. Não há acesso físico ao Poco/HyperOS nesta sessão; a validação específica do fabricante continua necessária.

## Distribuição protegida

O artefato `candidate-apk-not-validated` é apenas transporte interno entre jobs e não deve ser instalado. O artefato público `Poco-Optimizer-test-apk` só é criado quando **todos** os testes das duas versões Android passam. A pré-release exige o mesmo resultado. A CI guarda logcat, buffer de crash, screenshots e relatórios mesmo se houver falha.

O resultado final desta correção e os links estão em VALIDATION.md. Compilar não é considerado prova de que o app abre.
