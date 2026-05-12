package org.mrhusku.exceptions;

public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) {
        super("AI Processing Error: " + message);
    }
}