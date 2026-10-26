-- V5: Remove a tabela órfã processed_invoice após a revisão planejada de escopo (remoção do módulo experimental de NF-e)
DROP TABLE IF EXISTS processed_invoice;
