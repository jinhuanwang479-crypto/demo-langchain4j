package com.example.consultant.security;

/**
 * Thrown when the current user lacks permission to invoke a tool.
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
