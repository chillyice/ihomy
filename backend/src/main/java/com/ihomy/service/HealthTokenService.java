package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HealthTokenDTO;
import com.ihomy.entity.SysHealthToken;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysHealthTokenMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康数据上报令牌:长期、可吊销、最小权限(只够 /health/ingest 与 /health/ping)。
 * 明文 hk_ 只在生成时返回一次,库里只存 SHA-256 与前 8 位前缀;明文不写日志。
 * 不带 refresh/轮换,不触发 OPS 判定——快捷指令拿不到浏览器那套双 token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthTokenService {

    private static final int MAX_ACTIVE_TOKENS = 10;
    private static final int TOKEN_BYTES = 32;
    private static final Duration LAST_RESULT_TTL = Duration.ofDays(30);
    private static final String LAST_RESULT_KEY = "ihomy:health:last:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysHealthTokenMapper tokenMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 生成令牌:明文只出现在返回体里,落库前就哈希掉 */
    public Map<String, Object> create(Long userId, Long familyId, HealthTokenDTO dto) {
        Long active = tokenMapper.selectCount(new LambdaQueryWrapper<SysHealthToken>()
                .eq(SysHealthToken::getUserId, userId)
                .eq(SysHealthToken::getRevoked, 0));
        if (active != null && active >= MAX_ACTIVE_TOKENS) {
            throw new BizException(ResultCode.BAD_REQUEST, "上报令牌最多保留 10 个,请先吊销不用的");
        }
        String name = dto == null || dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) name = "健康数据设备";
        if (name.length() > 50) name = name.substring(0, 50);

        String plain = "hk_" + randomToken();
        SysHealthToken row = new SysHealthToken();
        row.setUserId(userId);
        row.setFamilyId(familyId);
        row.setName(name);
        row.setTokenHash(sha256Hex(plain));
        row.setTokenPrefix(plain.substring(0, Math.min(plain.length(), 8)));
        row.setRevoked(0);
        tokenMapper.insert(row);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("name", name);
        // 明文只返回这一次;用户丢了只能吊销重发
        data.put("token", plain);
        data.put("tokenPrefix", row.getTokenPrefix());
        data.put("createdAt", row.getCreatedAt());
        return data;
    }

    /** 令牌列表(只回前缀/设备名/最后使用与上报/吊销,永远不回 hash 与明文)+ 最近一次上报结果 */
    public Map<String, Object> list(Long userId) {
        List<SysHealthToken> rows = tokenMapper.selectList(new LambdaQueryWrapper<SysHealthToken>()
                .eq(SysHealthToken::getUserId, userId)
                .orderByDesc(SysHealthToken::getId));
        List<Map<String, Object>> tokens = new ArrayList<>();
        for (SysHealthToken r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("name", r.getName());
            item.put("tokenPrefix", r.getTokenPrefix());
            item.put("lastUsedAt", r.getLastUsedAt());
            item.put("lastIngestAt", r.getLastIngestAt());
            item.put("revoked", Integer.valueOf(1).equals(r.getRevoked()));
            item.put("createdAt", r.getCreatedAt());
            tokens.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tokens", tokens);
        // 快捷指令用户看不到 HTTP 响应,最近一次上报结果是唯一排障入口
        data.put("lastResult", lastResult(userId));
        return data;
    }

    /** 最近一次上报结果(Redis,30 天),设置页回显「接收 N 条 / 拒收 N 条」 */
    public Map<String, Object> lastResult(Long userId) {
        try {
            String json = redis.opsForValue().get(LAST_RESULT_KEY + userId);
            if (json == null) return null;
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.debug("读取健康上报结果失败 uid={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /** 记账式的上报摘要:只留统计与首条原因,不含具体数值 */
    public void saveLastResult(Long userId, Map<String, Object> result) {
        try {
            redis.opsForValue().set(LAST_RESULT_KEY + userId, objectMapper.writeValueAsString(result), LAST_RESULT_TTL);
        } catch (Exception e) {
            log.debug("保存健康上报结果失败 uid={}: {}", userId, e.getMessage());
        }
    }

    /** 吊销自己的令牌;别人的/不存在的都按 404(不可枚举) */
    public void revoke(Long userId, Long id) {
        int rows = tokenMapper.update(null, new LambdaUpdateWrapper<SysHealthToken>()
                .eq(SysHealthToken::getId, id)
                .eq(SysHealthToken::getUserId, userId)
                .eq(SysHealthToken::getRevoked, 0)
                .set(SysHealthToken::getRevoked, 1));
        if (rows == 0) throw new BizException(ResultCode.NOT_FOUND, "令牌不存在或已吊销");
    }

    /** 按明文查有效令牌(过滤过滤器用);明文只在这里算一次哈希 */
    public SysHealthToken findActive(String plain) {
        if (plain == null || !plain.startsWith("hk_")) return null;
        return tokenMapper.selectOne(new LambdaQueryWrapper<SysHealthToken>()
                .eq(SysHealthToken::getTokenHash, sha256Hex(plain))
                .eq(SysHealthToken::getRevoked, 0)
                .last("LIMIT 1"));
    }

    /** 记录一次使用(每请求一次写,令牌限速 60 次/小时,量可忽略) */
    public void touch(Long id) {
        tokenMapper.update(null, new LambdaUpdateWrapper<SysHealthToken>()
                .eq(SysHealthToken::getId, id)
                .set(SysHealthToken::getLastUsedAt, LocalDateTime.now()));
    }

    /** 记录一次成功入库 */
    public void markIngest(Long id) {
        LocalDateTime now = LocalDateTime.now();
        tokenMapper.update(null, new LambdaUpdateWrapper<SysHealthToken>()
                .eq(SysHealthToken::getId, id)
                .set(SysHealthToken::getLastUsedAt, now)
                .set(SysHealthToken::getLastIngestAt, now));
    }

    /** 令牌自检:返回本人、当前家庭与最后上报时间(给快捷指令/排障用) */
    public Map<String, Object> ping(SysHealthToken token) {
        SysUser u = userMapper.selectById(token.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", token.getUserId());
        data.put("username", u == null ? null : u.getUsername());
        // 家庭按成员实时解析,不信签发快照
        data.put("familyId", u == null ? token.getFamilyId() : u.getFamilyId());
        data.put("tokenPrefix", token.getTokenPrefix());
        data.put("lastUsedAt", token.getLastUsedAt());
        data.put("lastIngestAt", token.getLastIngestAt());
        return data;
    }

    private String randomToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
