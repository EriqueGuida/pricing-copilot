package com.guidaerique.pricing_copilot.shared.exception;

public class InvalidInvoiceXmlException extends RuntimeException {
    public InvalidInvoiceXmlException(String message) {
        super(message);
    }

    public InvalidInvoiceXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}