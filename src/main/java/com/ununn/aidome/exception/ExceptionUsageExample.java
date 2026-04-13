package com.ununn.aidome.exception;

/**
 * 统一异常管理使用示例
 * 
 * 本文件展示如何在项目中使用统一异常管理机制
 */
public class ExceptionUsageExample {
    
    // ==================== 1. Service层抛出异常 ====================
    
    /**
     * 示例：在Service方法中抛出业务异常
     */
    /**
     * 示例：在Service方法中抛出业务异常
     */
    public void exampleThrowBusinessException() {
        // ❌ 旧方式：返回Result.error
        // return Result.error("用户不存在");

        // ✅ 新方式：抛出异常，由全局处理器统一处理
        throw new BusinessException("用户不存在");
    }

    /**
     * 示例：带错误码的业务异常
     */
    public void exampleBusinessExceptionWithCode() {
        // 带错误码的异常
        throw new BusinessException(400, "参数格式错误");
    }
    
    /**
     * 示例：资源不存在时抛出异常
     */
    public void exampleResourceNotFound(Integer userId) {
        // ✅ 简洁的资源不存在异常
        throw new ResourceNotFoundException("用户", userId);
        // 输出: "用户 [ID=123] 不存在"
    }
    
    /**
     * 示例：参数校验失败
     */
    public void exampleValidation(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException("消息内容不能为空");
        }
    }
    
    /**
     * 示例：AI服务调用失败
     */
    public void exampleAIServiceError(Exception e) {
        throw new AIServiceException("大模型调用超时", e);
    }
    
    /**
     * 示例：认证失败
     */
    public void exampleAuthentication() {
        throw new AuthenticationException("Token已过期，请重新登录");
    }
    
    
    // ==================== 2. Controller层不需要try-catch ====================
    
    /**
     * 示例：Controller方法不需要捕获异常
     * 异常会自动被GlobalExceptionHandler捕获并转换为Result
     */
    /*
    @PostMapping("/chat")
    public Result chat(@RequestParam String message) {
        // ❌ 旧方式：需要手动捕获异常
        // try {
        //     return chatService.chat(message);
        // } catch (Exception e) {
        //     return Result.error("聊天失败：" + e.getMessage());
        // }
        
        // ✅ 新方式：直接调用，异常由全局处理器处理
        return chatService.chat(message);
    }
    */
    
    
    // ==================== 3. 拦截器中抛出异常 ====================
    
    /**
     * 示例：在Interceptor中抛出认证异常
     */
    /*
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("authorization");
        
        // ❌ 旧方式：手动设置响应状态码
        // if (token == null) {
        //     response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        //     return false;
        // }
        
        // ✅ 新方式：抛出异常，由全局处理器处理
        if (token == null) {
            throw new AuthenticationException("未提供认证令牌");
        }
        
        return true;
    }
    */
    
    
    // ==================== 4. 异常嵌套（保留原始异常信息）====================
    
    /**
     * 示例：捕获底层异常并包装为业务异常
     */
    public void exampleExceptionWrapping() {
        try {
            // 调用外部API
            callExternalApi();
        } catch (Exception e) {
            // ✅ 保留原始异常栈，方便排查问题
            throw new AIServiceException("外部API调用失败", e);
        }
    }
    
    private void callExternalApi() throws Exception {
        // 模拟API调用
    }
    
    
    // ==================== 5. 最佳实践总结 ====================
    
    /**
     * ✅ DO - 应该这样做：
     * 1. Service层抛出具体异常（BusinessException及其子类）
     * 2. Controller层不捕获异常，让全局处理器处理
     * 3. 使用具体的异常类型（ResourceNotFoundException而不是BusinessException）
     * 4. 保留原始异常信息（new BusinessException(msg, cause)）
     * 5. 在拦截器中抛出AuthenticationException
     * 
     * ❌ DON'T - 不应该这样做：
     * 1. 不要在Service层返回Result.error
     * 2. 不要在Controller层写大量try-catch
     * 3. 不要吞掉异常（catch后什么都不做）
     * 4. 不要直接抛Exception（要用具体类型）
     * 5. 不要在生产环境暴露详细堆栈信息
     */
}
