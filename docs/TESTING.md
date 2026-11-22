# Validação e critérios de aceite

## Automatizado

- JVM: classificação não destrutiva, datas desconhecidas, tamanhos e tipos, heurísticas sem ameaça confirmada, indicadores de desempenho, SHA-256 conhecido, bytes diferentes de mesmo tamanho, exclusão realmente efetuada em arquivos temporários, cópia preservada, cópia ausente, mutação de conteúdo, falha de provedor, plano expirado, seleção alheia e cancelamento de stream.
- Android Lint: manifest, APIs mínimas, recursos e verificações estáticas.
- Instrumentado: inicialização, recriação da Activity, análise sem pastas autorizadas, navegação e estados reais. O APK debug distribuído também é instalado e aberto três vezes fora da instrumentação, com verificação de processo, análise e navegação Testes SAF adicionais exercitam ContentResolver e DocumentsContract em provedor de teste.
- CI: compila APK debug, testes unitários, lint, emuladores API 35 e 36, artefatos e pré-release após sucesso.

Resultados desta execução são documentados em VALIDATION.md. Não trate a existência de testes como prova de execução; consulte os relatórios e o status real no Actions.

## Pendente em Poco X6 5G físico / HyperOS

Não há acesso a esse dispositivo nesta sessão. Antes de considerar uma versão estável, registrar versão de Android/HyperOS, versão/commit do APK, data, responsável e resultado dos seguintes testes:

1. Instalar APK, abrir em modo avião e confirmar modelo, capacidade do volume, bateria, temperatura e RAM comparando com configurações do sistema. Confirmar indisponíveis onde o sensor não informa.
2. Autorizar uma subpasta local com duas cópias idênticas, arquivo diferente de mesmo tamanho, arquivo grande, vídeo, documento antigo, APK, ZIP e .tmp. Validar caminhos, datas, hashes e categorias; nenhum item deve ser excluído na análise.
3. Autorizar uma pasta pai e uma filha; cada documento deve aparecer apenas uma vez. Testar armazenamento removível e nomes com acentos.
4. Tentar selecionar todas as duplicatas: bloquear a última. Cancelar a confirmação: nenhum arquivo removido. Confirmar uma cópia descartável: verificar presença da restante e os bytes do relatório.
5. Alterar arquivo e apagar cópia preservada antes de confirmar: operação deve ser recusada. Revogar acesso externamente e repetir; informar erro sem crash.
6. Testar negação/cancelamento do seletor, pasta não gravável, arquivo inacessível, provedor que recusa delete e pastas restritas Android/data/obb. Nenhuma permissão ampla deve ser pedida.
7. Cancelar hashes de arquivos grandes, girar a tela e alternar apps. Verificar continuidade durante rotação, cancelamento e ausência de ANR. Matar processo durante limpeza com arquivos descartáveis; conferir histórico incompleto e reanálise sem repetição automática.
8. Segurança: conferir permissões declaradas versus concedidas e origem de instalação; APK válido/corrompido/grande; arquivo removido durante hash. Não deve aparecer “vírus confirmado” por heurística.
9. Abrir Play Store/Play Protect e testar fallback quando loja não está disponível. Abrir telas oficiais de bateria, apps e armazenamento; conferir histórico após retorno sem alegação automática de melhoria.
10. Revogar pasta, reiniciar app e verificar ausência de acesso. Apagar dados locais e verificar histórico vazio, preferências padrão e pastas removidas; arquivos pessoais permanecem.
11. TalkBack, fonte 200%, tamanho de tela grande, contraste, orientação paisagem, recortes e barras do HyperOS. Todos os controles devem permanecer acessíveis por rolagem.
12. Bateria: app em segundo plano por 30 minutos sem serviço permanente. Carga de trabalho com milhares de arquivos; observar consumo, aquecimento, cancelamento e limite explícito de enumeração.

Também validar APIs 28, 30, 33, 35 e posteriores antes de ampliar a promessa de compatibilidade. Um emulador não certifica o comportamento dos provedores, sensores ou gerenciador de energia da Xiaomi.
