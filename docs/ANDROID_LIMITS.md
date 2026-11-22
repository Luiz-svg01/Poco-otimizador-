# Limites do Android e decisões de segurança

## Escopo de armazenamento

O app usa exclusivamente Storage Access Framework para arquivos pessoais. Não solicita MediaStore amplo, “todos os arquivos”, root, acessibilidade ou permissões privilegiadas. O usuário concede árvores pelo seletor oficial. Uma subpasta de Downloads pode ser autorizada quando a raiz é bloqueada. Áreas privadas de outros apps, Android/data e Android/obb não podem ser limpas. Vá às configurações oficiais para limpar cache de outro app.

A enumeração é limitada a 25.000 entradas, 5.000 diretórios e profundidade 40. Resultados parciais e falhas são informados. Documentos virtuais não são lidos; tamanho desconhecido impede seleção para exclusão. Árvores sobrepostas são deduplicadas por autoridade/documentId. Provedores podem omitir datas, nomes ou tamanho. A análise depende da honestidade dos metadados e bytes oferecidos pelo provedor. Provedores remotos podem fazer downloads; para operação inteiramente offline escolha armazenamento local.

Duplicatas exigem tamanho conhecido igual e hash SHA-256 completo. Hashes são calculados em streaming com buffer de 128 KiB; cancelamento é verificado a cada bloco. Arquivos com metadados mudando durante leitura são rejeitados. Não se promete descobrir duplicatas fora das áreas autorizadas, links de provedores diferentes ou arquivos que mudem concorrentemente.

## Exclusão confirmada

A análise não chama delete. Um plano nominal é preparado e mostrado antes da confirmação, com bytes totais. O plano expira em cinco minutos. Antes de cada exclusão, metadados e SHA-256 são verificados novamente e cada grupo afetado precisa manter uma cópia acessível de hash idêntico. Todas as cópias conhecidas de um grupo nunca podem ser selecionadas.

A exclusão usa DocumentsContract.deleteDocument sob a concessão do SAF. Sem permissão ou confirmação de resultado, o item recebe erro e não é contabilizado. O app não contorna confirmações do sistema e não repete exclusões silenciosamente. Provedores que exijam fluxos adicionais não suportados devem ser geridos pelo gerenciador oficial. Não há lixeira própria ou promessa de desfazer.

Uma comparação seguida de delete não é atômica: a API pública SAF não oferece “delete-if-hash-matches”. Outro app/provedor pode mudar o conteúdo no intervalo mínimo entre validação e exclusão. Não edite os arquivos durante a operação; não use provedores não confiáveis. A garantia de manter cópias vale para os grupos conhecidos e o instante de revalidação, não contra mudanças externas posteriores.

O relatório soma o tamanho validado apenas dos arquivos cujo delete retornou sucesso e a consulta posterior não retornou o documento. Isso é volume lógico removido, não aumento garantido de espaço físico (lixeira, compressão, nuvem, gravações concorrentes). Há registro inicial, por arquivo e final. Morte de processo/queda de energia pode ocorrer entre exclusão e registro; um histórico sem fechamento indica resultado incerto, exigindo reanálise. Exclusão já confirmada não é retomada automaticamente após morte do processo.

## Segurança

Visibilidade de pacotes limitada a apps com launcher e ao próprio app. GET_PERMISSIONS consulta dados, não concede permissões. As listas de apps e flags podem ser incompletas em perfis de trabalho, Android e HyperOS. Origem nula significa desconhecida, não maliciosa. Certificados são identificadores do signatário, não certificados de reputação. Combinações de permissões podem ter uso legítimo.

Não há engine antimalware, catálogo de vírus, API de reputação nem afirmação de ameaça confirmada. A enumeração Risk.CONFIRMED fica reservada a uma futura fonte confiável e nunca é produzida pelas heurísticas atuais. Play Protect deve ser executado manualmente pela Play Store. Arquivos suspeitos nunca são executados ou instalados para análise.

## Desempenho e bateria

ActivityManager.MemoryInfo, StatFs, ACTION_BATTERY_CHANGED, PowerManager e Build fornecem os valores. O espaço se refere ao volume de dados do app, não necessariamente à capacidade comercial anunciada do celular. RAM disponível não equivale a desempenho; o Android gerencia cache. Temperatura e saúde dependem de sensores fornecidos pelo fabricante.

Não há encerramento forçado, limpeza do cache de terceiros, medição de velocidade da CPU nem atribuição de lentidão a processos específicos. Abertura de configurações é registrada como abertura; alterações só são observadas nos indicadores públicos após o retorno, sem inferência causal. Não há acesso de uso/UsageStats, root ou APIs ocultas.
