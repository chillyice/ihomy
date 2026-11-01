package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.GameConst;
import com.ihomy.common.GameSaveName;
import com.ihomy.common.ResultCode;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.dto.GameInfoDTO;
import com.ihomy.dto.GameSaveDTO;
import com.ihomy.entity.GameInfo;
import com.ihomy.mapper.GameInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家庭小游戏业务:导入 .swf(存 games/{游戏名}/)与 .gba、列表/详情/改名/改描述/删除,家庭隔离。
 * 仅支持 SWF/GBA 两种导入类型(.gbc/.gb 仅前端本地模式播放,不入库)。改名不移动物理文件夹(文件夹名在导入时固定)。
 * GBA 存档落在游戏目录下的 saves/(模拟器自动同步),删游戏连带清存档。
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
        fileService.deleteGameFiles(g.getFileUrl());
        gameMapper.deleteById(id);
    }

    /** GBA 存档列表:游戏目录 saves/ 下的文件,按最后修改时间倒序 */
    public List<GameSaveDTO> listSaves(Long familyId, Long id) {
        GameInfo g = requireGba(familyId, id);
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null) return List.of();
        return fileService.listGameSaves(dir).stream().map(GameInfoService::toSaveDto).toList();
    }

    /** 上传/覆盖存档:文件名取客户端(模拟器)的存档名,消毒 + 扩展名白名单后落到 saves/ */
    public GameSaveDTO uploadSave(Long familyId, Long id, MultipartFile file) {
        GameInfo g = requireGba(familyId, id);
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "存档文件为空");
        }
        if (file.getSize() > GameSaveName.MAX_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "存档文件过大(上限 4MB)");
        }
        String name = GameSaveName.sanitize(file.getOriginalFilename());
        if (!GameSaveName.allowed(name)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只支持 .srm / .sav / .state 存档文件");
        }
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null) throw new BizException(ResultCode.NOT_FOUND);
        return toSaveDto(fileService.writeGameSave(dir, name, file));
    }

    /** 存档文件路径(下载用),不存在 404 */
    public Path saveFile(Long familyId, Long id, String name) {
        GameInfo g = requireGba(familyId, id);
        String safe = GameSaveName.sanitize(name);
        if (!GameSaveName.allowed(safe)) throw new BizException(ResultCode.NOT_FOUND);
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null) throw new BizException(ResultCode.NOT_FOUND);
        Path file = dir.resolve("saves").resolve(safe);
        if (!Files.isRegularFile(file)) throw new BizException(ResultCode.NOT_FOUND);
        return file;
    }

    /** 删除存档文件,不存在 404 */
    public void deleteSave(Long familyId, Long id, String name) {
        GameInfo g = requireGba(familyId, id);
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null || !fileService.deleteGameSave(dir, name)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /**
     * H5 小游戏通关奖励:每局固定加分,按天限次防刷。
     * 计数器走 Redis(原子自增 + 次日零时过期),Redis 不可用时降级为直接奖励,不阻塞游戏体验。
     * ponytail: 低并发家庭场景,INCR 后校验存在极小并发窗口,每日上限为软性约束而非硬安全边界。
     */
    public Map<String, Object> petLinkReward(Long userId, Long familyId) {
        return h5GameReward(userId, familyId, PointsRuleConst.GAME_PETLINK, "petlink",
                GameConst.PETLINK_DAILY_LIMIT, "宠物连连看通关");
    }

    /**
     * 数独完成奖励(每日限次口径与连连看一致)。
     */
    public Map<String, Object> sudokuReward(Long userId, Long familyId) {
        return h5GameReward(userId, familyId, PointsRuleConst.GAME_SUDOKU, "sudoku",
                GameConst.H5_GAME_DAILY_LIMIT, "数独完成");
    }

    /**
     * 2048 达成奖励(首次合并出 2048 时前端上报,每日限次防刷)。
     */
    public Map<String, Object> game2048Reward(Long userId, Long familyId) {
        return h5GameReward(userId, familyId, PointsRuleConst.GAME_2048, "2048",
                GameConst.H5_GAME_DAILY_LIMIT, "2048 达成");
    }

    private Map<String, Object> h5GameReward(Long userId, Long familyId, String featureCode,
                                             String counterKey, int dailyLimit, String remark) {
        Map<String, Object> map = new HashMap<>();
        if (!pointsService.ruleEnabled(familyId, featureCode)) {
            map.put("awarded", 0);
            map.put("balance", pointsService.balance(userId));
            map.put("remainingToday", 0);
            return map;
        }
        LocalDate today = LocalDate.now();
        String key = "ihomy:game:" + counterKey + ":" + userId + ":" + today;
        long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count == 1) {
                redis.expire(key, Duration.ofSeconds(secondsUntilMidnight()));
            }
        } catch (Exception e) {
            count = 1;
        }
        if (count > dailyLimit) {
            map.put("awarded", 0);
            map.put("balance", pointsService.balance(userId));
            map.put("remainingToday", 0);
            return map;
        }
        int awarded = pointsService.rulePoints(familyId, featureCode);
        pointsService.addRecord(userId, familyId, "REWARD", awarded, remark);
        map.put("awarded", awarded);
        map.put("balance", pointsService.balance(userId));
        map.put("remainingToday", (int) Math.max(0, dailyLimit - count));
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

    /** 存档只属于 GBA 游戏(SWF 是小游戏文件,没有模拟器存档) */
    private GameInfo requireGba(Long familyId, Long id) {
        GameInfo g = require(familyId, id);
        if (!GameConst.TYPE_GBA.equals(g.getType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有 GBA 游戏有存档");
        }
        return g;
    }

    private static GameSaveDTO toSaveDto(Path file) {
        long size = 0;
        long updatedAt = 0;
        try {
            size = Files.size(file);
            updatedAt = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
            // 文件刚被删/不可读时按 0 返回,前端仍能显示文件名
        }
        return new GameSaveDTO(file.getFileName().toString(), size, updatedAt);
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
