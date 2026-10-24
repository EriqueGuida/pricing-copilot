# 🏷️ Copiloto de Precificação Inteligente

![Status](https://img.shields.io/badge/Status-Concluido-green?style=for-the-badge)
![Milestone](https://img.shields.io/badge/Milestone-11%2F11%20Conclu%C3%ADdas-blue?style=for-the-badge)

---

## O Problema & A Solução

No varejo digital de alta rotação (Eletrônicos, Gaming e Casa Inteligente), a precificação via planilhas gera lentidão frente à concorrência e riscos críticos de erosão de margem por falhas de digitação ou oscilações de custos.

O **Copiloto de Precificação Inteligente** adota uma arquitetura híbrida e defensiva:
* **A IA Analítica Sugere:** Um microsserviço econométrico em Python estima a elasticidade-preço da demanda e recomenda o ponto de maximização de lucro.
* **O Backend Java Decide e Audita:** O core transacional em Java atua como o **árbitro supremo de negócio**. A IA nunca é a fonte da verdade. Toda recomendação passa obrigatoriamente pelas regras matemáticas estritas do Java, sendo recortada em janelas seguras de margem e volatilidade antes de virar preço ativo de catálogo. Qualquer mutação gera histórico de auditoria imutável com proteção física no banco de dados.

---

## 🎓 Contexto Acadêmico e Uso de IA (Transparência Técnica)

Este projeto nasceu como um desafio pessoal para conectar os pontos fundamentais que venho estudando desde o início da minha jornada em Tecnologia: os cursos **CS50x, CS50p e CS50 SQL de Harvard**, o curso Técnico em Desenvolvimento de Sistemas no **SENAI** e a graduação em Sistemas de Computação na **UFF (via CEDERJ)**.

Meu objetivo inicial era unir meus conhecimentos básicos de **Modelagem de Dados (MySQL)**, **APIs REST/CRUD com Java e Spring Boot** (vistos no SENAI) e uma introdução prática a **Modelos de IA em Python** (explorada em um workshop na UFF utilizando Google Colab).

### Engenharia Assistida por IA como Ferramenta de Estudo
Durante o desenvolvimento, utilizei modelos de IA (LLMs) não apenas para acelerar a escrita de código, mas como um **"Tech Lead / Mentor Virtual"** para compreender como sistemas reais do mercado financeiro e de e-commerce lidam com precisão monetária, concorrência e falhas de rede. Com isso, a arquitetura evoluiu além do escopo básico de sala de aula.

Para manter total transparência com recrutadores e avaliadores técnicos sobre o meu momento atual de carreira (nível Estágio/Iniciante), abaixo está a divisão clara entre o que construí com minha base atual e o que foi implementado sob orientação da IA como laboratório de estudo:

* **O que faz parte da minha base atual (Compreensão Sólida):**
  * Lógica de programação e estruturas de dados em Java e Python (base CS50x / CS50p).
  * Construção de APIs REST, entidades JPA, repositórios e operações de CRUD no Spring Boot (base SENAI).
  * Modelagem de banco de dados relacional no MySQL, constraints e consultas SQL (base CS50 SQL e SENAI).
  * Conceito de integração entre microsserviços via HTTP/JSON e noções introdutórias de regressão/IA em Python.
  * Regras de negócio financeiras fundamentais: por que utilizar `BigDecimal` em vez de `double`/`float` para valores monetários e a lógica de limites de margem (piso e teto).

* **O que foi sugerido e guiado pela IA (Em fase de estudo e exploração):**
  * **Containerização e Testes Avançados:** Configuração de `Docker`, `Testcontainers` e testes baseados em propriedades (*Property-Based Testing* com 300k iterações). Ainda estou dando os primeiros passos em testes automatizados e Docker.
  * **Padrões Avançados do Spring e Resiliência:** Uso de `@Transactional(propagation = REQUIRES_NEW)` em beans isolados, controle de concorrência otimista com `@Version` e configuração de *Circuit Breaker/Retry* com `Resilience4j`. Compreendo conceitualmente o problema que eles resolvem no projeto, mas ainda não domino sua configuração avançada sem consulta.
  * **Triggers de Imutabilidade e ADRs:** A formalização de *Architecture Decision Records* (ADRs) e os triggers de bloqueio de `UPDATE`/`DELETE` nas tabelas de auditoria foram co-criados com a IA para simular boas práticas de *Defense in Depth*.

---

## Roadmap e Status Atual

O desenvolvimento é orientado a 11 marcos executáveis. As camadas fundamentais de banco, infraestrutura e domínio matemático já estão implementadas e testadas:

- [x] **Milestone 1: Fundação & Infraestrutura**  
  *Setup base do projeto Maven, Docker Compose com MySQL 8.0, profiles, Spring Boot Actuator e baseline do Flyway.*
- [x] **Milestone 2: Modelagem Relacional & Integridade de Dados**  
  *DDL completo das tabelas core, constraints de domínio (`CHECK`, `UNIQUE`, `FKs`), triggers de imutabilidade da auditoria (bloqueio de `UPDATE`/`DELETE` em `price_history` e `competitor_price`) e testes com Testcontainers.*
- [x] **Milestone 3: Domínio Puro de Precificação**  
  *Implementação de `PriceBoundsCalculator` e `PriceGuard` com aritmética estrita em `BigDecimal`, arredondamento financeiro (`CEILING`/`FLOOR`), prevalência de margem sobre variação, tratamento da janela subcentavo (ADR-001) e suíte de testes de propriedade com 300.000 iterações.*
- [x] **Milestone 4: Catálogo de Produtos & Autenticação RBAC**  
  *Mapeamento JPA com `@Version`, CRUD de produtos/categorias, Spring Security 6 com JWT stateless, papéis `ADMIN`, `OPERATOR` e `EXECUTIVE`, e documentação OpenAPI/Swagger.*
- [x] **Milestone 5: Sobrescrita Manual, Auditoria & Alertas por Eventos**  
  *Endpoints de override, trava temporária contra a IA (`manual_lock_until`), eventos transacionais (`PriceChangedEvent`) e alertas de quebra de margem ou colapso de variação.*
- [x] **Milestone 6: Ingestão de Preços de Concorrentes**  
  *Arquitetura baseada em Strategy Pattern para ingestão de preços de mercado via CSV multipart e endpoint REST JSON.*
- [x] **Milestone 7: Módulo de IA: Dados Sintéticos & Modelo Econométrico**  
  *Gerador de dados reproduzível com seed fixa, regressão múltipla log-log de elasticidade da demanda e notebook de avaliação simulada contra baseline.*
- [x] **Milestone 8: Serviço FastAPI de Inferência**  
  *Microsserviço Python stateless, contratos Pydantic v2 com `Decimal` em string, estratégia de baseline fallback e medição de latência p95 < 500 ms.*
- [x] **Milestone 9: Integração Resiliente Java ↔ IA**  
  *Orquestrador de ciclos de precificação (`PricingRunService`), Resilience4j (timeout de 2s, retry e circuit breaker) e processamento transacional isolado por produto.*
- [x] **Milestone 10: Relatórios de Auditoria & Indicadores (KPIs)**  
  *Exportação de histórico em CSV via streaming de baixo consumo de memória, endpoints de métricas agregadas via SQL nativo e parser de NF-e.*
- [x] **Milestone 11: Orquestração Completa, CI/CD & DoD Final**  
  *Docker Compose unificado de produção/demonstração, pipeline do GitHub Actions, formalização dos ADRs e roteiro de apresentação técnica.*

---

## Stack Tecnológica (Ativa)

| Componente | Tecnologia | Função no Projeto |
| :--- | :--- | :--- |
| **Linguagem Core (Backend)** | Java 17 LTS | Tipagem forte, records imutáveis e controle monetário com `BigDecimal`. |
| **Framework Base** | Spring Boot 3.3+ | API REST, injeção de dependências, segurança JWT e métricas com Actuator. |
| **Microsserviço de IA** | Python 3.11 & FastAPI | Inferência econométrica (OLS), validação com Pydantic v2 e execução stateless. |
| **Resiliência** | Resilience4j | Proteção da integração HTTP com Circuit Breaker e Retry configurados. |
| **Persistência / BD** | MySQL 8.0 | Mecanismo InnoDB transacional com constraints rígidas e triggers de auditoria. |
| **Database Migrations**| Flyway 10.x | Versionamento sequencial e determinístico do schema do banco de dados. |
| **Testes de Integração**| Testcontainers & Failsafe | Subida automatizada de instâncias reais do MySQL 8.0 durante o ciclo `verify`. |
| **Testes Unitários** | JUnit 5, AssertJ & jqwik | Testes parametrizados e testes baseados em propriedades de larga escala. |
| **Ambiente Local** | Docker & Compose | Provisionamento orquestrado do MySQL, serviço de IA e backend Java. |

---

## Quickstart

### Pré-requisitos
* **Java 17+** instalado e configurado no `PATH`
* **Docker** e **Docker Compose** ativos na máquina

### 1. Clonar o repositório
```bash
git clone https://github.com/EriqueGuida/pricing-copilot.git
cd pricing-copilot
```

### 2. Subir a infraestrutura (MySQL + Microsserviço de IA em Python)
```bash
docker compose up -d copiloto-mysql copiloto-ai
```
> O container `copiloto-mysql` iniciará o MySQL 8.0 na porta `3307:3306` e o `copiloto-ai` iniciará o FastAPI na porta `8000:8000`. *(Alternativamente, execute `docker compose up -d --build` para subir toda a stack containerizada, incluindo o backend).*

### 3. Executar a suíte completa de testes (Unitários + Integração com Testcontainers)
```bash
./mvnw clean verify
```

### 4. Iniciar a aplicação Spring Boot localmente (Modo Dev)
```bash
./mvnw spring-boot:run
```

### 5. Validar o status da aplicação
Em outro terminal, faça uma chamada ao Actuator:
```bash
curl http://localhost:8080/actuator/health
```
Resposta esperada:
```json
{"status":"UP"}
```

---

## Documentação do Projeto

Para consultar a especificação completa, formulações matemáticas de margem e elasticidade, contratos de payload JSON e decisões de arquitetura detalhadas, acesse o documento mestre:

👉 **[Especificação Técnica e PRD Oficial](./docs/SPECIFICATION.md)**