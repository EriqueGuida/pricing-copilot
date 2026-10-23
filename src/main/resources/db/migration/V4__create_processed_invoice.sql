CREATE TABLE processed_invoice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    access_key VARCHAR(44) NOT NULL,
    issuer_cnpj VARCHAR(14) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    items_count INT NOT NULL,
    recalculated_items_count INT NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_processed_invoice PRIMARY KEY (id),
    CONSTRAINT uk_processed_invoice_access_key UNIQUE (access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;