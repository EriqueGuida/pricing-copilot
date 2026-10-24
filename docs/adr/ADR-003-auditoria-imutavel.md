# ADR-003: Auditoria Imutável (Append-Only) Enforçada por Triggers Físicas no Banco de Dados

## Status
Aceito

## Contexto
Em ambientes de e-commerce e varejo de grande escala, auditorias contábeis e fiscais exigem a comprovação exata de quem alterou o preço de cada produto, quando e sob qual justificativa. Erros de aplicação, falhas humanas ou injeções de comandos SQL poderiam adulterar o histórico de preços (`price_history`) ou de concorrentes (`competitor_price`).

## Decisão
Implementou-se a proteção de auditoria **no nível mais baixo da infraestrutura física de persistência**:
1. Tabelas `price_history` e `competitor_price` operam em padrão **Append-Only**.
2. Criaram-se gatilhos relacionais (`BEFORE UPDATE` e `BEFORE DELETE`) no MySQL que abortam imediatamente a transação com `SIGNAL SQLSTATE '45000'`.
3. Todo evento de alteração de catálogo gera obrigatoriamente um registro em `price_history` no mesmo escopo transacional ACID.

## Consequências
### Positivas
* **Impossibilidade de Fraude ou Adulteração:** Nem mesmo administradores ou falhas em ORMs (Hibernate/JPA) conseguem atualizar ou remover registros históricos.
* **Conformidade Contábil e Regulatória (SOX / Governança):** Trilha de auditoria 100% confiável para apuração de margens e investigações operacionais.

### Negativas
* Políticas futuras de expurgo de dados antigos ou arquivamento frio (*data cold storage*) exigirão migrações DDL controladas via Flyway para desativação/recriação temporária de triggers durante janelas programadas de manutenção.