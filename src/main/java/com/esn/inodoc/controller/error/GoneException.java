package com.esn.inodoc.controller.error;

public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
