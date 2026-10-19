CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    vertical ENUM(
        'CONSUMER_ELECTRONICS',
        'COMPUTER_GAMING',
        'SMART_HOME'
    ) NOT NULL,
    min_margin DECIMAL(5,4) NOT NULL,
    max_margin DECIMAL(5,4) NOT NULL,
    max_variation DECIMAL(5,4) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_name UNIQUE (name),
    CONSTRAINT ck_category_margins CHECK (
        min_margin > 0
        AND min_margin < max_margin
        AND max_margin < 1
    ),
    CONSTRAINT ck_category_variation CHECK (
        max_variation > 0 AND max_variation <= 0.5000
    )
) ENGINE=InnoDB;

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'OPERATOR', 'EXECUTIVE') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email CHECK (
        CHAR_LENGTH(TRIM(email)) > 0
    ),
    CONSTRAINT ck_app_user_password CHECK (
        CHAR_LENGTH(TRIM(password_hash)) > 0
    )
) ENGINE=InnoDB;

CREATE TABLE competitor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    website VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_competitor PRIMARY KEY (id),
    CONSTRAINT uq_competitor_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(40) NOT NULL,
    ean CHAR(13) NULL,
    name VARCHAR(160) NOT NULL,
    category_id BIGINT NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL,
    current_price DECIMAL(12,2) NOT NULL,
    manual_lock_until DATETIME(3) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT uq_product_ean UNIQUE (ean),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id)
        REFERENCES category(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_product_prices CHECK (
        cost_price > 0 AND current_price > 0
    ),
    CONSTRAINT ck_product_version CHECK (version >= 0),
    CONSTRAINT ck_product_sku CHECK (CHAR_LENGTH(TRIM(sku)) > 0),
    CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),

    INDEX ix_product_category_active (category_id, active),
    INDEX ix_product_active_lock (active, manual_lock_until)
) ENGINE=InnoDB;

CREATE TABLE pricing_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    status ENUM('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')
        NOT NULL DEFAULT 'RUNNING',
    processed_count INT NOT NULL DEFAULT 0,
    changed_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    model_version VARCHAR(40) NULL,

    CONSTRAINT pk_pricing_run PRIMARY KEY (id),
    CONSTRAINT ck_pricing_run_counts CHECK (
        processed_count >= 0
        AND changed_count >= 0
        AND skipped_count >= 0
        AND error_count >= 0
    ),
    CONSTRAINT ck_pricing_run_dates CHECK (
        finished_at IS NULL OR finished_at >= started_at
    ),

    INDEX ix_pricing_run_started (started_at),
    INDEX ix_pricing_run_status_started (status, started_at)
) ENGINE=InnoDB;

CREATE TABLE competitor_price (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    competitor_id BIGINT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    source ENUM('CSV', 'API') NOT NULL,

    CONSTRAINT pk_competitor_price PRIMARY KEY (id),
    CONSTRAINT fk_competitor_price_product FOREIGN KEY (product_id)
        REFERENCES product(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_competitor_price_competitor FOREIGN KEY (competitor_id)
        REFERENCES competitor(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_competitor_price_positive CHECK (price > 0),

    INDEX ix_competitor_price_product_date (product_id, collected_at),
    INDEX ix_competitor_price_competitor_date (competitor_id, collected_at)
) ENGINE=InnoDB;

CREATE TABLE sales_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sold_on DATE NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_sales_history PRIMARY KEY (id),
    CONSTRAINT fk_sales_history_product FOREIGN KEY (product_id)
        REFERENCES product(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_sales_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sales_unit_price CHECK (unit_price > 0),

    INDEX ix_sales_history_product_day (product_id, sold_on),
    INDEX ix_sales_history_sold_on (sold_on)
) ENGINE=InnoDB;

CREATE TABLE price_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    old_price DECIMAL(12,2) NOT NULL,
    new_price DECIMAL(12,2) NOT NULL,
    origin ENUM('AI', 'MANUAL', 'COST_UPDATE', 'CAMPAIGN') NOT NULL,
    changed_by BIGINT NULL,
    pricing_run_id BIGINT NULL,
    model_version VARCHAR(40) NULL,
    reason VARCHAR(255) NULL,
    explanation JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT pk_price_history PRIMARY KEY (id),
    CONSTRAINT fk_history_product FOREIGN KEY (product_id)
        REFERENCES product(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by)
        REFERENCES app_user(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_history_run FOREIGN KEY (pricing_run_id)
        REFERENCES pricing_run(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_history_prices CHECK (
        old_price > 0 AND new_price > 0
    ),
    CONSTRAINT ck_history_price_changed CHECK (old_price <> new_price),

    INDEX ix_history_product_date (product_id, created_at),
    INDEX ix_history_created (created_at),
    INDEX ix_history_run (pricing_run_id)
) ENGINE=InnoDB;

CREATE TABLE alert (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    type ENUM(
        'MARGIN_BREACH',
        'VARIATION_LIMIT_HIT',
        'LOW_CONFIDENCE',
        'AI_UNAVAILABLE'
    ) NOT NULL,
    status ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    message VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,

    CONSTRAINT pk_alert PRIMARY KEY (id),
    CONSTRAINT fk_alert_product FOREIGN KEY (product_id)
        REFERENCES product(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_alert_message CHECK (
        CHAR_LENGTH(TRIM(message)) > 0
    ),
    CONSTRAINT ck_alert_resolution CHECK (
        (status = 'OPEN' AND resolved_at IS NULL)
        OR
        (status = 'RESOLVED' AND resolved_at IS NOT NULL)
    ),

    INDEX ix_alert_status_created (status, created_at),
    INDEX ix_alert_product_created (product_id, created_at),
    INDEX ix_alert_type_status (type, status)
) ENGINE=InnoDB;