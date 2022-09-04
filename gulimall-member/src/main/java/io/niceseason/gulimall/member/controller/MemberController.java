package io.niceseason.gulimall.member.controller;

import java.util.Arrays;
import java.util.Map;

import io.niceseason.common.exception.BizCodeEnum;
import io.niceseason.gulimall.member.exception.PhoneNumExistException;
import io.niceseason.gulimall.member.exception.UserExistException;
import io.niceseason.gulimall.member.feign.CouponFeignService;
import io.niceseason.gulimall.member.vo.MemberLoginVo;
import io.niceseason.gulimall.member.vo.MemberRegisterVo;
import io.niceseason.gulimall.member.vo.SocialUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.niceseason.gulimall.member.entity.MemberEntity;
import io.niceseason.gulimall.member.service.MemberService;
import io.niceseason.common.utils.PageUtils;
import io.niceseason.common.utils.R;



/**
 * 会员
 *
 * @author Ethan
 * @email hongshengmo@163.com
 * @date 2020-05-27 23:01:00
 */
@RestController
@RequestMapping("member/member")
public class MemberController {
    @Autowired
    private MemberService memberService;

    @Autowired
    private CouponFeignService couponFeignService;


//    当数据库中含有以当前登录名为用户名或电话号且密码匹配时，验证通过，返回查询到的实体
//    否则返回null，并在controller返回用户名或密码错误
    @RequestMapping("/login")
    public R login(@RequestBody MemberLoginVo loginVo) {
        MemberEntity entity = memberService.login(loginVo);
        if (entity != null){
            return R.ok().put("memberEntity", entity);
        }else {
            return R.error(BizCodeEnum.LOGINACCT_PASSWORD_EXCEPTION.getCode(), BizCodeEnum.LOGINACCT_PASSWORD_EXCEPTION.getMsg());
        }
    }

//    登录接口
//    登录包含两种流程，实际上包括了注册和登录
//    如果之前未使用该社交账号登录，则使用token调用开放api获取社交账号相关信息，注册并将结果返回
//    如果之前已经使用该社交账号登录，则更新token并将结果返回
    @RequestMapping("/oauth2/login")
    public R login(@RequestBody SocialUser socialUser) {
//        todo：登录
        MemberEntity entity = memberService.login(socialUser);
        if (entity != null){
            return R.ok().put("memberEntity",entity);
        }else {
            return R.error();
        }
    }

    /**
     * 注册会员
     * @return
     */
//    通过gulimall-member会员服务注册逻辑
//    通过异常机制判断当前注册会员名和电话号码是否已经注册，如果已经注册，则抛出对应的自定义异常，并在返回时封装对应的错误信息
//    如果没有注册，则封装传递过来的会员信息，并设置默认的会员等级、创建时间
    @RequestMapping("/register")
    public R register(@RequestBody MemberRegisterVo registerVo) {
        try {
            memberService.register(registerVo);
        } catch (UserExistException userException) {
            return R.error(BizCodeEnum.USER_EXIST_EXCEPTION.getCode(), BizCodeEnum.USER_EXIST_EXCEPTION.getMsg());
        } catch (PhoneNumExistException phoneException) {
            return R.error(BizCodeEnum.PHONE_EXIST_EXCEPTION.getCode(), BizCodeEnum.PHONE_EXIST_EXCEPTION.getMsg());
        }
        return R.ok();
    }

    @RequestMapping("/coupons")
    public R test(){
        MemberEntity memberEntity=new MemberEntity();
        memberEntity.setNickname("zhangsan");
        R memberCoupons = couponFeignService.memberCoupons();

        return memberCoupons.put("member",memberEntity).put("coupons",memberCoupons.get("coupons"));
    }

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = memberService.queryPage(params);
        return R.ok().put("page", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		MemberEntity member = memberService.getById(id);

        return R.ok().put("member", member);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody MemberEntity member){
		memberService.save(member);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody MemberEntity member){
		memberService.updateById(member);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		memberService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
