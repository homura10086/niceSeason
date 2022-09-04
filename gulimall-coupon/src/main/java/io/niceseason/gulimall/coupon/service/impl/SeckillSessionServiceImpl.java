package io.niceseason.gulimall.coupon.service.impl;

import io.niceseason.gulimall.coupon.entity.SeckillSkuRelationEntity;
import io.niceseason.gulimall.coupon.service.SeckillSkuRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.niceseason.common.utils.PageUtils;
import io.niceseason.common.utils.Query;

import io.niceseason.gulimall.coupon.dao.SeckillSessionDao;
import io.niceseason.gulimall.coupon.entity.SeckillSessionEntity;
import io.niceseason.gulimall.coupon.service.SeckillSessionService;


@Service("seckillSessionService")
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionDao, SeckillSessionEntity> implements SeckillSessionService {

    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SeckillSessionEntity> page = this.page(
                new Query<SeckillSessionEntity>().getPage(params),
                new QueryWrapper<SeckillSessionEntity>()
        );

        return new PageUtils(page);
    }

//    获取最近三天的秒杀信息
//    获取最近三天的秒杀场次信息，再通过秒杀场次id查询对应的商品信息
    @Override
    public List<SeckillSessionEntity> getSeckillSessionsIn3Days() {
        QueryWrapper<SeckillSessionEntity> queryWrapper = new QueryWrapper<SeckillSessionEntity>()
                .between("start_time", getStartTime(), getEndTime());
        List<SeckillSessionEntity> seckillSessionEntities = this.list(queryWrapper);

        return seckillSessionEntities.stream().peek(session -> {
            List<SeckillSkuRelationEntity> skuRelationEntities = seckillSkuRelationService.list(
                    new QueryWrapper<SeckillSkuRelationEntity>().eq("promotion_session_id", session.getId()));
            session.setRelations(skuRelationEntities);
        }).collect(Collectors.toList());
    }

    //当前天数的 00:00:00
    private String getStartTime() {
        LocalDate now = LocalDate.now();
        LocalDateTime time = now.atTime(LocalTime.MIN);
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    //当前天数+2 23:59:59..
    private String getEndTime() {
        LocalDate now = LocalDate.now();
        LocalDateTime time = now.plusDays(2).atTime(LocalTime.MAX);
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }



}
