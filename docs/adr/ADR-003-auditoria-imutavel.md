# ADR-003: Auditoria Imutável (Append-Only) com Defesa em Profundidade via Triggers no Banco

## Status
Aceito

## Contexto
Em ambientes de e-commerce e varejo, a rastreabilidade de preços exige o registro histórico de quem alterou o preço de cada produto, quando e sob qual justificativa. Bugs na camada de aplicação, falhas de mapeamento ORM ou comandos SQL acidentais poderiam sobrescrever ou apagar registros históricos nas tabelas `price_history` ou `competitor_price`.

## Decisão
Implementou-se uma estratégia de **defesa em profundidade** combinando aplicação e banco de dados:
1. As tabelas `price_history` e `competitor_price` operam em modelo **Append-Only**.
2. Criaram-se gatilhos relacionais (`BEFORE UPDATE` e `BEFORE DELETE`) no MySQL que abortam operações ordinárias de mutação com `SIGNAL SQLSTATE '45000'`.
3. Toda alteração de preço no catálogo grava obrigatoriamente o respectivo registro em `price_history` na mesma transação ACID (`REQUIRES_NEW` por item).

## Consequências
### Positivas
* **Defesa em Profundidade:** Protege o histórico contra bugs na camada de aplicação (JPA/Hibernate) ou comandos `UPDATE`/`DELETE` acidentais.
* **Atomicidade:** Nenhuma mudança de preço é confirmada no banco sem o respectivo registro em `price_history`.

### Negativas e Limitações Conhecidas
* As triggers operam como proteção em nível de tabela, não constituindo isoladamente garantia forense absoluta contra um administrador de banco de dados (que possua privilégios de `DROP TRIGGER` ou `TRUNCATE`). Em um ambiente produtivo estrito, essa abordagem deve ser combinada com segregação de usuários de banco (um usuário para migrações Flyway e outro de runtime restrito a `SELECT`/`INSERT` em `price_history`).