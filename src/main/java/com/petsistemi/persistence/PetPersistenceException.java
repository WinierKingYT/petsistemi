package com.petsistemi.persistence;

public class PetPersistenceException extends RuntimeException {

    public PetPersistenceException(String message) {
        super(message);
    }

    public PetPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
