# ADR-005: Isolamento Transacional por Produto (REQUIRES_NEW) e Resiliência Fail-Closed

## Status
Aceito

## Contexto
Durante a execução de um ciclo de precificação em lote (`PricingRun`), o backend Java consulta o microsserviço Python pela rede e atualiza dezenas de produtos. Se todo o ciclo rodasse dentro de uma única transação `@Transactional`:
1. A conexão com o MySQL ficaria retida durante chamadas HTTP externas (risco de esgotamento do pool HikariCP).
2. Uma falha de validação ou conflito de concorrência (`@Version`) em um único SKU reverteria (*rollback*) todos os outros produtos já processados no lote.

## Decisão
1. **Ciclo Externo Não-Transacional:** O método orquestrador que realiza a chamada HTTP ao serviço Python executa fora de contexto transacional de banco de dados.
2. **Persistência Isolada por SKU (`Propagation.REQUIRES_NEW`):** A atualização de cada produto e a gravação do seu `price_history` ocorrem em um bean separado (`PricingItemProcessor`) anotado com `REQUIRES_NEW`, garantindo atomicidade entre preço e histórico de um mesmo item, mas isolamento total entre SKUs distintos.
3. **Resiliência HTTP e Política Fail-Closed:** A chamada ao Python via `RestClient` aplica timeouts de transporte (1.000 ms de conexão e 2.000 ms de leitura), `@Retry` (2 tentativas com espera fixa de 500 ms apenas para falhas de I/O e HTTP `5xx`) e `@CircuitBreaker` (janela de 10 chamadas, mínimo de 5, limiar de 50%, ignorando erros `4xx`). Caso o serviço Python esteja indisponível, o sistema atua em modo *fail-closed*: não altera o preço vigente do produto e gera o alerta operacional `AI_UNAVAILABLE`.

## Consequências
### Positivas
* Nenhuma conexão de banco fica presa aguardando rede.
* Falhas individuais (como conflito otimista `@Version` por edição manual simultânea) afetam apenas o respectivo SKU, contabilizando erro no run sem perder o restante do lote.

### Negativas e Limitações Conhecidas
* Maior número de transações curtas abertas no banco (uma por produto atualizado) e necessidade de tratar reconciliação caso o processo sofra queda abrupta durante um run.
* O controle otimista (`@Version`) reside na entidade `Product`. Embora o `PricingItemProcessor` recarregue a `Category` e recalcule os limites na transação curta como mitigação, alterações concorrentes na categoria no exato milissegundo entre a leitura e o commit exigiriam versionamento ou lock específico também em `Category`.