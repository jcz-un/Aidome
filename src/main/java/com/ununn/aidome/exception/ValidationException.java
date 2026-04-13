package com.ununn.aidome.exception;

/**
 * 参数校验异常
 */
public class ValidationException extends BusinessException {
    
    public ValidationException(String message) {
        super(422, message);
    }
}
