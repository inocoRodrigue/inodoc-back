package com.esn.inodoc.controller.error;

public class IntegrationUnavailableException extends RuntimeException {
    public IntegrationUnavailableException(String message) {
        super(message);
    }
}
