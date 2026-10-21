# COPILOTO DE PRECIFICAÇÃO INTELIGENTE
## DOCUMENTO DE ESPECIFICAÇÃO TÉCNICA E REQUISITOS (PRD MESTRE)

---

### 1. Visão Geral e Objetivo do Projeto

O **Copiloto de Precificação Inteligente** é uma plataforma distribuída voltada para a automação, otimização e governança de preços no comércio eletrônico, cobrindo três verticais de negócio:
1. **Eletrônicos de Consumo**
2. **Acessórios de Informática e Gaming**
3. **Dispositivos de Casa Inteligente (Smart Home)**

#### 1.1 O Problema de Negócio
No varejo digital de alta rotação, a atualização manual de preços por planilhas gera gargalos operacionais graves: atraso no acompanhamento da concorrência, perda de oportunidade de venda e risco constante de erosão de margem por erro de digitação de centavos ou falta de sincronia com a variação dos custos de aquisição.

#### 1.2 A Solução Arquitetural
O sistema adota uma **arquitetura híbrida desacoplada**:
* Um serviço analítico em **Python** estima a elasticidade-preço da demanda e recomenda o preço economicamente ótimo.
* Um núcleo transacional robusto em **Java/Spring Boot** atua como o **árbitro supremo de negócio**: valida limites de segurança, calcula faixas de tolerância matemática, toma a decisão final de aplicação, gerencia a concorrência e persiste trilhas de auditoria protegidas por integridade física no banco de dados.

> **Princípio Fundamental:**  
> **A Inteligência Artificial nunca é a fonte da verdade.** Toda sugestão gerada pela IA é estritamente consultiva e deve ser interceptada, recortada em faixas rígidas de proteção de margem e auditada pelo backend Java antes de se tornar o preço oficial de catálogo.

---

### 2. Stack Tecnológica Oficial

| Componente | Tecnologia | Versão | Justificativa Arquitetural |
| :--- | :--- | :--- | :--- |
| **Backend Core** | Java | 17 LTS | Padrão corporativo estável, tipagem forte, alta performance e suporte à semântica moderna (Records, Pattern Matching). |
| **Framework Web Core** | Spring Boot | 3.3+ | Ecossistema padrão de mercado para microserviços transacionais, injeção de dependência e suporte nativo a Jakarta EE 10. |
| **Segurança & RBAC** | Spring Security | 6.x | Modelo de autenticação *stateless* desacoplado via filtros JWT e controle de acesso em tempo de compilação/execução via anotações `@PreAuthorize`. |
| **Persistência / ORM** | Spring Data JPA / Hibernate | 6.x | Gerenciamento de ciclo de vida transacional ACID, mapeamento objeto-relacional estrito e suporte a bloqueio otimista (`@Version`). |
| **Banco de Dados** | MySQL Server | 8.0 | Transacionalidade InnoDB, suporte nativo a constraints declarativas (`CHECK`, `UNIQUE`), índices compostos e gatilhos relacionais (`TRIGGERS`). |
| **Evolução de Schema** | Flyway | 10.x | Versionamento imperativo e reproduzível do banco de dados relacional; imutabilidade e rastreabilidade de DDL. |
| **Tolerância a Falhas** | Resilience4j | 2.x | Implementação de Circuit Breaker, Timeouts e Retries para isolamento de chamadas remotas HTTP entre microsserviços. |
| **Documentação da API** | Springdoc OpenAPI | 2.x | Geração dinâmica de contratos Swagger UI conforme especificação OpenAPI 3.0 / RFC 7807 (`ProblemDetail`). |
| **Engine de IA & API** | Python / FastAPI | 3.11+ / 0.110+ | Ecossistema de análise estatística consolidado, inferência assíncrona de alto desempenho e validação estrita via Pydantic v2. |
| **Modelagem Estatística** | Statsmodels / Scikit-Learn | 0.14+ / 1.4+ | Regressão econométrica log-log, estimação linear de elasticidade e processamento vetorial de séries históricas de venda. |
| **Containerização** | Docker & Compose | 24+ / v2 | Padronização e orquestração de todos os nós do ambiente (MySQL, Backend Java e Microsserviço Python). |
| **Testes de Integração** | Testcontainers | 1.19+ | Execução de testes de integração contra instâncias reais do MySQL 8.0 em containers efêmeros gerenciados automaticamente. |

---

### 3. Arquitetura e Fluxo de Dados

#### 3.1 Diagrama Estrutural da Solução

```
                          ┌──────────────────────────────────────┐
                          │     Swagger UI / Postman / Client    │
                          └──────────────────┬───────────────────┘
                                             │ HTTP REST + Bearer JWT
                                             ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ NÚCLEO TRANSACIONAL JAVA (Spring Boot 3.3 / Porta 8080)                                │
│                                                                                        │
│  [API Layer / Controllers] ──▶ Spring Security (Filtro JWT - RBAC)                     │
│           │                                                                            │
│           ▼                                                                            │
│  [Application Services]                                                                │
│   ├── PricingRunService ──(HTTP)──▶ [Adapter: PriceSuggestionClient]                   │
│   ├── ProductOverrideService        (Resilience4j: Timeout 2s, Retry 1x, CB)           │
│   └── CompetitorIngestionService                     │                                 │
│           │                                          │ JSON (Lotes de até 100 itens)   │
│           ▼                                          │ (Aritmética Decimal em String)  │
│  [Domain Core - Pure Java]                           │                                 │
│   ├── PriceBoundsCalculator (Piso/Teto/Margens)      │                                 │
│   ├── PriceGuard (Validação de Intervalo e Escala)   │                                 │
│   └── Event Publisher (PriceChangedEvent)            │                                 │
│           │                   │                      │                                 │
│           │                   ▼                      │                                 │
│           │        [AlertEventListener]              │                                 │
│           │                                          │                                 │
│           ▼                                          │                                 │
│  [Persistence Layer / Spring Data JPA]               │                                 │
│   └── Repositories (Product, Category, Audit, Alert) │                                 │
└───────────────────┬──────────────────────────────────┼─────────────────────────────────┘
                    │ Conexão Transacional             │
                    │ JDBC (ACID)                      │
                    ▼                                  ▼
┌───────────────────────────────────────┐   ┌────────────────────────────────────────────┐
│ BANCO DE DADOS (MySQL 8 - Porta 3307) │   │ SERVIÇO ANALÍTICO IA (FastAPI - Porta 8000)│
│                                       │   │                                            │
│ ├── category, app_user                │   │ ├── POST /v1/suggestions (Stateless)       │
│ ├── product (@Version)                │   │ ├── GET  /v1/model/info                    │
│ ├── pricing_run, alert                │   │ ├── Pydantic v2 (Validation Engine)        │
│ ├── price_history (Append-Only)       │   │ └── Engine Econométrica (Log-Log)          │
│ │   └── Triggers: BLOQUEIA UPD/DEL    │   └────────────────────────────────────────────┘
│ └── competitor_price (Append-Only)    │                          ▲
│     └── Triggers: BLOQUEIA UPD/DEL    │                          │ Leitura Somente-Leitura
│                                       │                          │ (sales_history)
└───────────────────────────────────────┘                          │
                    └──────────────────────────────────────────────┘
```

#### 3.2 Estrutura de Pacotes por Feature (Backend Java)
O código fonte do backend é estruturado estritamente por funcionalidade de domínio:

```
com.guidaerique.pricing_copilot
├── catalog        # Gestão de categorias e cadastro de produtos
├── pricing        # Orquestração do ciclo de precificação, cálculo puro e guardas
├── competitor     # Ingestão de preços de mercado (CSV e POST direto)
├── audit          # Histórico append-only de alterações e exportações
├── alert          # Event listeners e consolidação de alertas operacionais
├── security       # Filtros JWT, serviços de token e controle RBAC
└── shared         # Exceções globais, handlers RFC 7807 e utilitários
```
*Cada pacote agrupa internamente suas divisões em:* `controller`, `service`, `repository`, `domain` e `dto`.

---

### 4. Modelo de Dados e Integridade Física (MySQL 8.0)

A integridade dos dados financeiros não delega responsabilidade unicamente à aplicação; o banco de dados implementa restrições relacionais, validações de domínio e gatilhos de segurança física.

#### 4.1 Convenções de Tipagem
*   **Valores Monetários:** `DECIMAL(12,2)` — impede qualquer representação em ponto flutuante binário.
*   **Taxas e Percentuais:** `DECIMAL(5,4)` — armazenados como frações absolutas (ex: `0.1500` para 15,00%).
*   **Controle de Versão Concorrente:** `BIGINT NOT NULL DEFAULT 0` — mapeado para `@Version` no JPA.
*   **Datas e Carimbos Temporais:** `DATETIME(3)` ou `DATETIME` manipulados exclusivamente em fuso UTC.

#### 4.2 Esquema Completo das Tabelas (DDL)

```sql
-- 1. Categorias e limites operacionais de margem e volatilidade
CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    vertical ENUM('CONSUMER_ELECTRONICS', 'COMPUTER_GAMING', 'SMART_HOME') NOT NULL,
    min_margin DECIMAL(5,4) NOT NULL,
    max_margin DECIMAL(5,4) NOT NULL,
    max_variation DECIMAL(5,4) NOT NULL,
    CONSTRAINT ck_category_margins CHECK (min_margin > 0 AND min_margin < max_margin AND max_margin < 1),
    CONSTRAINT ck_category_variation CHECK (max_variation > 0 AND max_variation <= 0.5000)
);

-- 2. Usuários do sistema e controle de acesso
CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'OPERATOR', 'EXECUTIVE') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Cadastro mestre de produtos
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(40) NOT NULL UNIQUE,
    ean CHAR(13) UNIQUE,
    name VARCHAR(160) NOT NULL,
    category_id BIGINT NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL,
    current_price DECIMAL(12,2) NOT NULL,
    manual_lock_until DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_product_prices CHECK (cost_price > 0 AND current_price > 0)
);

-- 4. Cadastro de competidores mapeados
CREATE TABLE competitor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Ingestão de preços praticados pelo mercado (Append-only)
CREATE TABLE competitor_price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    competitor_id BIGINT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source ENUM('CSV', 'API') NOT NULL DEFAULT 'CSV',
    CONSTRAINT fk_comp_price_product FOREIGN KEY (product_id) REFERENCES product(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_comp_price_competitor FOREIGN KEY (competitor_id) REFERENCES competitor(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_comp_price_positive CHECK (price > 0),
    INDEX ix_comp_price_lookup (product_id, collected_at DESC)
);

-- 6. Execuções do ciclo de precificação automatizado
CREATE TABLE pricing_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    status ENUM('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED') NOT NULL,
    processed_items INT NOT NULL DEFAULT 0,
    updated_items INT NOT NULL DEFAULT 0,
    skipped_items INT NOT NULL DEFAULT 0,
    error_items INT NOT NULL DEFAULT 0,
    model_version VARCHAR(40) NULL
);

-- 7. Histórico e auditoria de precificação (Append-only)
CREATE TABLE price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    old_price DECIMAL(12,2) NOT NULL,
    new_price DECIMAL(12,2) NOT NULL,
    origin ENUM('AI', 'MANUAL', 'COST_UPDATE') NOT NULL,
    changed_by BIGINT NULL,
    pricing_run_id BIGINT NULL,
    model_version VARCHAR(40) NULL,
    reason VARCHAR(255) NULL,
    explanation JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_history_product FOREIGN KEY (product_id) REFERENCES product(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by) REFERENCES app_user(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_history_run FOREIGN KEY (pricing_run_id) REFERENCES pricing_run(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX ix_history_product_date (product_id, created_at),
    INDEX ix_history_created (created_at)
);

-- 8. Base de treino econométrico (Séries temporais de vendas)
CREATE TABLE sales_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sold_on DATE NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_sales_product FOREIGN KEY (product_id) REFERENCES product(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_sales_positive CHECK (quantity > 0 AND unit_price > 0),
    INDEX ix_sales_product_date (product_id, sold_on)
);

-- 9. Alertas de conformidade e operacionais
CREATE TABLE alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    type ENUM('MARGIN_BREACH', 'VARIATION_LIMIT_HIT', 'LOW_CONFIDENCE', 'AI_UNAVAILABLE') NOT NULL,
    status ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    message VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    resolved_by BIGINT NULL,
    CONSTRAINT fk_alert_product FOREIGN KEY (product_id) REFERENCES product(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_alert_user FOREIGN KEY (resolved_by) REFERENCES app_user(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX ix_alert_status (status, created_at)
);
```

#### 4.3 Triggers de Imutabilidade (Segurança Física contra Fraude e Mutação)
O histórico de preços e as coletas de concorrentes não podem sofrer atualizações ou deleções sob nenhuma circunstância.

```sql
DELIMITER //

CREATE TRIGGER trg_price_history_no_update 
BEFORE UPDATE ON price_history 
FOR EACH ROW 
BEGIN 
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'price_history e imutavel: UPDATE proibido'; 
END //

CREATE TRIGGER trg_price_history_no_delete 
BEFORE DELETE ON price_history 
FOR EACH ROW 
BEGIN 
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'price_history e imutavel: DELETE proibido'; 
END //

CREATE TRIGGER trg_competitor_price_no_update 
BEFORE UPDATE ON competitor_price 
FOR EACH ROW 
BEGIN 
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'competitor_price e imutavel: UPDATE proibido'; 
END //

CREATE TRIGGER trg_competitor_price_no_delete 
BEFORE DELETE ON competitor_price 
FOR EACH ROW 
BEGIN 
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'competitor_price e imutavel: DELETE proibido'; 
END //

DELIMITER ;
```

---

### 5. Regras de Negócio e Cálculos Matemáticos

Toda a lógica matemática de determinação de preços é governada exclusivamente pelo Java na classe `PriceBoundsCalculator`.

#### 5.1 Definições Fundamentais
*   **Margem Comercial Nominal ($m$):**
    $$m = \frac{p - c}{p} \iff p = \frac{c}{1 - m}$$
    Onde $p$ é o preço de venda e $c$ é o custo de aquisição (`cost_price`).

#### 5.2 Limites de Margem e Variação
Dado um produto com custo $c$, preço atual $p_{\text{atual}}$, margem mínima $m_{\text{min}}$, margem máxima $m_{\text{max}}$ e variação máxima permitida por ciclo $v_{\text{max}}$:

1.  **Piso de Margem ($\text{piso\_margem}$):**
    Menor preço que satisfaz a margem mínima. Arredondado obrigatoriamente para **CIMA** (`RoundingMode.CEILING`) para não violar a margem mínima em frações de centavo:
    $$\text{piso\_margem} = \left\lceil \frac{c}{1 - m_{\text{min}}} \right\rceil_2$$
2.  **Teto de Margem ($\text{teto\_margem}$):**
    Maior preço que não ultrapassa a margem máxima da categoria. Arredondado para **BAIXO** (`RoundingMode.FLOOR`):
    $$\text{teto\_margem} = \left\lfloor \frac{c}{1 - m_{\text{max}}} \right\rfloor_2$$
3.  **Piso de Variação ($\text{piso\_variacao}$):**
    Limite inferior de oscilação do preço atual em um ciclo:
    $$\text{piso\_variacao} = \lceil p_{\text{atual}} \cdot (1 - v_{\text{max}}) \rceil_2$$
4.  **Teto de Variação ($\text{teto\_variacao}$):**
    Limite superior de oscilação do preço atual em um ciclo:
    $$\text{teto\_variacao} = \lfloor p_{\text{atual}} \cdot (1 + v_{\text{max}}) \rfloor_2$$
5.  **Intervalo Seguro Efetivo:**
    $$\text{min\_efetivo} = \max(\text{piso\_margem}, \text{piso\_variacao})$$
    $$\text{max\_efetivo} = \min(\text{teto\_margem}, \text{teto\_variacao})$$

#### 5.3 Regra de Prevalência da Margem e Colapso de Intervalo
Se a interseção entre o intervalo de margem e o de variação for vazia ($\text{min\_efetivo} > \text{max\_efetivo}$):
*   **A margem prevalece irrestritamente sobre o limite de variação.**
*   O intervalo **colapsa em um ponto único** ($\text{min\_efetivo} = \text{max\_efetivo} = \text{alvo}$).
*   **Escolha do Alvo:**
    *   Se $p_{\text{atual}} < \text{piso\_margem}$, o alvo é $\text{piso\_margem}$.
    *   Se $p_{\text{atual}} > \text{teto\_margem}$, o alvo é $\text{teto\_margem}$.
*   **Sinalização do Alerta:** Se o alvo escolhido estiver fora do alcance da variação permitida ($\text{alvo} < \text{piso\_variacao}$ ou $\text{alvo} > \text{teto\_variacao}$), o sistema define a flag `variationLimitHit = true`, que instrui o backend a gerar o alerta operacional `VARIATION_LIMIT_HIT`.

#### 5.4 Tratamento da Janela Subcentavo (ADR-001)
Em cenários raros onde o custo é muito baixo e a diferença entre margem mínima e máxima é infinitesimal, o arredondamento para centavos pode gerar $\text{piso\_margem} > \text{teto\_margem}$. Nesse caso de inconsistência subcentavo, **a margem mínima prevalece de forma absoluta** sobre a máxima, fixando o preço-alvo em $\text{piso\_margem}$.

#### 5.5 Regras de Sobrescrita Manual (Override)
*   **Papel OPERATOR:**
    *   Só tem permissão para aplicar um preço contido no intervalo de margem estrito da categoria: $[\text{piso\_margem}, \text{teto\_margem}]$.
    *   Tentativa de aplicar valor fora dessa faixa resulta em HTTP 403 / Domain Validation Exception.
*   **Papel ADMIN:**
    *   Possui prerrogativa para definir qualquer valor $> 0$.
    *   Exige justificativa formal obrigatória (`reason`) contendo no mínimo 10 caracteres.
    *   Se o preço aplicado violar a margem mínima ($p < \text{piso\_margem}$), a operação é autorizada, o histórico é gravado e o sistema emite imediatamente o alerta `MARGIN_BREACH`.
*   **Trava Contra a IA (`manual_lock_until`):**
    *   Toda sobrescrita manual bloqueia o produto contra sugestões da IA por um período determinado (`lockHours`).
    *   Valor padrão: **24 horas**. Valor máximo permitido: **720 horas (30 dias)**.
    *   O lock pode ser destravado a qualquer momento via endpoint específico.

---

### 6. Módulo Analítico de IA e Contratos da API

#### 6.1 Formulação Econométrica (FastAPI / Python)
O microsserviço estima uma regressão linear múltipla log-log por vertical de negócio:
$$\ln(q) = \alpha + \eta \ln(p) + \beta \ln(p_{\text{comp}}) + \sum \gamma_k D_k + \epsilon$$

*   $q$: Quantidade demandada.
*   $p$: Preço praticado pela loja.
*   $p_{\text{comp}}$: Mediana dos concorrentes diretos no produto.
*   $\eta$: Elasticidade-preço direta da demanda ($\eta < 0$).
*   $\beta$: Elasticidade-cruzada em relação aos concorrentes ($\beta > 0$).
*   $D_k$: Dummies de sazonalidade semanal (dia da semana).

#### 6.2 Preço Ótimo e Baseline Fallback
O lucro esperado é dado por $L(p) = (p - c) \cdot q(p)$. Maximizando em relação a $p$:
$$p^* = c \cdot \left( \frac{\eta}{1 + \eta} \right), \quad \text{para } \eta < -1$$

*   **Critérios de Baixa Confiança (`LOW_CONFIDENCE`):**
    O modelo declara baixa confiança e ativa a estratégia `BASELINE_FALLBACK` caso:
    1.  A elasticidade estimada seja inelástica ou aberrante: $\eta \ge -1$.
    2.  O coeficiente de determinação da regressão $R^2 < 0.30$.
    3.  A amostragem de vendas do produto/categoria seja inferior a 30 observações.
*   **Política de Preço no Baseline:**
    *   Se existirem concorrentes: $\text{preço\_sugerido} = \text{mediana}(p_{\text{comp}}) \times 0.99$.
    *   Se não existirem concorrentes: $\text{preço\_sugerido} = p_{\text{atual}}$.
*   **Operação de Recorte (Clip):**
    O preço final sugerido pela IA deve sofrer clipping nos limites recebidos no payload:
    $$p_{\text{sugerido}} = \max\left(\text{min\_price}, \min\left(p^*, \text{max\_price}\right)\right)$$

#### 6.3 Contratos de Interface (Pydantic v2)
Todos os valores monetários trafegam serializados como **string** no JSON para garantir precisão absoluta nas pontas.

```python
from decimal import Decimal
from typing import Literal, List, Optional
from pydantic import BaseModel, Field

class SuggestionItem(BaseModel):
    sku: str
    category_id: int
    cost_price: Decimal = Field(gt=0, decimal_places=2)
    current_price: Decimal = Field(gt=0, decimal_places=2)
    min_price: Decimal = Field(gt=0, decimal_places=2)
    max_price: Decimal = Field(gt=0, decimal_places=2)
    competitor_prices: List[Decimal] = Field(default_factory=list)

class SuggestionRequest(BaseModel):
    run_id: int
    items: List[SuggestionItem] = Field(min_length=1, max_length=500)

class Explanation(BaseModel):
    elasticity: float
    competitor_median: Optional[Decimal]
    unconstrained_optimum: Optional[Decimal]
    clipped_by: Literal['MIN_PRICE', 'MAX_PRICE', 'NONE']
    confidence: Literal['HIGH', 'LOW']

class SuggestionResult(BaseModel):
    sku: str
    suggested_price: Decimal = Field(gt=0, decimal_places=2)
    strategy: Literal['ELASTICITY', 'BASELINE_FALLBACK']
    explanation: Explanation

class SuggestionResponse(BaseModel):
    model_version: str
    results: List[SuggestionResult]
```

#### 6.4 Endpoints do Microsserviço Python
*   `GET /health`: Retorna status de integridade do serviço analítico.
*   `GET /v1/model/info`: Versão ativa do modelo econométrico, data de corte e métricas consolidadas.
*   `POST /v1/suggestions`: Processa o lote de sugestões de forma **estritamente stateless**. O Python não se conecta ao banco de dados e não armazena estado das requisições.

---

### 7. Requisitos Funcionais do Sistema (RF)

*   **RF-01: Catálogo de Produtos e Categorias (Must)**
    *   CRUD administrativo de categorias contendo limites percentuais (`min_margin`, `max_margin`, `max_variation`).
    *   CRUD de produtos com SKU único, EAN opcional, custo e preço atual.
    *   *Aceite:* Não permitir cadastro de produto com preço que viole os limites de margem da categoria associada; rejeitar custos ou preços negativos ou zerados.
*   **RF-02: Ingestão de Preços de Concorrentes (Must)**
    *   Importação em massa via CSV (`multipart/form-data`) e ingestão individual via JSON (`POST /api/competitor-prices`).
    *   Associação obrigatória por SKU ou EAN existente no catálogo.
    *   *Aceite:* Registros com preços $\le 0$ são descartados com log estruturado de erro de linha; linhas inválidas não quebram o lote; dados válidos são inseridos como registros imutáveis com `collected_at`.
*   **RF-03: Ciclo Autônomo de Precificação (Must)**
    *   Disparo orquestrado via `@Scheduled` e endpoint manual `POST /api/pricing/runs`.
    *   Executa seleção de produtos elegíveis (ativos e com `manual_lock_until < now()` ou nulo).
    *   Calcula faixas com `PriceBoundsCalculator`, divide em lotes de até 100 itens e invoca o serviço Python.
    *   *Aceite:* Cada produto tem seu preço atualizado em **transação individual isolada** (`REQUIRES_NEW`); falha de validação ou exceção em um item não afeta a aplicação dos demais; a entidade `pricing_run` registra totalizadores de sucesso, alterados, ignorados e falhas.
*   **RF-04: Sobrescrita Manual e Trava de Segurança (Must)**
    *   Endpoints `PUT /api/products/{id}/price` e `DELETE /api/products/{id}/lock`.
    *   *Aceite:* O operador é barrado (HTTP 403) se tentar precificar fora dos limites de margem; o administrador é autorizado para qualquer valor $> 0$, mas se violar a margem mínima, gera alerta `MARGIN_BREACH` e exige justificativa $\ge 10$ caracteres; o produto fica bloqueado contra a IA pelo prazo estipulado.
*   **RF-05: Auditoria Contínua e Imutabilidade (Must)**
    *   Todo e qualquer evento de mudança de preço insere obrigatoriamente um registro em `price_history` na mesma transação lógica da mudança.
    *   *Aceite:* Qualquer tentativa de executar `UPDATE` ou `DELETE` diretamente na tabela `price_history` resulta em falha do comando no MySQL via Trigger SQLSTATE '45000'.
*   **RF-06: Gestão de Alertas Desacoplada (Must)**
    *   Alertas operacionais gerados através de eventos de domínio (`PriceChangedEvent`).
    *   Tipos: `MARGIN_BREACH`, `VARIATION_LIMIT_HIT`, `LOW_CONFIDENCE`, `AI_UNAVAILABLE`.
    *   *Aceite:* Alertas são instanciados como `OPEN`; endpoint `PATCH /api/alerts/{id}/resolve` permite que administradores ou operadores marquem o alerta como `RESOLVED`, preenchendo o carimbo `resolved_at`.
*   **RF-07: Segurança Baseada em Papéis (RBAC) (Must)**
    *   Proteção via tokens JWT com assinatura digital.
    *   *Aceite:* Matriz de autorização respeitada integralmente:

| Operação de Negócio | Endpoint | ADMIN | OPERATOR | EXECUTIVE |
| :--- | :--- | :---: | :---: | :---: |
| Autenticação / Login | `POST /api/auth/login` | Público | Público | Público |
| Gestão de Categorias | `POST/PUT/DELETE /api/categories/**` | Sim | Não | Não |
| Gestão de Produtos | `POST/PUT /api/products/**` | Sim | Sim | Não |
| Override Dentro da Margem | `PUT /api/products/{id}/price` | Sim | Sim | Não |
| Override Fora da Margem | `PUT /api/products/{id}/price` | Sim | Não | Não |
| Destravar Lock Manual | `DELETE /api/products/{id}/lock` | Sim | Sim | Não |
| Disparar Pricing Run | `POST /api/pricing/runs` | Sim | Sim | Não |
| Ingestão Concorrentes | `POST /api/competitor-prices/**` | Sim | Sim | Não |
| Visualizar Histórico | `GET /api/products/{id}/history` | Sim | Sim | Sim |
| Visualizar Alertas / KPIs | `GET /api/alerts`, `GET /api/kpis` | Sim | Sim | Sim |
| Resolver Alertas | `PATCH /api/alerts/{id}/resolve` | Sim | Sim | Não |
| Exportar Relatório CSV | `GET /api/reports/price-history.csv` | Sim | Sim | Sim |

*   **RF-08: Exportação de Histórico em CSV (Should)**
    *   Endpoint `GET /api/reports/price-history.csv?from=&to=` com streaming HTTP direto da base.
    *   *Aceite:* Processamento de grandes volumes sem elevação de consumo de memória RAM via `StreamingResponseBody`.
*   **RF-09: Endpoints de Indicadores Consolidados (KPIs) (Should)**
    *   Endpoint `GET /api/kpis` consolidando margem média ponderada por categoria, total de itens em risco de margem e assertividade dos runs.
    *   *Aceite:* Consultas executadas inteiramente agregadas no banco relacional.
*   **RF-10: Atualização de Custo por Nota Fiscal Eletrônica (Should)**
    *   Endpoint `POST /api/invoices/import` recebendo arquivo XML de NFe simplificado.
    *   *Aceite:* Idempotência estrita pela chave de acesso de 44 dígitos; atualização atômica do `cost_price` do produto; se a margem cair abaixo da mínima, aciona recálculo automático com subida de preço para `piso_margem` (origem `COST_UPDATE`).

---

### 8. Requisitos Não-Funcionais (RNF)

*   **RNF-01 (Segurança):** Spring Security 6, JWT com assinatura criptográfica válida, senhas persistidas exclusivamente sob algoritmo BCrypt com fator de custo $\ge 10$.
*   **RNF-02 (Aritmética Financeira):** Uso obrigatório e irrestrito de `BigDecimal` no ecossistema Java. É formalmente proibido o uso de `float` ou `double` em qualquer operação com moeda ou percentual de margem.
*   **RNF-03 (Concorrência e ACID):** Uso de `@Version` na entidade `Product` para controle de concorrência otimista. Em caso de corrida de escrita simultânea (ex: run da IA finalizando no mesmo milissegundo de um override humano), o JPA aborta uma das operações com `OptimisticLockingFailureException`, impedindo corrupção silenciosa.
*   **RNF-04 (Resiliência):** Comunicação HTTP entre Java e Python encapsulada por Resilience4j: timeout de 2000 ms, 1 retry automático e circuit breaker configurado. Se o serviço de IA estiver inacessível, o backend ativa fallback transparente: o preço do catálogo não é alterado, o produto é marcado como ignorado e gera-se alerta `AI_UNAVAILABLE`.
*   **RNF-05 (Performance Analítica):** Latência de inferência p95 no microsserviço Python $\le 500$ ms para requisições com lotes padrão de 50 produtos.
*   **RNF-06 (Observabilidade):** Implementação de filtro de requisição para injeção e propagação do cabeçalho `X-Correlation-ID` entre as chamadas do Java e os logs do FastAPI. Métricas de runtime expostas via `/actuator/health` e `/actuator/metrics`.
*   **RNF-07 (Tratamento de Exceções):** Padronização de mensagens de erro HTTP via especificação RFC 7807 (`ProblemDetail`) contendo tipo, título, status e detalhe da violação.
*   **RNF-08 (Infraestrutura Reprodutível):** Subida integral da solução (banco, backend Java e serviço Python) via comando único `docker compose up --build`.

---

### 9. Roadmap de Execução por Milestones

O projeto é dividido em **11 milestones encadeadas**. Cada etapa possui dependências claras, escopo de implementação e critérios rastreáveis de conclusão.

```
[M1: Fundação] ──▶ [M2: Banco de Dados] ──▶ [M3: Domínio de Preço] ──▶ [M4: Catálogo & RBAC]
  (CONCLUÍDA)         (CONCLUÍDA)              (CONCLUÍDA)                   (CONCLUÍDA)
                                                                                  │
  ┌───────────────────────────────────────────────────────────────────────────────┘
  ▼
[M5: Override & Auditoria] ──▶ [M6: Ingestão de Concorrentes] ──▶ [M7: Dados & Modelo IA]
        (CONCLUÍDA)                      (CONCLUÍDA)                    (CONCLUÍDA)
                                                                          │
  ┌───────────────────────────────────────────────────────────────────────┘
  ▼
[M8: Serviço FastAPI] ──▶ [M9: Integração Java ↔ IA] ──▶ [M10: Relatórios/KPIs] ──▶ [M11: DoD Final]
    (CONCLUÍDA)                  (A INICIAR)
```

---

#### Milestone 1 — Infraestrutura e Fundação
*Status:* **Concluída e Validada no Repositório.**
*   **Entregáveis:** Estrutura Maven/Java 17, Docker Compose com MySQL 8.0 (porta 3307), Spring Boot Actuator, perfis `dev` e `test`, baseline Flyway `V1`.
*   **Critério de Aceite:** Aplicação sobe, executa a migration baseline e `/actuator/health` retorna `{"status":"UP"}`.

#### Milestone 2 — Modelagem Relacional e Integridade do Banco
*Status:* **Concluída e Validada no Repositório.**
*   **Entregáveis:** Migrations Flyway `V2__create_core_tables.sql` e `V3__create_immutability_triggers.sql`. Tabelas com constraints `CHECK`, `UNIQUE`, `FOREIGN KEY (RESTRICT)` e triggers em `price_history` e `competitor_price`.
*   **Critério de Aceite:** Testes de integração em `ImmutabilityTriggersIT`, `BusinessConstraintsIT` e `ReferentialIntegrityIT` passam 100% contra MySQL real rodando em Testcontainers. Operações de mutação nas tabelas auditadas falham com erro SQLSTATE 45000.

#### Milestone 3 — Domínio Puro de Precificação
*Status:* **Concluída e Validada no Repositório.**
*   **Entregáveis:** Classes puras em `pricing.domain`: `PriceBoundsCalculator`, `PriceBounds`, `PriceGuard` e exceção de domínio. Implementação das regras de arredondamento `CEILING`/`FLOOR`, prevalência de margem e suporte à janela subcentavo (ADR-001).
*   **Critério de Aceite:** Cobertura de testes unitários $\ge 80\%$, teste de propriedade executando 300.000 iterações com semente fixa aprovado em `PriceBoundsCalculatorPropertyTest`. Rejeição no `PriceGuard` de valores monetários com precisão superior a 2 casas decimais.

---

#### Milestone 4 — Catálogo de Produtos e Segurança RBAC
*Status:* **A Iniciar.**
*   **Objetivo:** Disponibilizar os recursos transacionais de catálogo protegidos por autenticação stateless JWT e autorização por papéis de usuário.
*   **Tarefas Práticas e Acionáveis:**
    1.  Mapear entidades JPA com anotações de tabela e constraints: `Category`, `Product` (com anotação `@Version private Long version`), `AppUser`.
    2.  Criar os repositórios JPA: `CategoryRepository`, `ProductRepository`, `AppUserRepository`.
    3.  Implementar DTOs de request e response para categorias e produtos, com validações Bean Validation (`@NotNull`, `@Positive`, etc.).
    4.  Implementar `CategoryService` e `ProductService` garantindo regras de cadastro (preço inicial dentro dos limites da categoria).
    5.  Implementar a camada de segurança com Spring Security 6:
        *   Provedor de token JWT (`JwtService`): assinatura HMAC-SHA256, tempo de expiração e parsing de claims.
        *   Filtro `JwtAuthenticationFilter` injetando o contexto de segurança.
        *   `AuthController` expondo `POST /api/auth/login`.
    6.  Configurar `@EnableMethodSecurity` com anotações `@PreAuthorize` nos controllers de catálogo:
        *   `CategoryController`: Leitura liberada para todos autenticados; criação/edição restrita a `ADMIN`.
        *   `ProductController`: Leitura liberada; cadastro/atualização permitido para `ADMIN` e `OPERATOR`.
    7.  Implementar `GlobalExceptionHandler` tratando exceções com retornos padronizados no padrão RFC 7807 (`ProblemDetail`).
    8.  Configurar Springdoc para exibição da documentação Swagger UI com suporte ao esquema de autenticação `bearerAuth`.
*   **Critério de Conclusão / Testes:**
    *   Testes de integração com `MockMvc` validam a matriz RBAC: usuário `EXECUTIVE` recebe HTTP 403 Forbidden ao tentar salvar ou editar produtos/categorias; `OPERATOR` recebe 403 ao tentar criar categoria; login inválido retorna HTTP 401 Unauthorized.
    *   Swagger acessível em `/swagger-ui.html` documentando todas as rotas e esquemas de dados.

---

#### Milestone 5 — Sobrescrita Manual, Auditoria e Alertas por Eventos
*Status:* **Pendente (Depende de M4).**
*   **Objetivo:** Implementar o fluxo transacional de override manual de preço, garantir a persistência atômica da auditoria e disparar alertas via eventos desacoplados.
*   **Tarefas Práticas e Acionáveis:**
    1.  Mapear entidades JPA: `PriceHistory` e `Alert`.
    2.  Implementar `PriceOverrideService`:
        *   Obter produto e categoria; invocar `PriceBoundsCalculator`.
        *   Validar permissões: se o usuário logado for `OPERATOR`, lançar exceção caso o preço informado não pertença ao intervalo $[\text{piso\_margem}, \text{teto\_margem}]$; se for `ADMIN`, permitir qualquer valor positivo desde que acompanhado de `reason` $\ge 10$ caracteres.
        *   Definir carimbo de bloqueio `manual_lock_until` (parâmetro `lockHours`, default 24h).
        *   Atualizar o preço do produto (o incremento de `@Version` é automático).
        *   Criar e persistir na mesma transação o registro em `price_history` com `origin = 'MANUAL'`.
        *   Publicar o evento Spring `PriceChangedEvent`.
    3.  Criar o ouvinte `AlertEventListener`:
        *   Escuta `PriceChangedEvent` de forma desacoplada.
        *   Se o override do `ADMIN` quebrou a margem mínima: instanciar e gravar `Alert` do tipo `MARGIN_BREACH`.
        *   Se o cálculo detectou `variationLimitHit`: gravar `Alert` do tipo `VARIATION_LIMIT_HIT`.
    4.  Criar controllers e endpoints:
        *   `PUT /api/products/{id}/price`: Executa a sobrescrita.
        *   `DELETE /api/products/{id}/lock`: Zera o lock do produto.
        *   `GET /api/products/{id}/history`: Histórico paginado daquele item.
        *   `GET /api/alerts`: Listagem de alertas abertos e resolvidos.
        *   `PATCH /api/alerts/{id}/resolve`: Resolução de alerta.
*   **Critério de Conclusão / Testes:**
    *   Testes unitários e de integração confirmam que alterações de preço geram auditoria transacional síncrona.
    *   Teste de concorrência com duas threads paralelas tentando alterar o mesmo produto comprova que uma delas falha com erro de concorrência otimista (`OptimisticLockException`), evitando perdas de atualização.
    *   Testes com MockMvc comprovam que `OPERATOR` é barrado ao tentar violar a margem e que o evento desacoplado grava os alertas esperados.

---

#### Milestone 6 — Ingestão de Preços de Concorrentes
*Status:* **Pendente (Depende de M4).**
*   **Objetivo:** Implementar a ingestão de preços de mercado por upload de CSV multipart e payload JSON direto, sob uma arquitetura baseada no Strategy Pattern.
*   **Tarefas Práticas e Acionáveis:**
    1.  Mapear entidades JPA: `Competitor` e `CompetitorPrice`.
    2.  Criar a interface Strategy `CompetitorPriceSource`.
    3.  Implementar `CsvCompetitorPriceSource`:
        *   Parser seguro de arquivo CSV.
        *   Validação linha a linha: casamento com produto via SKU ou EAN, verificação de valor $> 0$.
        *   Relatório de execução: mapear linhas inconsistentes com mensagem de erro sem interromper a gravação das linhas válidas.
        *   Persistência em lote das linhas válidas em `competitor_price` com carimbo `collected_at`.
    4.  Implementar `JsonCompetitorPriceSource` para consumo via endpoint POST.
    5.  Criar os endpoints correspondentes:
        *   `POST /api/competitor-prices/import`: Upload multipart/form-data de CSV.
        *   `POST /api/competitor-prices`: Ingestão direta de JSON.
*   **Critério de Conclusão / Testes:**
    *   Teste de integração enviando CSV contendo linhas válidas e inválidas: a resposta deve listar detalhadamente as linhas rejeitadas com suas causas e confirmar a persistência estrita dos registros válidos.
    *   Validação de que os registros inseridos tornam-se intocáveis no banco (triggers validados).

---

#### Milestone 7 — Módulo de IA: Dados Sintéticos, Modelagem e Avaliação
*Status:* **Pendente (Pode ocorrer em paralelo a M4-M6).**
*   **Objetivo:** Gerar base sintética reproduzível de vendas, calibrar o modelo econométrico log-log de elasticidade e validar os ganhos simulados em notebook.
*   **Tarefas Práticas e Acionáveis:**
    1.  Estruturar diretório `ai-service/` com arquivos de configuração (`pyproject.toml` ou `requirements.txt`).
    2.  Implementar o gerador de dados reproduzível (`data_generator.py`):
        *   Fixar semente aleatória (`seed = 42`).
        *   Gerar série temporal de 365 dias para a tabela `sales_history` nas três verticais.
        *   Injetar elasticidade verdadeira conhecida ($\eta_{\text{real}}$), sazonalidade e variações de preços exógenas (para prevenir endogeneidade).
    3.  Implementar script de treinamento econométrico (`train.py`):
        *   Ajuste da regressão múltipla: $\ln(q) = \alpha + \eta \ln(p) + \beta \ln(p_{\text{comp}}) + \text{dummies}$.
        *   Extração dos parâmetros $\hat{\eta}$, $\hat{\beta}$ e $R^2$ por categoria.
        *   Serialização do artefato calibrado (`model_artifact.json` ou `joblib`) rotulado com tag semântica de versão (ex: `v1.0.0`).
    4.  Desenvolver notebook de avaliação (`evaluation.ipynb`):
        *   Divisão temporal: 80% iniciais para treino, 20% finais mais recentes para teste (sem embaralhar).
        *   Cálculo do erro de estimação: $|\hat{\eta} - \eta_{\text{real}}|$.
        *   Simulação comparativa de lucro acumulado no conjunto de teste: Modelo de Elasticidade vs. Baseline (Mediana Concorrente $\times 0.99$) vs. Ótimo Teórico.
        *   Declaração explícita no relatório: os resultados financeiros são **estritamente simulados em ambiente controlado**.
*   **Critério de Conclusão / Testes:**
    *   Execução do pipeline Python a partir do zero regenera perfeitamente os mesmos dados e os mesmos coeficientes de elasticidade.
    *   Testes com `pytest` cobrindo o cálculo do ponto ótimo $p^* = c \cdot \frac{\eta}{1+\eta}$.

---

#### Milestone 8 — Microsserviço Python com FastAPI
*Status:* **Pendente (Depende de M7).**
*   **Objetivo:** Expor o modelo estatístico por meio de uma API HTTP assíncrona, com inferência puramente stateless e contratos Pydantic v2.
*   **Tarefas Práticas e Acionáveis:**
    1.  Mapear schemas de entrada e saída Pydantic v2 com `Decimal` trafegado como string monetária.
    2.  Implementar rotas do FastAPI:
        *   `GET /health`: Healthcheck simples.
        *   `GET /v1/model/info`: Metadados da versão do modelo, data de calibragem e parâmetros médios.
        *   `POST /v1/suggestions`:
            *   Processar requisições em lotes (até 500 itens).
            *   Checar regras de confiança: se $\eta \ge -1$ ou $R^2$ baixo, adotar baseline fallback e emitir `confidence: "LOW"`.
            *   Calcular preço ótimo irrestrito e aplicar recorte nos limites recebidos (`min_price` e `max_price`), informando em `clipped_by` o tipo de corte.
    3.  Garantir *statelessness*: nenhuma conexão com banco de dados a partir desta aplicação.
    4.  Criar suíte de testes com `pytest`: validação de schemas, lógica de clipping e acionamento de fallback.
    5.  Criar `Dockerfile` otimizado para a aplicação FastAPI.
    6.  Aferir latência: registrar tempo de resposta p95 $< 500$ ms para lote de 50 itens.
*   **Critério de Conclusão / Testes:**
    *   Suíte de testes `pytest` executando com 100% de sucesso.
    *   Serviço roda em container Docker e responde compliant à especificação OpenAPI local.

---

#### Milestone 9 — Integração Resiliente Java ↔ Python
*Status:* **Pendente (Depende de M5, M6 e M8).**
*   **Objetivo:** Orquestrar o ciclo completo de precificação autônoma no Java, integrando com o microsserviço Python de forma segura e tolerante a falhas.
*   **Tarefas Práticas e Acionáveis:**
    1.  Implementar a interface `PriceSuggestionClient` com duas implementações:
        *   `HttpPriceSuggestionClient`: Comunicação REST real com o microsserviço Python via Spring `RestClient`.
        *   `FakePriceSuggestionClient`: Implementação estática utilizada para testes locais isolados.
    2.  Configurar **Resilience4j**:
        *   Timeout de conexão/leitura de 2 segundos.
        *   1 retry automático para falhas transitórias.
        *   Circuit Breaker para abrir e isolar chamadas caso a taxa de falhas exceda o limiar.
    3.  Implementar `PricingRunService`:
        *   Criar e persistir a entidade `PricingRun` com status inicial `RUNNING`.
        *   Filtrar produtos ativos e sem lock manual vigente.
        *   Para cada produto: calcular os limites operacionais usando `PriceBoundsCalculator`.
        *   Fracionar a lista de produtos em lotes de até 100 itens e enviar ao serviço Python.
        *   Ao receber o retorno: submeter cada sugestão ao `PriceGuard.validate()`.
        *   Se validado com sucesso e o preço sugerido for diferente do atual: atualizar o produto, registrar em `price_history` e disparar evento (em transação isolada por produto via `@Transactional(propagation = Propagation.REQUIRES_NEW)`).
        *   Se a resposta contiver `confidence: "LOW"`, disparar evento para gerar `Alert(type=LOW_CONFIDENCE)`.
        *   Se o Python estiver fora do ar ou o `PriceGuard` rejeitar o valor: manter o preço atual intacto, marcar item como ignorado/falha, disparar alerta `AI_UNAVAILABLE` e prosseguir com os outros itens.
        *   Atualizar e fechar o `PricingRun` com status `SUCCESS`, `PARTIAL` ou `FAILED` e totalizadores consolidados.
    4.  Configurar agendador `@Scheduled` para disparo automático contínuo.
*   **Critério de Conclusão / Testes:**
    *   Teste de ciclo ponta a ponta disparado via Swagger com a IA ativa atualiza os preços e gera histórico e totalizadores corretos.
    *   Teste de resiliência derrubando intencionalmente o microsserviço Python: o backend completa o ciclo sem travar, preserva os preços vigentes dos produtos e emite o alerta `AI_UNAVAILABLE`.

---

#### Milestone 10 — Relatórios e Requisitos Complementares (Should)
*Status:* **Pendente (Depende de M5 e M9).**
*   **Objetivo:** Entregar exportação de histórico para fechamentos financeiros em formato de streaming de alto volume e endpoints analíticos de KPIs.
*   **Tarefas Práticas e Acionáveis:**
    1.  Implementar `GET /api/reports/price-history.csv`:
        *   Consulta orientada a stream pelo JPA/JDBC.
        *   Escrita contínua em `StreamingResponseBody` sem carregar todas as linhas em memória RAM.
        *   Suporte a filtros de intervalo de datas (`from` e `to`).
    2.  Implementar `GET /api/kpis`:
        *   Consultas SQL analíticas agregadas via repositório.
        *   Retorno com: margem média por categoria, volume de produtos próximos da margem mínima, taxa de sucesso de execuções da IA e distribuição das alterações por tipo de origem.
    3.  *Se o cronograma permitir:* Implementar `NfeCostUpdateService` (RF-10):
        *   Importação simplificada de XML de NF-e via `POST /api/invoices/import`.
        *   Garantia de idempotência verificando chave de acesso de 44 dígitos já processada.
        *   Atualização do custo de aquisição do produto; se a margem cair abaixo do mínimo, elevar o preço de venda para `piso_margem` (origem `COST_UPDATE`).
*   **Critério de Conclusão / Testes:**
    *   Exportação de CSV executada com sucesso contra massa volumosa sem sobrecarga de memória heap do Java.
    *   KPIs retornam com precisão agregada contra os dados seedados na base.

---

#### Milestone 11 — Polimento, CI/CD e Definition of Done (DoD)
*Status:* **Pendente (Fase Final).**
*   **Objetivo:** Orquestrar o ambiente global por Docker Compose, fechar o pipeline de integração contínua e formalizar a documentação arquitetural de portfólio.
*   **Tarefas Práticas e Acionáveis:**
    1.  Consolidar o arquivo `docker-compose.yml` unificado para subir simultaneamente:
        *   `copiloto-mysql`: MySQL 8.0 na porta 3307 com migrations automáticas via Flyway e seed de dados inicial.
        *   `copiloto-backend`: API Java/Spring Boot na porta 8080.
        *   `copiloto-ai`: Microsserviço FastAPI na porta 8000.
    2.  Configurar pipeline do **GitHub Actions** (`.github/workflows/ci.yml`):
        *   Compilação e execução de todos os testes unitários e de integração do backend Java (com Testcontainers rodando no runner).
        *   Execução da suíte de testes do microsserviço Python via `pytest`.
    3.  Formalizar os Registros de Decisão Arquitetural na pasta `docs/adr/`:
        *   `ADR-001`: Critério de desempate e prevalência de margem mínima em janelas de centavos subcentavo.
        *   `ADR-002`: Microsserviço Python separado para IA e inferência estritamente stateless.
        *   `ADR-003`: Margem prevalece sobre variação com auditoria imutável via triggers de banco de dados.
        *   `ADR-004`: Reparametrização da fórmula econométrica de demanda para isolamento de elasticidade-preço.
    4.  Elaborar `README.md` final do projeto com instruções de execução rápida, diagrama arquitetural, tabela comparativa de lucros do modelo contra o baseline e seção de riscos e limitações (declarando o uso de dados sintéticos para validação econométrica).
*   **Critério de Conclusão / Definition of Done (DoD):**
    *   O comando `docker compose up --build` sobe todo o ambiente e a interface do Swagger UI fica acessível e operacional com dados de exemplo.
    *   Pipeline de CI do GitHub Actions executando com status verde em todas as etapas de build e teste.
    *   Todas as decisões de negócio e tolerância a falhas documentadas e comprovadas por evidências de testes.