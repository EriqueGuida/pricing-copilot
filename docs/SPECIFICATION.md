# COPILOTO DE PRECIFICAÇÃO INTELIGENTE
## DOCUMENTO DE ESPECIFICAÇÃO TÉCNICA E REQUISITOS (PRD)

---

### 1. Visão Geral e Objetivo do Projeto

O **Copiloto de Precificação Inteligente** é uma plataforma distribuída voltada para a automação, otimização e governança de preços no comércio eletrônico, cobrindo três verticais de negócio:
1. **Eletrônicos de Consumo (`CONSUMER_ELECTRONICS`)**
2. **Acessórios de Informática e Gaming (`COMPUTER_GAMING`)**
3. **Dispositivos de Casa Inteligente (`SMART_HOME`)**

#### 1.1 O Problema de Negócio
No varejo digital de alta rotação, a atualização manual de preços por planilhas gera gargalos operacionais: atraso no acompanhamento da concorrência, perda de oportunidade de venda e risco constante de erosão de margem por erro de arredondamento de centavos ou falta de validação contra o custo de aquisição.

#### 1.2 A Solução Arquitetural
O sistema adota uma **arquitetura híbrida desacoplada**:
* Um serviço analítico *stateless* em **Python (FastAPI)** estima a elasticidade-preço da demanda e sugere o preço que maximiza o lucro esperado (ou aplica uma regra de *baseline* quando a confiança estatística é baixa).
* Um núcleo transacional em **Java 17 (Spring Boot 3.3+)** atua como o **guardião das regras de negócio**: calcula faixas permitidas com `BigDecimal`, valida a resposta da IA no `PriceGuard`, controla concorrência otimista (`@Version`) e grava o histórico de alterações protegido por *triggers* no banco de dados.

> **Princípio Fundamental:**  
> **A Inteligência Artificial sugere; o Backend Java decide.** Toda sugestão gerada pela IA é estritamente consultiva e deve ser validada dentro de limites rígidos de margem e variação pelo Java antes de atualizar o catálogo.

---

### 2. Stack Tecnológica Oficial

| Componente | Tecnologia | Versão | Justificativa Arquitetural |
| :--- | :--- | :--- | :--- |
| **Backend Core** | Java | 17 LTS | Tipagem forte, suporte a `records` e precisão aritmética decimal (`BigDecimal`). |
| **Framework Web Core** | Spring Boot | 3.3+ | Injeção de dependência, integração nativa com Jakarta EE 10, Spring Web e validação. |
| **Segurança & RBAC** | Spring Security | 6.x | Autenticação *stateless* via filtro JWT (com checagem de conta ativa) e autorização por método (`@PreAuthorize`). |
| **Persistência / ORM** | Spring Data JPA / Hibernate | 6.x | Transações ACID, isolamento por item (`REQUIRES_NEW`) e bloqueio otimista (`@Version`). |
| **Banco de Dados** | MySQL Server | 8.0 | Engine InnoDB, suporte a constraints declarativas (`CHECK`, `UNIQUE`, `FK RESTRICT`) e `TRIGGERS`. |
| **Evolução de Schema** | Flyway | 10.x | Versionamento imutável e reproduzível do esquema SQL validado no startup (`ddl-auto: validate`). |
| **Tolerância a Falhas** | Resilience4j | 2.x | Implementação de `@CircuitBreaker` e `@Retry` combinados com timeouts no `RestClient`. |
| **Documentação da API** | Springdoc OpenAPI | 2.x | Geração de contratos Swagger UI (OpenAPI 3.0) e respostas de erro padronizadas via RFC 7807 (`ProblemDetail`). |
| **Engine de IA & API** | Python / FastAPI | 3.11+ / 0.110+ | Serviço analítico *stateless* com validação de schemas via Pydantic v2 (`Decimal` serializado como string). |
| **Modelagem Estatística** | Statsmodels / NumPy | 0.14+ | Regressão econométrica log-log para estimativa de elasticidade-preço em dados sintéticos controlados. |
| **Containerização** | Docker & Compose | 24+ / v2 | Orquestração local dos três serviços (`copiloto-mysql`, `copiloto-ai`, `copiloto-backend`). |
| **Testes Automatizados**| JUnit 5, Testcontainers & Pytest | - | Testes unitários, teste de propriedade com oráculo inteiro (300k amostras) e testes de integração com MySQL real. |

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
│  [API Layer / Controllers] ──▶ Spring Security (Filtro JWT + Conta Ativa + RBAC)       │
│           │                                                                            │
│           ▼                                                                            │
│  [Application Services]                                                                │
│   ├── PricingRunService ──(HTTP)──▶ [Adapter: HttpPriceSuggestionClient]               │
│   ├── PriceOverrideService          (Connect 1s, Read 2s, Retry 2x, CircuitBreaker)    │
│   └── CompetitorIngestionService                     │                                 │
│           │                                          │ JSON (Lotes de até 100 itens)   │
│           ▼                                          │ (Valores Monetários em String)  │
│  [Domain Core & Item Processor]                      │                                 │
│   ├── PriceBoundsCalculator (Piso/Teto CEILING/FLOOR)│                                 │
│   ├── PriceGuard (Validação de Intervalo e Escala)   │                                 │
│   ├── PricingItemProcessor (Transação REQUIRES_NEW)  │                                 │
│   └── Event Publisher (PriceChangedEvent)            │                                 │
│           │                   │                      │                                 │
│           │                   ▼                      │                                 │
│           │        [AlertEventListener]              │                                 │
│           │        (Fase: AFTER_COMMIT)              │                                 │
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
│ ├── pricing_run, alert                │   │ ├── GET  /health                           │
│ ├── price_history (Append-Only)       │   │ ├── Pydantic v2 (Validation Engine)        │
│ │   └── Triggers: BLOQUEIA UPD/DEL    │   │ └── Engine Econométrica (Log-Log + Fallback│
│ └── competitor_price (Append-Only)    │   └────────────────────────────────────────────┘
│     └── Triggers: BLOQUEIA UPD/DEL    │                         
└───────────────────────────────────────┘                          
```

#### 3.2 Estrutura de Pacotes por Domínio (Backend Java)
O código-fonte do backend é organizado por domínio funcional:

```
com.guidaerique.pricing_copilot
├── catalog        # Gestão de categorias e cadastro de produtos
├── pricing        # Orquestração de runs, cálculo de limites, PriceGuard e cliente HTTP
├── competitor     # Ingestão de preços de concorrentes (CSV e JSON)
├── audit          # Histórico append-only de alterações de preço (price_history)
├── alert          # Event listeners pós-commit e gestão de alertas operacionais
├── kpi            # Consultas analíticas agregadas de indicadores operacionais
├── security       # Filtro JWT, serviço de token, EntryPoint/AccessDeniedHandler e RBAC
└── shared         # Exceções de domínio e GlobalExceptionHandler (RFC 7807)
```

---

### 4. Modelo de Dados e Defesa em Profundidade (MySQL 8.0)

A integridade dos dados combina validações na aplicação Java com restrições relacionais e *triggers* no banco de dados.

#### 4.1 Convenções de Tipagem
*   **Valores Monetários:** `DECIMAL(12,2)` — evita representação aproximada em ponto flutuante binário.
*   **Taxas e Percentuais:** `DECIMAL(5,4)` — armazenados como frações absolutas (ex: `0.1500` para 15,00%).
*   **Controle de Versão Concorrente:** `BIGINT NOT NULL DEFAULT 0` — mapeado para `@Version` no JPA.
*   **Datas e Carimbos Temporais:** `DATETIME(3)` ou `DATETIME` em UTC.

#### 4.2 Esquema das Tabelas (DDL das Migrations Flyway)

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
    CONSTRAINT ck_price_history_diff CHECK (old_price <> new_price),
    INDEX ix_history_product_date (product_id, created_at),
    INDEX ix_history_created (created_at)
);

-- 8. Base de treino econométrico (Séries temporais sintéticas de vendas)
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

#### 4.3 Triggers de Imutabilidade (Defesa em Profundidade)
Para proteger as tabelas `price_history` e `competitor_price` contra mutações acidentais ou bugs de ORM na aplicação, gatilhos relacionais bloqueiam instruções ordinárias de `UPDATE` e `DELETE` (ADR-003):

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

Toda a lógica matemática de limites de preços é governada pelo Java na classe `PriceBoundsCalculator`.

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
    Limite inferior de oscilação do preço atual em um ciclo (`RoundingMode.CEILING`):
    $$\text{piso\_variacao} = \lceil p_{\text{atual}} \cdot (1 - v_{\text{max}}) \rceil_2$$
4.  **Teto de Variação ($\text{teto\_variacao}$):**
    Limite superior de oscilação do preço atual em um ciclo (`RoundingMode.FLOOR`):
    $$\text{teto\_variacao} = \lfloor p_{\text{atual}} \cdot (1 + v_{\text{max}}) \rfloor_2$$
5.  **Intervalo Seguro Efetivo:**
    $$\text{min\_efetivo} = \max(\text{piso\_margem}, \text{piso\_variacao})$$
    $$\text{max\_efetivo} = \min(\text{teto\_margem}, \text{teto\_variacao})$$

#### 5.3 Regra de Prevalência da Margem e Colapso de Intervalo
Se a interseção entre o intervalo de margem e o de variação for vazia ($\text{min\_efetivo} > \text{max\_efetivo}$):
*   **A margem prevalece sobre o limite de variação.**
*   O intervalo **colapsa em um ponto único** ($\text{min\_efetivo} = \text{max\_efetivo} = \text{alvo}$).
*   **Escolha do Alvo:**
    *   Se $p_{\text{atual}} < \text{piso\_margem}$, o alvo é $\text{piso\_margem}$.
    *   Se $p_{\text{atual}} > \text{teto\_margem}$, o alvo é $\text{teto\_margem}$.
*   **Sinalização do Alerta:** Se o alvo escolhido estiver fora do alcance da variação permitida ($\text{alvo} < \text{piso\_variacao}$ ou $\text{alvo} > \text{teto\_variacao}$), o sistema define a flag `variationLimitHit = true`, que gera o alerta operacional `VARIATION_LIMIT_HIT`.

#### 5.4 Tratamento da Janela Subcentavo (ADR-001)
Em cenários onde o custo é baixo e a diferença entre margem mínima e máxima é estreita, o arredondamento direcional para centavos pode gerar $\text{piso\_margem} > \text{teto\_margem}$. Nesse caso, **a margem mínima prevalece**, fixando o preço-alvo em $\text{piso\_margem}$.

#### 5.5 Regras de Sobrescrita Manual (Override)
*   **Papel OPERATOR:**
    *   Só tem permissão para aplicar um preço contido no intervalo de margem da categoria: $[\text{piso\_margem}, \text{teto\_margem}]$.
    *   Tentativa de aplicar valor fora dessa faixa é rejeitada.
*   **Papel ADMIN:**
    *   Pode definir qualquer valor $> 0$, exigindo justificativa formal obrigatória (`reason`) com no mínimo 10 caracteres.
    *   Se o preço aplicado violar a margem mínima ($p < \text{piso\_margem}$), a operação é gravada no histórico e emite o alerta `MARGIN_BREACH`.
*   **Trava Contra a IA (`manual_lock_until`):**
    *   Toda sobrescrita manual bloqueia o produto contra sugestões da IA por um período determinado (`lockHours`, padrão 24h, máximo 720h).

---

### 6. Módulo Analítico de IA e Contratos da API

#### 6.1 Formulação Econométrica (FastAPI / Python)
O microsserviço estima uma regressão linear múltipla log-log por vertical de negócio (ADR-004):
$$\ln(q) = \alpha + \eta \ln(p) + \beta \ln(p_{\text{comp}}) + \sum \gamma_k D_k + \epsilon$$

*   $q$: Quantidade demandada.
*   $p$: Preço praticado pela loja.
*   $p_{\text{comp}}$: Mediana dos concorrentes diretos no produto.
*   $\eta$: Elasticidade-preço direta da demanda ($\eta < 0$).
*   $\beta$: Elasticidade-cruzada em relação aos concorrentes ($\beta > 0$).
*   $D_k$: Dummies de sazonalidade semanal (dia da semana).

#### 6.2 Preço Ótimo (Maximização de Lucro) e Baseline Fallback
O lucro esperado é dado por $L(p) = (p - c) \cdot q(p)$. Maximizando em relação a $p$:
$$p^* = c \cdot \left( \frac{\eta}{1 + \eta} \right), \quad \text{para } \eta < -1$$

*   **Critérios de Baixa Confiança (`LOW_CONFIDENCE`):**
    O serviço Python declara baixa confiança e ativa internamente a estratégia `BASELINE_FALLBACK` caso:
    1.  A elasticidade estimada seja inelástica ou inválida: $\eta \ge -1$.
    2.  O coeficiente de determinação da regressão $R^2 < 0{,}30$.
    3.  A amostragem de vendas seja inferior a 30 observações.
*   **Política de Preço no Baseline (Dentro do Python):**
    *   Se existirem concorrentes: $\text{preço\_sugerido} = \text{mediana}(p_{\text{comp}}) \times 0{,}99$.
    *   Se não existirem concorrentes: $\text{preço\_sugerido} = p_{\text{atual}}$.
*   **Operação de Recorte (Clip):**
    O preço final sugerido pela IA sofre recorte nos limites enviados pelo Java (`min_price` e `max_price`), mas ainda assim passa pela revalidação obrigatória do `PriceGuard` no retorno ao Java:
    $$p_{\text{sugerido}} = \max\left(\text{min\_price}, \min\left(p^*, \text{max\_price}\right)\right)$$

#### 6.3 Contratos de Interface (Pydantic v2)
Todos os valores monetários trafegam serializados como **string** no JSON para evitar perda de precisão.

```python
from decimal import Decimal
from typing import Literal, List, Optional
from pydantic import BaseModel, Field

class SuggestionItem(BaseModel):
    sku: str
    category_id: int
    vertical: Optional[str] = None
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
*   `GET /v1/model/info`: Versão ativa do artefato do modelo e métricas por vertical.
*   `POST /v1/suggestions`: Processa o lote de sugestões de forma **stateless** quanto ao estado de negócio (sem conexão com o MySQL).

---

### 7. Requisitos Funcionais do Sistema (RF)

*   **RF-01: Catálogo de Produtos e Categorias (Must)**
    *   CRUD administrativo de categorias contendo vertical e limites percentuais (`min_margin`, `max_margin`, `max_variation`).
    *   CRUD de produtos com SKU único, EAN opcional, custo e preço atual.
    *   *Aceite:* Não permitir cadastro de produto com preço que viole os limites de margem da categoria associada; rejeitar custos ou preços negativos ou zerados.
*   **RF-02: Ingestão de Preços de Concorrentes (Must)**
    *   Importação em lote via CSV (`multipart/form-data`) e ingestão individual via JSON (`POST /api/competitor-prices`).
    *   *Aceite:* Registros com preços $\le 0$ ou SKU/EAN inexistentes são reportados sem quebrar a gravação das linhas válidas; dados válidos são inseridos como registros imutáveis.
*   **RF-03: Ciclo de Precificação Automatizada (Must)**
    *   Disparo manual via `POST /api/pricing/runs` e agendado via `@Scheduled`.
    *   Seleciona produtos ativos e sem lock manual vigente, busca preços de concorrentes em lote (`ROW_NUMBER()`), calcula limites com `PriceBoundsCalculator` e consulta o serviço Python.
    *   *Aceite:* Cada produto tem seu preço e histórico gravados em **transação curta isolada** (`REQUIRES_NEW` em `PricingItemProcessor`), revalidando os limites vigentes antes de persistir; falha em um item não reverte os demais.
*   **RF-04: Sobrescrita Manual e Trava de Segurança (Must)**
    *   Endpoints `PUT /api/products/{id}/price` e `DELETE /api/products/{id}/lock`.
    *   *Aceite:* O operador é barrado se tentar precificar fora dos limites de margem; o administrador é autorizado para qualquer valor $> 0$ com justificativa $\ge 10$ caracteres (gerando alerta `MARGIN_BREACH` se abaixo do piso); o produto fica bloqueado contra a IA pelo prazo estipulado.
*   **RF-05: Auditoria Contínua e Imutabilidade (Must)**
    *   Toda mudança de preço insere obrigatoriamente um registro em `price_history` na mesma transação da atualização do produto.
    *   *Aceite:* Tentativas ordinárias de executar `UPDATE` ou `DELETE` em `price_history` ou `competitor_price` são barradas no MySQL via Trigger (`SQLSTATE '45000'`).
*   **RF-06: Gestão de Alertas Desacoplada (Must)**
    *   Alertas operacionais gerados em listener pós-commit (`AFTER_COMMIT`) a partir de `PriceChangedEvent` ou falhas de integração.
    *   Tipos: `MARGIN_BREACH`, `VARIATION_LIMIT_HIT`, `LOW_CONFIDENCE`, `AI_UNAVAILABLE`.
    *   *Aceite:* Alertas nascem como `OPEN` e podem ser resolvidos via `PATCH /api/alerts/{id}/resolve`.
*   **RF-07: Segurança Baseada em Papéis (RBAC) (Must)**
    *   Proteção via tokens JWT e revalidação de conta ativa (`active = true`) a cada requisição.
    *   *Aceite:* Requisições sem autenticação válida retornam `401 Unauthorized`; requisições autenticadas sem permissão retornam `403 Forbidden`, respeitando a matriz abaixo:

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

*   **RF-08: Endpoints de Indicadores Consolidados (KPIs) (Should)**
    *   Endpoint `GET /api/kpis` consolidando métricas operacionais de margem, produtos e execuções de runs.

---

### 8. Requisitos Não-Funcionais (RNF)

*   **RNF-01 (Segurança):** Spring Security 6, JWT HMAC-SHA256, senhas com BCrypt e tratamento de falhas de segurança (`AuthenticationEntryPoint` e `AccessDeniedHandler`) padronizado em RFC 7807 (`ProblemDetail`).
*   **RNF-02 (Aritmética Financeira):** Uso exclusivo de `BigDecimal` no Java e `Decimal` no Python para valores monetários.
*   **RNF-03 (Concorrência e ACID):** Uso de `@Version` na entidade `Product` para controle de concorrência otimista, retornando HTTP `409 Conflict` em caso de escrita conflitante simultânea.
*   **RNF-04 (Resiliência e Fail-Closed):** Comunicação HTTP entre Java e Python configurada com timeout de conexão de 1.000 ms, timeout de leitura de 2.000 ms, `@Retry` (2 tentativas com espera fixa de 500 ms para erros de I/O e `5xx`) e `@CircuitBreaker` (janela de 10 chamadas, mínimo de 5, limiar de 50%, ignorando `4xx`). Em caso de falha da IA, o Java preserva o preço atual do produto e gera o alerta `AI_UNAVAILABLE` (ADR-005).
*   **RNF-05 (Observabilidade e Erros):** Propagação do cabeçalho `X-Correlation-ID` e padronização de erros HTTP via RFC 7807 (`ProblemDetail`) com registro em log de falhas internas no `GlobalExceptionHandler`.
*   **RNF-06 (Infraestrutura Reprodutível):** Subida integral da solução via `docker compose up --build`.

---

### 9. Histórico de Execução por Milestones e Revisão de Escopo

O desenvolvimento inicial percorreu **11 milestones** (cujo estado original completo encontra-se preservado na tag [`v0.1.0-experimental`](https://github.com/EriqueGuida/pricing-copilot/releases/tag/v0.1.0-experimental)), seguido de uma consolidação de escopo na branch `main`.

```
[M1: Fundação] ──▶ [M2: Banco de Dados] ──▶ [M3: Domínio de Preço] ──▶ [M4: Catálogo & RBAC]
                                                                                  │
  ┌───────────────────────────────────────────────────────────────────────────────┘
  ▼
[M5: Override & Auditoria] ──▶ [M6: Ingestão de Concorrentes] ──▶ [M7: Dados & Modelo IA]
                                                                          │
  ┌───────────────────────────────────────────────────────────────────────┘
  ▼
[M8: Serviço FastAPI] ──▶ [M9: Integração Java ↔ IA] ──▶ [M10: KPIs Consolidados] ──▶ [M11: Consolidação & ADRs]
```

#### Milestone 1 — Infraestrutura e Fundação
*   Estrutura Maven/Java 17, Docker Compose com MySQL 8.0 (porta 3307), Spring Boot Actuator e baseline Flyway.

#### Milestone 2 — Modelagem Relacional e Integridade do Banco
*   Migrations Flyway com constraints `CHECK`, `UNIQUE`, `FOREIGN KEY (RESTRICT)` e triggers append-only em `price_history` e `competitor_price`, testadas via Testcontainers.

#### Milestone 3 — Domínio Puro de Precificação
*   Classes `PriceBoundsCalculator`, `PriceBounds` e `PriceGuard` com arredondamento `CEILING`/`FLOOR`, prevalência de margem, janela subcentavo (ADR-001) e teste de propriedade com oráculo em centavos inteiros (`Random(42)`, 300.000 amostras).

#### Milestone 4 — Catálogo de Produtos e Segurança RBAC
*   Entidades JPA (`Category`, `Product` com `@Version`, `AppUser`), autenticação JWT com verificação de conta ativa, `AuthenticationEntryPoint` (401) / `AccessDeniedHandler` (403), autorização `@PreAuthorize` e documentação Swagger UI.

#### Milestone 5 — Sobrescrita Manual, Auditoria e Alertas por Eventos
*   `PriceOverrideService` com validação por perfil (`OPERATOR` restrito à margem; `ADMIN` com justificativa), trava `manual_lock_until`, gravação atômica em `price_history` e disparo de alertas em listener pós-commit (`AFTER_COMMIT`).

#### Milestone 6 — Ingestão de Preços de Concorrentes
*   Ingestão via CSV multipart e JSON utilizando Strategy Pattern (`CompetitorPriceSource`), com validação linha a linha e busca otimizada com `ROW_NUMBER()`.

#### Milestone 7 — Módulo de IA: Dados Sintéticos e Modelagem Econométrica
*   Geração reproduzível de dados sintéticos (`seed = 42`), treinamento do modelo log-log por vertical (`train.py`) e exportação do artefato `model_artifact.json`.

#### Milestone 8 — Microsserviço Python com FastAPI
*   API FastAPI *stateless* (`/health`, `/v1/model/info`, `/v1/suggestions`), schemas Pydantic v2 com `Decimal` e testes unitários com `pytest` validando o cálculo em `Decimal`, o *clipping* e o *baseline fallback*.

#### Milestone 9 — Integração Resiliente Java ↔ Python
*   Orquestração em `PricingRunService`, envio explícito do `vertical` da categoria ao Python, configuração de timeouts, `@Retry` e `@CircuitBreaker`, e persistência isolada por item em `PricingItemProcessor` (`REQUIRES_NEW`) com revalidação dos limites vigentes.

#### Milestone 10 — Indicadores Consolidados (KPIs) e Revisão de Escopo
*   Implementação do endpoint `GET /api/kpis` com métricas agregadas de negócio.
*   *Nota de Revisão de Escopo:* Os módulos experimentais de importação de XML de NF-e e exportação de relatórios CSV (presentes na tag `v0.1.0-experimental`) foram removidos da branch `main` para manter o projeto focado no domínio central de precificação.

#### Milestone 11 — Polimento, CI/CD e ADRs
*   Orquestração via `docker-compose.yml` (`copiloto-mysql`, `copiloto-ai`, `copiloto-backend`), `.dockerignore` configurado no serviço Python, pipeline no GitHub Actions e formalização dos 5 ADRs em `docs/adr/`:
    *   `ADR-001-janela-subcentavo.md`
    *   `ADR-002-ia-stateless.md`
    *   `ADR-003-auditoria-imutavel.md`
    *   `ADR-004-elasticidade-demanda.md`
    *   `ADR-005-isolamento-transacional-e-resiliencia.md`