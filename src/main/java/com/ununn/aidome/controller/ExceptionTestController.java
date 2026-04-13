package com.ununn.aidome.controller;

import com.ununn.aidome.exception.*;
import com.ununn.aidome.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 异常管理测试Controller
 * 
 * 用于演示统一异常管理的各种场景
 * 访问这些接口可以看到异常如何被全局处理器捕获并转换为标准响应
 */
@Slf4j
@RestController
@RequestMapping("/test/exception")
public class ExceptionTestController {
    
    /**
     * 测试1：业务异常
     * GET /test/exception/business
     */
    @GetMapping("/business")
    public Result<Void> testBusinessException() {
        log.info("测试业务异常");
        throw new BusinessException("这是一个业务异常示例");
    }
    
    /**
     * 测试2：认证异常
     * GET /test/exception/auth
     */
    @GetMapping("/auth")
    public Result<Void> testAuthenticationException() {
        log.info("测试认证异常");
        throw new AuthenticationException("Token已过期，请重新登录");
    }
    
    /**
     * 测试3：资源不存在异常
     * GET /test/exception/not-found
     */
    @GetMapping("/not-found")
    public Result<Void> testResourceNotFoundException() {
        log.info("测试资源不存在异常");
        throw new ResourceNotFoundException("用户", 12345);
    }
    
    /**
     * 测试4：参数校验异常
     * GET /test/exception/validation?message=
     */
    @GetMapping("/validation")
    public Result<Void> testValidationException(@RequestParam(required = false) String message) {
        log.info("测试参数校验异常");
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException("消息内容不能为空");
        }
        return Result.success();
    }
    
    /**
     * 测试5：AI服务异常
     * GET /test/exception/ai
     */
    @GetMapping("/ai")
    public Result<Void> testAIServiceException() {
        log.info("测试AI服务异常");
        try {
            // 模拟AI调用失败
            simulateAIFailure();
        } catch (Exception e) {
            throw new AIServiceException("大模型调用超时", e);
        }
        return Result.success();
    }
    
    /**
     * 测试6：非法参数异常
     * GET /test/exception/illegal-arg?age=-1
     */
    @GetMapping("/illegal-arg")
    public Result<Void> testIllegalArgumentException(@RequestParam Integer age) {
        log.info("测试非法参数异常");
        if (age < 0 || age > 150) {
            throw new IllegalArgumentException("年龄必须在0-150之间");
        }
        return Result.success();
    }
    
    /**
     * 测试7：空指针异常（系统异常）
     * GET /test/exception/null-pointer
     */
    @GetMapping("/null-pointer")
    public Result<Void> testNullPointerException() {
        log.info("测试空指针异常");
        String str = null;
        // 故意触发空指针
        return Result.success();
    }
    
    /**
     * 测试8：未捕获的异常（兜底测试）
     * GET /test/exception/unknown
     */
    @GetMapping("/unknown")
    public Result<Void> testUnknownException() {
        log.info("测试未知异常");
        // 故意除以零
        int result = 1 / 0;
        return Result.success();
    }
    
    /**
     * 测试9：成功响应
     * GET /test/exception/success
     */
    @GetMapping("/success")
    public Result<String> testSuccess() {
        log.info("测试成功响应");
        return Result.success("操作成功");
    }
    
    // ==================== 辅助方法 ====================
    
    private void simulateAIFailure() throws Exception {
        // 模拟网络超时
        throw new java.net.SocketTimeoutException("Read timed out");
    }
}
