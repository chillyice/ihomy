package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.GameCategory;
import com.ihomy.common.GameConst;
import com.ihomy.common.GameLinkUrl;
import com.ihomy.common.GameSaveName;
import com.ihomy.common.ResultCode;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.dto.GameInfoDTO;
import com.ihomy.dto.GameLinkDTO;
import com.ihomy.dto.GameSaveDTO;
import com.ihomy.entity.GameInfo;
import com.ihomy.entity.GameSiteAccount;
import com.ihomy.mapper.GameInfoMapper;
import com.ihomy.mapper.GameSiteAccountMapper;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家庭小游戏业务:导入 .swf(存 games/{游戏名}/)与 .gba、添加外部网页游戏(LINK)、列表/详情/改名/改描述/删除,家庭隔离。
 * 仅支持 SWF/GBA 两种文件导入类型(.gbc/.gb 仅前端本地模式播放,不入库);LINK 类型只存外链,点卡片新标签页打开。
 * 改名不移动物理文件夹(文件夹名在导入时固定)。GBA 存档落在游戏目录下的 saves/(模拟器自动同步),
 * 删游戏连带清存档;LINK 游戏无文件,删除不动磁盘。
 */
@Service
@RequiredArgsConstructor
public class GameInfoService {

    private final GameInfoMapper gameMapper;
    private final GameSiteAccountMapper siteAccountMapper;
    private final FileService fileService;
    private final StringRedisTemplate redis;
    private final PointsService pointsService;

    public List<GameInfo> list(Long familyId, Long userId) {
        List<GameInfo> games = gameMapper.selectList(new LambdaQueryWrapper<GameInfo>()
                .eq(GameInfo::getFamilyId, familyId)
                .orderByDesc(GameInfo::getCreatedAt));
        if (!games.isEmpty() && userId != null) {
            markHasAccount(games, userId);
        }
        return games;
    }

    /** 一次批量查询当前成员保存过站点账号的游戏,回填 hasAccount(避免卡片逐个查) */
    private void markHasAccount(List<GameInfo> games, Long userId) {
        Set<Long> withAccount = siteAccountMapper.selectList(new LambdaQueryWrapper<GameSiteAccount>()
                        .select(GameSiteAccount::getGameId)
                        .eq(GameSiteAccount::getUserId, userId))
                .stream().map(GameSiteAccount::getGameId).collect(Collectors.toSet());
        games.forEach(g -> g.setHasAccount(withAccount.contains(g.getId())));
    }

    public GameInfo get(Long familyId, Long id) {
        return require(familyId, id);
    }

    public GameInfo importGame(Long userId, Long familyId, MultipartFile file, String name, String description,
                               String category) {
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
        g.setCategory(GameCategory.normalize(category));
        g.setFileUrl(url);
        g.setStatus(GameConst.STATUS_ACTIVE);
        gameMapper.insert(g);
        return require(familyId, g.getId());
    }

    /** 添加外部网页游戏(LINK):只存网址,点开新标签页跳转 */
    public GameInfo importLink(Long userId, Long familyId, GameLinkDTO dto) {
        String cleanName = normalizeName(dto.getName());
        String linkUrl = GameLinkUrl.normalize(dto.getUrl());
        GameInfo g = new GameInfo();
        g.setFamilyId(familyId);
        g.setUserId(userId);
        g.setName(cleanName);
        g.setDescription(normalizeDescription(dto.getDescription()));
        g.setType(GameConst.TYPE_LINK);
        g.setCategory(GameCategory.normalize(dto.getCategory()));
        g.setLinkUrl(linkUrl);
        g.setStatus(GameConst.STATUS_ACTIVE);
        gameMapper.insert(g);
        return require(familyId, g.getId());
    }

    public GameInfo update(Long familyId, Long id, GameInfoDTO dto) {
        GameInfo game = require(familyId, id);
        String cleanName = normalizeName(dto == null ? null : dto.getName());
        String cleanDesc = normalizeDescription(dto == null ? null : dto.getDescription());
        LambdaUpdateWrapper<GameInfo> updater = new LambdaUpdateWrapper<GameInfo>()
                .eq(GameInfo::getId, id)
                .eq(GameInfo::getFamilyId, familyId)
                .set(GameInfo::getName, cleanName)
                .set(GameInfo::getDescription, cleanDesc);
        // 分类未提供(旧调用方)时保持原值;提供了就归一后写入
        if (dto != null && dto.getCategory() != null && !dto.getCategory().isBlank()) {
            updater.set(GameInfo::getCategory, GameCategory.normalize(dto.getCategory()));
        }
        // LINK 游戏可改网址;文件型游戏忽略该字段
        if (GameConst.TYPE_LINK.equals(game.getType()) && dto != null && dto.getLinkUrl() != null
                && !dto.getLinkUrl().isBlank()) {
            updater.set(GameInfo::getLinkUrl, GameLinkUrl.normalize(dto.getLinkUrl()));
        }
        gameMapper.update(null, updater);
        return require(familyId, id);
    }

    public void delete(Long familyId, Long id) {
        GameInfo g = require(familyId, id);
        if (!GameConst.TYPE_LINK.equals(g.getType())) {
            fileService.deleteGameFiles(g.getFileUrl());
        }
        gameMapper.deleteById(id);
    }

    /** GBA 存档列表:只列当前成员自己的存档,按最后修改时间倒序 */
    public List<GameSaveDTO> listSaves(Long familyId, Long userId, Long id) {
        GameInfo g = requireGba(familyId, id);
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null) return List.of();
        fileService.migrateLegacyGameSaves(dir, g.getUserId());
        return fileService.listGameSaves(dir, userId).stream().map(GameInfoService::toSaveDto).toList();
    }

    /** 上传/覆盖存档:文件名取客户端(模拟器)的存档名,消毒 + 扩展名白名单后落到当前成员的 saves/u{id}/ */
    public GameSaveDTO uploadSave(Long familyId, Long userId, Long id, MultipartFile file) {
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
        fileService.migrateLegacyGameSaves(dir, g.getUserId());
        return toSaveDto(fileService.writeGameSave(dir, userId, name, file));
    }

    /** 当前成员的存档文件路径(下载用),不存在 404 */
    public Path saveFile(Long familyId, Long userId, Long id, String name) {
        GameInfo g = requireGba(familyId, id);
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null) throw new BizException(ResultCode.NOT_FOUND);
        fileService.migrateLegacyGameSaves(dir, g.getUserId());
        Path file = fileService.findGameSave(dir, userId, name);
        if (file == null) throw new BizException(ResultCode.NOT_FOUND);
        return file;
    }

    /** 删除当前成员的存档文件,不存在 404 */
    public void deleteSave(Long familyId, Long userId, Long id, String name) {
        GameInfo g = requireGba(familyId, id);
        Path dir = fileService.gameDirectory(g.getFileUrl());
        if (dir == null) throw new BizException(ResultCode.NOT_FOUND);
        fileService.migrateLegacyGameSaves(dir, g.getUserId());
        if (!fileService.deleteGameSave(dir, userId, name)) {
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

    /** 家庭隔离取游戏:不属于当前家庭一律 NOT_FOUND(站点账号等服务复用) */
    public GameInfo require(Long familyId, Long id) {
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
