package com.ununn.aidome.exception;

/**
 * 资源不存在异常
 */
public class ResourceNotFoundException extends BusinessException {
    
    public ResourceNotFoundException(String message) {
        super(404, message);
    }
    
    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(404, String.format("%s [ID=%s] 不存在", resourceName, resourceId));
    }
}
