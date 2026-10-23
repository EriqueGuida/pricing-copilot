package com.guidaerique.pricing_copilot.shared.exception;

public class DuplicateInvoiceException extends RuntimeException {
    private final String accessKey;

    public DuplicateInvoiceException(String accessKey) {
        super("Nota Fiscal com a chave de acesso '" + accessKey + "' já foi processada anteriormente.");
        this.accessKey = accessKey;
    }

    public String getAccessKey() {
        return accessKey;
    }
}