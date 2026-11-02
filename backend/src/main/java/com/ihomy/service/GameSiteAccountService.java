package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.GameSiteAccountDTO;
import com.ihomy.dto.GameSiteAccountVO;
import com.ihomy.entity.GameSiteAccount;
import com.ihomy.mapper.GameSiteAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 游戏站点账号业务:成员为外部网页游戏保存登录信息,一人一游戏一行,密码 AES-GCM 加密落库。
 * 只允许读写本人(user_id)的行;家庭隔离靠 GameInfoService.require(游戏不属于本家庭一律 NOT_FOUND)。
 * 删除即物理删(密文不留库),无回收站语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameSiteAccountService {

    private final GameSiteAccountMapper accountMapper;
    private final GameInfoService gameInfoService;
    private final ParameterService parameterService;

    /** 查本人保存在某游戏下的账号(明文仅返回给本人;没存过返回带空字段的视图,前端据此渲染空态) */
    public GameSiteAccountVO get(Long familyId, Long userId, Long gameId) {
        gameInfoService.require(familyId, gameId);
        return toVo(gameId, findOwn(gameId, userId));
    }

    /** 保存(整行覆盖式更新,不存在则插入):密码入库前加密,密文与盐值绑定 */
    public GameSiteAccountVO save(Long familyId, Long userId, Long gameId, GameSiteAccountDTO dto) {
        gameInfoService.require(familyId, gameId);
        String username = normalize(dto == null ? null : dto.getSiteUsername(), 100);
        String password = normalize(dto == null ? null : dto.getSitePassword(), 200);
        String remark = normalize(dto == null ? null : dto.getRemark(), 200);
        if (username == null && password == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请至少填写用户名或密码");
        }
        GameSiteAccount acc = findOwn(gameId, userId);
        if (acc == null) {
            acc = new GameSiteAccount();
            acc.setGameId(gameId);
            acc.setUserId(userId);
            acc.setSiteUsername(username);
            acc.setSitePassword(encrypt(password, gameId));
            acc.setRemark(remark);
            accountMapper.insert(acc);
        } else {
            accountMapper.update(null, new LambdaUpdateWrapper<GameSiteAccount>()
                    .eq(GameSiteAccount::getId, acc.getId())
                    .set(GameSiteAccount::getSiteUsername, username)
                    .set(GameSiteAccount::getSitePassword, encrypt(password, gameId))
                    .set(GameSiteAccount::getRemark, remark));
        }
        return get(familyId, userId, gameId);
    }

    /** 删除本人保存的账号,物理删;本来就没存过按成功处理(幂等) */
    public void delete(Long familyId, Long userId, Long gameId) {
        gameInfoService.require(familyId, gameId);
        accountMapper.delete(new LambdaQueryWrapper<GameSiteAccount>()
                .eq(GameSiteAccount::getGameId, gameId)
                .eq(GameSiteAccount::getUserId, userId));
    }

    private GameSiteAccount findOwn(Long gameId, Long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<GameSiteAccount>()
                .eq(GameSiteAccount::getGameId, gameId)
                .eq(GameSiteAccount::getUserId, userId));
    }

    private String encrypt(String plain, Long gameId) {
        if (plain == null) return null;
        try {
            return parameterService.encrypt(plain);
        } catch (Exception e) {
            log.error("加密站点账号失败, gameId={}", gameId, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "保存失败，请稍后重试");
        }
    }

    private GameSiteAccountVO toVo(Long gameId, GameSiteAccount acc) {
        GameSiteAccountVO vo = new GameSiteAccountVO();
        vo.setGameId(gameId);
        if (acc == null) return vo;
        vo.setSiteUsername(acc.getSiteUsername());
        vo.setRemark(acc.getRemark());
        vo.setUpdatedAt(acc.getUpdatedAt() != null ? acc.getUpdatedAt() : acc.getCreatedAt());
        if (acc.getSitePassword() != null) {
            try {
                vo.setSitePassword(parameterService.decrypt(acc.getSitePassword()));
            } catch (Exception e) {
                // 密文解不开(盐值变更等)时不外泄内容,提示用户重新保存
                log.error("解密站点账号失败, gameId={}", gameId, e);
                throw new BizException(ResultCode.INTERNAL_ERROR, "账号信息读取失败，请重新保存一次");
            }
        }
        return vo;
    }

    private String normalize(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }
}
