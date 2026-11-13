package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysUserPref;
import com.ihomy.mapper.SysUserPrefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户界面偏好:一人一份,跟账号走。
 * 前端把 localStorage 当即时缓存,登录后 GET 对齐、改动走 PUT;服务端只做不透明存取,
 * 不解析 pref_value 的结构 —— 布局语义升级靠键里的版本号(如 home.layout.v3 → home.layout.v4)。
 */
@Service
@RequiredArgsConstructor
public class UserPrefService {

    /** 单人偏好键上限:目前只用到 2 个(两套首页布局),留余量但不给当免费云存储用 */
    private static final int MAX_KEYS_PER_USER = 20;
    /** 单份内容上限(字符):两套布局的 JSON 都在 2KB 内 */
    private static final int MAX_VALUE_LENGTH = 16384;
    /** 键上限(字符):与 sys_user_pref.pref_key 的列宽一致,超了会在写库时炸成 500 */
    private static final int MAX_KEY_LENGTH = 64;
    /** 键格式:点分小写(版本号写在键里),与服务端不解析内容的取舍一致 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z0-9]+)*$");

    private final SysUserPrefMapper userPrefMapper;

    /** 当前用户的全部偏好(键 → JSON 文本);没有则空 Map,前端据此判断要不要把本机布局交上来 */
    public Map<String, String> listMine(Long userId) {
        List<SysUserPref> rows = userPrefMapper.selectList(
                new LambdaQueryWrapper<SysUserPref>().eq(SysUserPref::getUserId, userId));
        Map<String, String> prefs = new HashMap<>();
        for (SysUserPref row : rows) prefs.put(row.getPrefKey(), row.getPrefValue());
        return prefs;
    }

    /** 保存一份偏好:没有就插、有就覆盖;内容与库里一致时直接返回,避免多端来回刷同一份布局 */
    public void save(Long userId, String key, String value) {
        String prefKey = key == null ? "" : key.trim();
        if (!KEY_PATTERN.matcher(prefKey).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "偏好名称格式不正确");
        }
        if (prefKey.length() > MAX_KEY_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "偏好名称过长");
        }
        if (value == null || value.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "偏好内容不能为空");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "偏好内容过大,请恢复默认布局后再试");
        }

        Map<String, String> mine = listMine(userId);
        if (value.equals(mine.get(prefKey))) return;
        // 只拦「新增键」:已有键的覆盖写不该因为条数被挡下
        if (!mine.containsKey(prefKey) && mine.size() >= MAX_KEYS_PER_USER) {
            throw new BizException(ResultCode.BAD_REQUEST, "偏好数量已达上限");
        }
        userPrefMapper.upsert(userId, prefKey, value);
    }
}
