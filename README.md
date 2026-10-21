# 🏷️ Copiloto de Precificação Inteligente

![Status](https://img.shields.io/badge/Status-Em%20Desenvolvimento-yellow?style=for-the-badge)
![Milestone](https://img.shields.io/badge/Milestone-8%2F11%20Conclu%C3%ADdas-blue?style=for-the-badge)

---

## O Problema & A Solução

No varejo digital de alta rotação (Eletrônicos, Gaming e Casa Inteligente), a precificação via planilhas gera lentidão frente à concorrência e riscos críticos de erosão de margem por falhas de digitação ou oscilações de custos.

O **Copiloto de Precificação Inteligente** adota uma arquitetura híbrida e defensiva:
* **A IA Analítica Sugere:** Um microsserviço econométrico em Python estima a elasticidade-preço da demanda e recomenda o ponto de maximização de lucro.
* **O Backend Java Decide e Audita:** O core transacional em Java atua como o **árbitro supremo de negócio**. A IA nunca é a fonte da verdade. Toda recomendação passa obrigatoriamente pelas regras matemáticas estritas do Java, sendo recortada em janelas seguras de margem e volatilidade antes de virar preço ativo de catálogo. Qualquer mutação gera histórico imutável com proteção física no banco de dados.

---

## Roadmap e Status Atual

O desenvolvimento é orientado a 11 marcos executáveis. As camadas fundamentais de banco, infraestrutura e domínio matemático já estão implementadas e testadas:

- [x] **Milestone 1: Fundação & Infraestrutura**  
  *Setup base do projeto Maven, Docker Compose com MySQL 8.0, profiles, Spring Boot Actuator e baseline do Flyway.*
- [x] **Milestone 2: Modelagem Relacional & Integridade de Dados**  
  *DDL completo das tabelas core, constraints de domínio (`CHECK`, `UNIQUE`, `FKs`), triggers de imutabilidade relacional (bloqueio de `UPDATE`/`DELETE` em auditoria) e testes com Testcontainers.*
- [x] **Milestone 3: Domínio Puro de Precificação**  
  *Implementação de `PriceBoundsCalculator` e `PriceGuard` com aritmética estrita em `BigDecimal`, arredondamento financeiro (`CEILING`/`FLOOR`), prevalência de margem sobre variação, tratamento da janela subcentavo (ADR-001) e suíte de testes de propriedade com 300.000 iterações.*
- [x] **Milestone 4: Catálogo de Produtos & Autenticação RBAC**  
  *Mapeamento JPA com `@Version`, CRUD de produtos/categorias, Spring Security 6 com JWT stateless, papéis `ADMIN`, `OPERATOR` e `EXECUTIVE`, e documentação OpenAPI/Swagger.*
- [x] **Milestone 5: Sobrescrita Manual, Auditoria & Alertas por Eventos**  
  *Endpoints de override, trava temporária contra a IA (`manual_lock_until`), eventos desacoplados (`PriceChangedEvent`) e alertas de quebra de margem ou colapso de variação.*
- [x] **Milestone 6: Ingestão de Preços de Concorrentes**  
  *Arquitetura baseada em Strategy Pattern para ingestão de preços de mercado via CSV multipart e endpoint REST JSON.*
- [x] **Milestone 7: Módulo de IA: Dados Sintéticos & Modelo Econométrico**  
  *Gerador de dados reproduzível com seed fixa, regressão múltipla log-log de elasticidade da demanda e notebook de avaliação simulada contra baseline.*
- [x] **Milestone 8: Serviço FastAPI de Inferência**  
  *Microsserviço Python stateless, contratos Pydantic v2 com `Decimal` em string, estratégia de baseline fallback e medição de latência p95 < 500 ms.*
- [ ] **Milestone 9: Integração Resiliente Java ↔ IA**  
  *Orquestrador de ciclos de precificação (`PricingRunService`), Resilience4j (timeout de 2s, retry e circuit breaker) e processamento transacional isolado por produto.*
- [ ] **Milestone 10: Relatórios de Auditoria & Indicadores (KPIs)**  
  *Exportação de histórico em CSV via streaming de baixo consumo de memória, endpoints de métricas agregadas via SQL nativo e parser de NF-e.*
- [ ] **Milestone 11: Orquestração Completa, CI/CD & DoD Final**  
  *Docker Compose unificado de produção/demonstração, pipeline do GitHub Actions, formalização dos ADRs e roteiro de apresentação técnica.*

---

##  Stack Tecnológica (Ativa)

| Componente | Tecnologia | Função no Projeto |
| :--- | :--- | :--- |
| **Linguagem Core** | Java 17 LTS | Tipagem forte, records imutáveis e controle monetário com `BigDecimal`. |
| **Framework Base** | Spring Boot 3.3+ | Configuração nativa de injeção de dependências e métricas com Actuator. |
| **Persistência / BD** | MySQL 8.0 | Mecanismo InnoDB transacional com constraints rígidas e triggers ativas. |
| **Database Migrations**| Flyway 10.x | Versionamento sequencial e determinístico do schema do banco de dados. |
| **Testes de Integração**| Testcontainers | Subida automatizada de instâncias reais do MySQL 8.0 durante o ciclo de build. |
| **Testes Unitários** | JUnit 5 & AssertJ | Testes parametrizados e testes baseados em propriedades de larga escala. |
| **Ambiente Local** | Docker & Compose | Provisionamento rápido e isolado de containers de banco de dados. |

---

## Quickstart

### Pré-requisitos
* **Java 17+** instalado e configurado no `PATH`
* **Docker** e **Docker Compose** ativos na máquina

### 1. Clonar o repositório
```bash
git clone https://github.com/EriqueGuida/pricing-copilot.git
cd copiloto-precificacao
```

### 2. Subir o container do banco de dados
```bash
docker compose up -d mysql
```
> O container iniciará o MySQL 8.0 mapeado na porta `3307:3306`.

### 3. Executar a suíte de testes (com Testcontainers)
```bash
./mvnw clean test
```

### 4. Iniciar a aplicação Spring Boot
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