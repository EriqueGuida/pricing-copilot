package com.guidaerique.pricing_copilot.pricing.domain;

public class InvalidPriceSuggestionException
        extends RuntimeException {

    public InvalidPriceSuggestionException(String message) {
        super(message);
    }
}