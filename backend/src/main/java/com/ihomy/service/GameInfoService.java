package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.GameConst;
import com.ihomy.common.ResultCode;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.dto.GameInfoDTO;
import com.ihomy.entity.GameInfo;
import com.ihomy.mapper.GameInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家庭小游戏业务:导入 .swf(存 games/{游戏名}/)与 .gba、列表/详情/改名/改描述/删除,家庭隔离。
 * 仅支持 SWF/GBA 两种导入类型(.gbc/.gb 仅前端本地模式播放,不入库)。改名不移动物理文件夹(文件夹名在导入时固定)。
 */
@Service
@RequiredArgsConstructor
public class GameInfoService {

    private final GameInfoMapper gameMapper;
    private final FileService fileService;
    private final StringRedisTemplate redis;
    private final PointsService pointsService;

    public List<GameInfo> list(Long familyId) {
        return gameMapper.selectList(new LambdaQueryWrapper<GameInfo>()
                .eq(GameInfo::getFamilyId, familyId)
                .orderByDesc(GameInfo::getCreatedAt));
    }

    public GameInfo get(Long familyId, Long id) {
        return require(familyId, id);
    }

    public GameInfo importGame(Long userId, Long familyId, MultipartFile file, String name, String description) {
        String cleanName = normalizeName(name);
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要导入的游戏文件");
        }
        String original = file.getOriginalFilename();
        String lower = original != null ? original.toLowerCase() : "";
        String type;
        if (lower.endsWith(GameConst.SWF_EXTENSION)) {
            type = GameConst.TYPE_SWF;
        } else if (lower.endsWith(GameConst.GBA_EXTENSION)) {
            type = GameConst.TYPE_GBA;
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持导入 .swf 或 .gba 文件");
        }
        String url = fileService.uploadGame(file, cleanName);
        GameInfo g = new GameInfo();
        g.setFamilyId(familyId);
        g.setUserId(userId);
        g.setName(cleanName);
        g.setDescription(normalizeDescription(description));
        g.setType(type);
        g.setFileUrl(url);
        g.setStatus(GameConst.STATUS_ACTIVE);
        gameMapper.insert(g);
        return require(familyId, g.getId());
    }

    public GameInfo update(Long familyId, Long id, GameInfoDTO dto) {
        require(familyId, id);
        String cleanName = normalizeName(dto == null ? null : dto.getName());
        String cleanDesc = normalizeDescription(dto == null ? null : dto.getDescription());
        gameMapper.update(null, new LambdaUpdateWrapper<GameInfo>()
                .eq(GameInfo::getId, id)
                .eq(GameInfo::getFamilyId, familyId)
                .set(GameInfo::getName, cleanName)
                .set(GameInfo::getDescription, cleanDesc));
        return require(familyId, id);
    }

    public void delete(Long familyId, Long id) {
        GameInfo g = require(familyId, id);
        fileService.deleteByUrl(g.getFileUrl());
        gameMapper.deleteById(id);
    }

    /**
     * H5 小游戏通关奖励(宠物连连看):每局固定加分,按天限次防刷。
     * 计数器走 Redis(原子自增 + 次日零时过期),Redis 不可用时降级为直接奖励,不阻塞游戏体验。
     * ponytail: 低并发家庭场景,INCR 后校验存在极小并发窗口,每日上限为软性约束而非硬安全边界。
     */
    public Map<String, Object> petLinkReward(Long userId, Long familyId) {
        Map<String, Object> map = new HashMap<>();
        if (!pointsService.ruleEnabled(familyId, PointsRuleConst.GAME_PETLINK)) {
            map.put("awarded", 0);
            map.put("balance", pointsService.balance(userId));
            map.put("remainingToday", 0);
            return map;
        }
        LocalDate today = LocalDate.now();
        String key = "ihomy:game:petlink:" + userId + ":" + today;
        long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count == 1) {
                redis.expire(key, Duration.ofSeconds(secondsUntilMidnight()));
            }
        } catch (Exception e) {
            count = 1;
        }
        if (count > GameConst.PETLINK_DAILY_LIMIT) {
            map.put("awarded", 0);
            map.put("balance", pointsService.balance(userId));
            map.put("remainingToday", 0);
            return map;
        }
        int awarded = pointsService.rulePoints(familyId, PointsRuleConst.GAME_PETLINK);
        pointsService.addRecord(userId, familyId, "REWARD", awarded, "宠物连连看通关");
        map.put("awarded", awarded);
        map.put("balance", pointsService.balance(userId));
        map.put("remainingToday", (int) Math.max(0, GameConst.PETLINK_DAILY_LIMIT - count));
        return map;
    }

    /** 距次日零时的秒数,用于给每日计数器设过期时间 */
    private long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
    }

    private GameInfo require(Long familyId, Long id) {
        GameInfo g = gameMapper.selectOne(new LambdaQueryWrapper<GameInfo>()
                .eq(GameInfo::getFamilyId, familyId)
                .eq(GameInfo::getId, id));
        if (g == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return g;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "游戏名不能为空");
        }
        String n = name.trim();
        return n.length() > GameConst.NAME_MAX_LENGTH ? n.substring(0, GameConst.NAME_MAX_LENGTH) : n;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String d = description.trim();
        return d.length() > GameConst.DESC_MAX_LENGTH ? d.substring(0, GameConst.DESC_MAX_LENGTH) : d;
    }
}
