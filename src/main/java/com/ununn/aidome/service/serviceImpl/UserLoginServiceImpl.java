package com.ununn.aidome.service.serviceImpl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.ununn.aidome.Util.RegexUtils;
import com.ununn.aidome.mapper.UserMapper;
import com.ununn.aidome.pojo.LoginFormDTO;
import com.ununn.aidome.pojo.Result;
import com.ununn.aidome.pojo.User;
import com.ununn.aidome.pojo.UserDTO;
import com.ununn.aidome.service.UserLoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.ununn.aidome.Util.RedisConstants.*;


@Service
@Slf4j
public class UserLoginServiceImpl implements UserLoginService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result login(LoginFormDTO user) {

        String phone = user.getPhone();
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2,如果不符合返回错误信息
            return Result.error("手机号格式错误");
        }

        //1,根据手机号和密码查询用户
        User user1 = userMapper.selectByIdAndPassword(user);

        //2,查询失败时，如果是测试用户则自动创建
        if(user1 == null){
            // 测试用户：手机号13800138000，密码123456
            if("13800138000".equals(phone) && "123456".equals(user.getPassword())){
                // 创建测试用户
                user1 = new User();
                user1.setPhone("13800138000");
                user1.setPassword("123456");
                user1.setUsername("测试用户");
                // 如果数据库中没有，先尝试插入（可能会失败，但没关系）
                try {
                    User existUser = userMapper.selectByPhone(phone);
                    if(existUser == null) {
                        user1.setId(1);
                        userMapper.insert(user1);
                        user1 = userMapper.selectByPhone(phone);
                    } else {
                        user1 = existUser;
                    }
                } catch (Exception e) {
                    log.warn("测试用户已存在或插入失败: {}", e.getMessage());
                    // 如果插入失败，尝试直接查询
                    user1 = userMapper.selectByPhone(phone);
                    if(user1 == null) {
                        return Result.error("用户名或密码错误");
                    }
                }
            } else {
                return Result.error("用户名或密码错误");
            }
        }

        //3,查询成功生成token(UUID加随机字符串)
        String token = UUID.randomUUID().toString();
        //4,将User对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user1, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);

        //返回token
        return Result.success(token);
    }

    // 注册
    @Override
    public Result register(LoginFormDTO user) {
        //1校验手机号
        String phone = user.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2,如果不符合返回错误信息
            return Result.error("手机号格式错误");
        }


        //2,校验验证码
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY +phone);
        String code = user.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            //3,如果失败则返回错误信息
            return Result.error("验证码错误");
        }

        //3,判断用户是否存在
        User user1 = userMapper.selectByPhone(phone);
        if(user1 != null){
            return Result.error("用户已存在");
        }

        //3,成功则将用户保存
        createUserWithPhone(user);
        return Result.success();
    }

    private User createUserWithPhone(LoginFormDTO user) {
        //创建用户
        User user1 = new User();
        user1.setPhone(user.getPhone());
        user1.setPassword(user.getPassword());
        user1.setUsername("user_" + RandomUtil.randomNumbers(10));
        //添加用户
        userMapper.insert(user1);
        return user1;
    }

    // 发送验证码
    @Override
    public Result sendCode(String phone) {
        //1,校验手机号
//        if(RegexUtils.isPhoneInvalid(phone)){
//            //2,如果不符合返回错误信息
//            return Result.error("手机号格式错误");
//        }
        //3,符合,生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4,保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone ,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5,返回验证码
        log.info("验证码发送成功,验证码:{}",code);

        //返回ok
        return Result.success("发送成功");
    }

    // 退出登录
    @Override
    public Result logout(String token) {
        if (token != null && !token.isEmpty()) {
            // 删除Redis中的token
            Boolean deleted = stringRedisTemplate.delete(LOGIN_USER_KEY + token);
            log.info("删除Redis中的token: {}, 结果: {}", LOGIN_USER_KEY + token, deleted);
        }
        return Result.success("退出登录成功");
    }

    // 获取用户信息
    @Override
    public Result getUserInfo(Integer userId) {
        try {
            // 根据用户ID从数据库中获取用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                // 如果数据库中没有找到用户，返回错误
                return Result.error("用户不存在");
            }
            // 构建用户信息DTO，只返回必要的字段
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            
            return Result.success(userDTO);
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage());
            return Result.error("获取用户信息失败: " + e.getMessage());
        }
    }
}
