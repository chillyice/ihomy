package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.PointsProductDTO;
import com.ihomy.entity.Checkin;
import com.ihomy.entity.PointsOrder;
import com.ihomy.entity.PointsProduct;
import com.ihomy.entity.PointsRecord;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.CheckinMapper;
import com.ihomy.mapper.PointsOrderMapper;
import com.ihomy.mapper.PointsProductMapper;
import com.ihomy.mapper.PointsRecordMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 积分体系业务(V3.4):每日签到 / 内容发布奖励 / 积分商城兑换。
 * 签到规则:基础 5 分 + 连续签到加成((连续天数-1)%7,7 天一轮回),断签重计。
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    /** 签到基础分;连续加成上限为 7 天一轮回 */
    public static final int CHECKIN_BASE = 5;
    /** 发布内容奖励分(写流水大改时同步前端提示) */
    public static final int REWARD_BLOG = 10;
    public static final int REWARD_DIARY = 8;
    public static final int REWARD_PHOTO = 2;
    public static final int REWARD_VIDEO = 15;

    private final CheckinMapper checkinMapper;
    private final PointsRecordMapper recordMapper;
    private final PointsProductMapper productMapper;
    private final PointsOrderMapper orderMapper;
    private final SysUserMapper sysUserMapper;

    // ---------- 查询 ----------

    /** 我的积分概览:总积分/今日是否已签/当前连续天数/今日签到可得积分 */
    public Map<String, Object> stats(Long userId) {
        LocalDate today = LocalDate.now();
        Checkin todayCheckin = checkinMapper.selectOne(new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getUserId, userId).eq(Checkin::getCheckinDate, today));
        Checkin yesterday = checkinMapper.selectOne(new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getUserId, userId)
                .eq(Checkin::getCheckinDate, today.minusDays(1)));
        int streak = todayCheckin != null ? todayCheckin.getStreak()
                : (yesterday != null ? yesterday.getStreak() : 0);
        Map<String, Object> map = new HashMap<>();
        map.put("balance", balance(userId));
        map.put("checkedToday", todayCheckin != null);
        map.put("streak", streak);
        map.put("todayPoints", todayCheckin != null ? 0 : CHECKIN_BASE + streak % 7);
        return map;
    }

    /** 当前总积分(流水 change 求和) */
    public int balance(Long userId) {
        Integer sum = recordMapper.sumBalance(userId);
        return sum == null ? 0 : sum;
    }

    // ---------- 签到 ----------

    /** 签到:昨日签到则连续+1(按规则加分),否则重新连续;UNIQUE 兜底防重复 */
    @Transactional
    public Map<String, Object> checkin(Long userId, Long familyId) {
        LocalDate today = LocalDate.now();
        int nextStreak = 1;
        Checkin yesterday = checkinMapper.selectOne(new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getUserId, userId).eq(Checkin::getCheckinDate, today.minusDays(1)));
        if (yesterday != null) {
            nextStreak = yesterday.getStreak() + 1;
        }
        int points = CHECKIN_BASE + (nextStreak - 1) % 7;
        Checkin checkin = new Checkin();
        checkin.setUserId(userId);
        checkin.setFamilyId(familyId);
        checkin.setCheckinDate(today);
        checkin.setPoints(points);
        checkin.setStreak(nextStreak);
        try {
            checkinMapper.insert(checkin);
        } catch (Exception e) {
            // UNIQUE(user_id, checkin_date) 冲突即今日已签,并发下同样生效
            throw new BizException(ResultCode.ALREADY_CHECKIN);
        }
        addRecord(userId, familyId, "CHECKIN", points, "每日签到(连续第" + nextStreak + "天)");
        Map<String, Object> map = new HashMap<>();
        map.put("points", points);
        map.put("streak", nextStreak);
        map.put("balance", balance(userId));
        return map;
    }

    /** 记录一笔流水(balance 为变动后余额) */
    public void addRecord(Long userId, Long familyId, String type, int change, String remark) {
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setFamilyId(familyId);
        record.setChangeType(type);
        record.setChange(change);
        record.setBalance(balance(userId) + change);
        record.setRemark(remark);
        recordMapper.insert(record);
    }

    // ---------- 积分商城 ----------

    /** 本家庭上架商品列表(附当前用户已兑换次数,便于前端显示限兑) */
    public List<Map<String, Object>> products(Long familyId, Long userId) {
        return productMapper.selectList(new LambdaQueryWrapper<PointsProduct>()
                        .eq(PointsProduct::getFamilyId, familyId)
                        .orderByDesc(PointsProduct::getCreatedAt))
                .stream().map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("name", p.getName());
                    map.put("icon", p.getIcon());
                    map.put("points", p.getPoints());
                    map.put("stock", p.getStock());
                    map.put("perLimit", p.getPerLimit());
                    map.put("enabled", p.getEnabled());
                    map.put("redeemedCount", orderMapper.selectCount(new LambdaQueryWrapper<PointsOrder>()
                            .eq(PointsOrder::getProductId, p.getId())
                            .eq(PointsOrder::getUserId, userId)));
                    return map;
                }).collect(Collectors.toList());
    }

    /** 家长上架商品 */
    public PointsProduct createProduct(Long familyId, Long userId, PointsProductDTO dto) {
        PointsProduct product = new PointsProduct();
        product.setFamilyId(familyId);
        product.setName(dto.getName());
        product.setIcon(dto.getIcon());
        product.setPoints(dto.getPoints());
        product.setStock(dto.getStock() == null ? -1 : dto.getStock());
        product.setPerLimit(dto.getPerLimit() == null ? 0 : dto.getPerLimit());
        product.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        product.setCreatedBy(userId);
        productMapper.insert(product);
        return product;
    }

    /** 编辑商品(含上下架),仅本家庭商品可改 */
    public void updateProduct(Long id, Long familyId, PointsProductDTO dto) {
        PointsProduct product = productMapper.selectById(id);
        if (product == null || !product.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        product.setName(dto.getName());
        product.setIcon(dto.getIcon());
        if (dto.getPoints() != null) product.setPoints(dto.getPoints());
        if (dto.getStock() != null) product.setStock(dto.getStock());
        if (dto.getPerLimit() != null) product.setPerLimit(dto.getPerLimit());
        if (dto.getEnabled() != null) product.setEnabled(dto.getEnabled());
        productMapper.updateById(product);
    }

    /** 下架商品(逻辑下线,保留历史订单) public */
    public void deleteProduct(Long id, Long familyId) {
        PointsProduct product = productMapper.selectById(id);
        if (product == null || !product.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        product.setEnabled(0);
        productMapper.updateById(product);
    }

    /** 兑换商品:校验积分/库存/限兑,落订单并扣积分 */
    @Transactional
    public PointsOrder redeem(Long productId, Long familyId, Long userId) {
        PointsProduct product = productMapper.selectById(productId);
        if (product == null || !product.getFamilyId().equals(familyId) || product.getEnabled() != 1) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (product.getStock() != null && product.getStock() != -1 && product.getStock() <= 0) {
            throw new BizException(ResultCode.PRODUCT_SOLD_OUT);
        }
        if (product.getPerLimit() != null && product.getPerLimit() > 0) {
            Long count = orderMapper.selectCount(new LambdaQueryWrapper<PointsOrder>()
                    .eq(PointsOrder::getProductId, productId)
                    .eq(PointsOrder::getUserId, userId));
            if (count >= product.getPerLimit()) {
                throw new BizException(ResultCode.PRODUCT_SOLD_OUT);
            }
        }
        if (balance(userId) < product.getPoints()) {
            throw new BizException(ResultCode.INSUFFICIENT_POINTS);
        }
        // ponytail: 低并发家庭场景,未加行锁;若需严格防超卖可改 UPDATE ... WHERE stock>0
        if (product.getStock() != null && product.getStock() != -1) {
            product.setStock(product.getStock() - 1);
            productMapper.updateById(product);
        }
        PointsOrder order = new PointsOrder();
        order.setUserId(userId);
        order.setFamilyId(familyId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setPointsSpent(product.getPoints());
        order.setStatus(DictConst.ORDER_PENDING);
        orderMapper.insert(order);
        addRecord(userId, familyId, "REDEEM", -product.getPoints(), "兑换 " + product.getName());
        return order;
    }

    /** 我的兑换记录 */
    public List<PointsOrder> myOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<PointsOrder>()
                .eq(PointsOrder::getUserId, userId)
                .orderByDesc(PointsOrder::getCreatedAt));
    }

    /** 家庭全部兑换记录(仅家长核销用),带兑换人昵称 */
    public List<Map<String, Object>> familyOrders(Long familyId) {
        List<PointsOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PointsOrder>()
                .eq(PointsOrder::getFamilyId, familyId)
                .orderByDesc(PointsOrder::getCreatedAt));
        Map<Long, String> nicknames = orders.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(orders.stream().map(PointsOrder::getUserId)
                        .distinct().collect(Collectors.toList())).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        return orders.stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("productName", o.getProductName());
            map.put("pointsSpent", o.getPointsSpent());
            map.put("status", o.getStatus());
            map.put("createdAt", o.getCreatedAt());
            map.put("nickname", nicknames.getOrDefault(o.getUserId(), "未知成员"));
            return map;
        }).collect(Collectors.toList());
    }

    /** 家长核销订单(确认虚拟物品已被使用) */
    public void markTaken(Long orderId, Long familyId) {
        PointsOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        order.setStatus(DictConst.ORDER_REDEEMED);
        orderMapper.updateById(order);
    }
}