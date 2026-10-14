package com.smartstock.app.exception;

public class UserRoleChangeNotAllowedException extends RuntimeException {
    public UserRoleChangeNotAllowedException(String message) {
        super(message);
    }
}
