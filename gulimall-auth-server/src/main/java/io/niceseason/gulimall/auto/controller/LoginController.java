package io.niceseason.gulimall.auto.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import io.niceseason.common.constant.AuthServerConstant;
import io.niceseason.common.exception.BizCodeEnum;
import io.niceseason.common.utils.R;
import io.niceseason.common.vo.MemberResponseVo;
import io.niceseason.gulimall.auto.feign.MemberFeignService;
import io.niceseason.gulimall.auto.feign.ThirdPartFeignService;
import io.niceseason.gulimall.auto.vo.UserLoginVo;
import io.niceseason.gulimall.auto.vo.UserRegisterVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
public class LoginController {
    @Autowired
    private ThirdPartFeignService thirdPartFeignService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Qualifier("io.niceseason.gulimall.auto.feign.MemberFeignService")
    @Autowired
    private MemberFeignService memberFeignService;


    @RequestMapping("/login.html")
    public String loginPage(HttpSession session) {
        if (session.getAttribute(AuthServerConstant.LOGIN_USER) != null) {
            return "redirect:http://gulimall.com/";
        }else {
            return "login";
        }
    }

//    通过会员服务远程调用登录接口
//    如果调用成功，重定向至首页
//    如果调用失败，则封装错误信息并携带错误信息重定向至登录页
    @RequestMapping("/login")
    public String login(UserLoginVo vo, RedirectAttributes attributes, HttpSession session){
//        todo:登录
        R r = memberFeignService.login(vo);
        if (r.getCode() == 0) {
            String jsonString = JSON.toJSONString(r.get("memberEntity"));
            MemberResponseVo memberResponseVo = JSON.parseObject(jsonString, new TypeReference<MemberResponseVo>() {
            });
            session.setAttribute(AuthServerConstant.LOGIN_USER, memberResponseVo);
            return "redirect:http://gulimall.com/";
        }else {
            String msg = (String) r.get("msg");
            Map<String, String> errors = new HashMap<>();
            errors.put("msg", msg);
            attributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }



    //    接口防刷
    //    由于发送验证码的接口暴露，为了防止恶意攻击，我们不能随意让接口被调用。
    //    在redis中以phone-code将电话号码和验证码进行存储并将当前时间与code一起存储
    //    如果调用时以当前phone取出的v不为空且当前时间在存储时间的60s以内，说明60s内该号码已经调用过，返回错误信息
    //    60s以后再次调用，需要删除之前存储的phone-code
    //    code存在一个过期时间，我们设置为10min，10min内验证该验证码有效
    @GetMapping("/sms/sendCode")
    @ResponseBody
    public R sendCode(@RequestParam("phone")String phone) {
//        todo:接口防刷
       //接口防刷,在redis中缓存phone-code
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String prePhone = AuthServerConstant.SMS_CODE_CACHE_PREFIX + phone;
        String v = ops.get(prePhone);
        if (!StringUtils.isEmpty(v)) {
            long pre = Long.parseLong(v.split("_")[1]);
            //如果存储的时间小于60s，说明60s内发送过验证码
            if (System.currentTimeMillis() - pre < 60000) {
                return R.error(BizCodeEnum.SMS_CODE_EXCEPTION.getCode(), BizCodeEnum.SMS_CODE_EXCEPTION.getMsg());
            }
        }
        //如果存在的话，删除之前的验证码
        redisTemplate.delete(prePhone);
        //获取到6位数字的验证码
        String code = String.valueOf((int)((Math.random() + 1) * 100000));
        //在redis中进行存储并设置过期时间
        ops.set(prePhone, code+"_"+System.currentTimeMillis(), 10, TimeUnit.MINUTES);
        thirdPartFeignService.sendCode(phone, code);
        return R.ok();
    }

    //    注册接口编写
    //    在gulimall-auth-server服务中编写注册的主体逻辑
    //    若JSR303校验未通过，则通过BindingResult封装错误信息，并重定向至注册页面
    //    若通过JSR303校验，则需要从redis中取值判断验证码是否正确，正确的话通过会员服务注册
    //    会员服务调用成功则重定向至登录页，否则封装远程服务返回的错误信息返回至注册页面
    //    注： RedirectAttributes可以通过session保存信息并在重定向的时候携带过去
    @PostMapping("/register")
    public String register(@Valid UserRegisterVo registerVo, BindingResult result, RedirectAttributes attributes) {
//        todo:注册接口
        //1.判断校验是否通过
        Map<String, String> errors = new HashMap<>();
        if (result.hasErrors()){
            //1.1 如果校验不通过，则封装校验结果
            result.getFieldErrors().forEach(item -> {
                errors.put(item.getField(), item.getDefaultMessage());
                //1.2 将错误信息封装到session中
                attributes.addFlashAttribute("errors", errors);
            });
            //1.2 重定向到注册页
            return "redirect:http://auth.gulimall.com/reg.html";
        }else {
            //2.若JSR303校验通过
            //判断验证码是否正确
            String code = redisTemplate.opsForValue().get(
                    AuthServerConstant.SMS_CODE_CACHE_PREFIX + registerVo.getPhone());
            //2.1 如果对应手机的验证码不为空且与提交上的相等-》验证码正确
            if (!StringUtils.isEmpty(code) && registerVo.getCode().equals(code.split("_")[0])) {
                //2.1.1 使得验证后的验证码失效
                redisTemplate.delete(AuthServerConstant.SMS_CODE_CACHE_PREFIX + registerVo.getPhone());

                //2.1.2 远程调用会员服务注册
                R r = memberFeignService.register(registerVo);
                if (r.getCode() == 0) {
                    //调用成功，重定向登录页
                    return "redirect:http://auth.gulimall.com/login.html";
                }else {
                    //调用失败，返回注册页并显示错误信息
                    String msg = (String) r.get("msg");
                    errors.put("msg", msg);
                    attributes.addFlashAttribute("errors", errors);
                    return "redirect:http://auth.gulimall.com/reg.html";
                }
            }else {
                //2.2 验证码错误
                errors.put("code", "验证码错误");
                attributes.addFlashAttribute("errors", errors);
                return "redirect:http://auth.gulimall.com/reg.html";
            }
        }
    }
}
