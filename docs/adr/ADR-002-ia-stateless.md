# ADR-002: Microsserviço de IA Analítica Desacoplado e 100% Stateless

## Status
Aceito

## Contexto
O algoritmo de recomendação de preço ótimo baseia-se em regressões econométricas log-log e otimização matemática com base em elasticidade da demanda. Havia duas opções de design arquitetural:
1. Embutir bibliotecas de Machine Learning dentro do ecossistema Java ou permitir que o serviço Python acessasse diretamente as tabelas do MySQL.
2. Isolar o motor analítico em um microsserviço Python/FastAPI sem qualquer conexão de banco de dados, operando exclusivamente com dados passados via payload HTTP.

## Decisão
Adotou-se o modelo de **inferência estritamente stateless**. O microsserviço FastAPI:
* Não possui conexão JDBC/SQL nem armazena estado em disco/sessão.
* Carrega os coeficientes econométricos em memória no momento de inicialização (`lifespan`) em modo somente leitura.
* Recebe todas as informações necessárias na requisição (`cost_price`, `current_price`, limites pré-calculados e concorrência).
* Responde a recomendação analítica e as justificativas em formato serializado Decimal de alta precisão.

## Consequências
### Positivas
* **Soberania do Core Transacional:** O backend Java é o único detentor do modelo relacional, das transações ACID e dos locks de concorrência.
* **Escalabilidade Horizontal Independente:** O serviço FastAPI pode escalar em múltiplos containers sem risco de deadlocks ou contenção de conexões no MySQL.
* **Resiliência e Isolamento de Falhas:** Caso a IA caia, fique lenta ou apresente erro, o Resilience4j no Java ativa circuit breaker e fallback gracioso sem comprometer a loja.

### Negativas
* Trafega um payload ligeiramente maior na rede via HTTP (limites operacionais e histórico de concorrentes do item são transmitidos na chamada).