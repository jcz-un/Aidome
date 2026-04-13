package com.ununn.aidome.exception;

/**
 * 认证异常 - 用户未登录或Token失效
 */
public class AuthenticationException extends BusinessException {
    
    public AuthenticationException(String message) {
        super(401, message);
    }
}
