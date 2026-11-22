# Registro de validação — 22/11/2026

Versão: 0.1.0-test-debug. Projeto desenvolvido nesta sessão sem acesso a um Poco X6 5G físico.

## Evidência local

- JDK 21, Gradle Wrapper 8.11.1, SDK/build-tools 35.
- Compilação Android debug e APK de testes instrumentados concluídas.
- Testes JVM executados com sucesso; suite expandida para 19 casos cobrindo análise e operações em arquivos reais temporários.
- Android Lint executado sem erros na compilação inicial. Avisos de versões mais recentes de bibliotecas e sugestões KTX não impedem o build; as versões fixadas são estáveis e compatíveis.
- Assinatura APK debug verificada por apksigner (v2).
- Manifest do APK inspecionado: sem permissões de internet, armazenamento amplo, câmera, localização, SMS, contatos ou acessibilidade. Apenas permissão interna de receiver do AndroidX.

## Validação em CI

O workflow Android APK executa novamente testes, lint, compilação e testes instrumentados em emulador API 35. O resultado real e a URL serão acrescentados após a execução. Nenhum teste físico foi realizado; siga TESTING.md antes de promover para versão estável.
