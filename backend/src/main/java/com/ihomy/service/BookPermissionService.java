package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.BookPermission;
import com.ihomy.mapper.BookPermissionMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记账本权限:家长按成员配置整本账本的可见与读写档位(等级见 DictConst.BOOK_PERM_*)。
 *
 * 默认口径 = 家庭成员都能读写:没有配置行即 WRITE,历史行为不变;
 * 家长(OWNER)恒为 MANAGE,且只有家长能改别人的档位。
 * 本服务不做缓存:改档要立刻生效(撤销权限不能等 5 分钟 TTL),一行主键查询本身够便宜。
 */
@Service
@RequiredArgsConstructor
public class BookPermissionService {

    private static final String ROLE_OWNER = "OWNER";

    private final BookPermissionMapper permMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;

    /** 当前登录用户在本家庭的账本等级;未登录按不可见处理(游客本就没有账本) */
    public String levelOf(LoginUser user) {
        if (user == null) return DictConst.BOOK_PERM_NONE;
        return levelOf(user.getFamilyId(), user.getUserId(), user.getRole());
    }

    /** 指定用户在指定家庭的账本等级:OWNER 恒可管理,无配置行落默认「可记账」 */
    public String levelOf(Long familyId, Long userId, String roleCode) {
        if (familyId == null || userId == null) return DictConst.BOOK_PERM_NONE;
        if (ROLE_OWNER.equals(roleCode)) return DictConst.BOOK_PERM_MANAGE;
        BookPermission row = selectOne(familyId, userId);
        return row == null ? DictConst.BOOK_PERM_WRITE : DictConst.bookPermLevel(row.getPermLevel());
    }

    public boolean canRead(String level) {
        return rank(level) >= rank(DictConst.BOOK_PERM_READ);
    }

    public boolean canWrite(String level) {
        return rank(level) >= rank(DictConst.BOOK_PERM_WRITE);
    }

    public boolean canManage(String level) {
        return rank(level) >= rank(DictConst.BOOK_PERM_MANAGE);
    }

    /** 读接口守门:返回当前等级,供调用方继续判 canManage */
    public String requireRead(LoginUser user) {
        String level = levelOf(user);
        if (!canRead(level)) throw new BizException(ResultCode.FORBIDDEN, "家长还没有向你开放家庭账本");
        return level;
    }

    /** 写接口守门:只读档位拒绝新增/修改/删除 */
    public String requireWrite(LoginUser user) {
        String level = requireRead(user);
        if (!canWrite(level)) throw new BizException(ResultCode.FORBIDDEN, "家庭账本当前为只读，无法记账或修改");
        return level;
    }

    /** 权限配置页数据:本人等级 + 家长可见的成员档位列表 */
    public Map<String, Object> overview(LoginUser user) {
        boolean isOwner = ROLE_OWNER.equals(user.getRole());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("level", levelOf(user));
        out.put("isOwner", isOwner);
        out.put("levels", List.of(DictConst.BOOK_PERM_NONE, DictConst.BOOK_PERM_READ,
                DictConst.BOOK_PERM_WRITE, DictConst.BOOK_PERM_MANAGE));
        out.put("members", isOwner ? membersWithLevel(user.getFamilyId()) : List.of());
        return out;
    }

    /** 家长给某个成员设置档位;家长本人与 OWNER 不能被降级(避免把自己锁在账本外) */
    @Transactional
    public void setLevel(LoginUser operator, Long targetUserId, String level) {
        if (!ROLE_OWNER.equals(operator.getRole())) throw new BizException(ResultCode.FORBIDDEN);
        if (targetUserId == null) throw new BizException(ResultCode.BAD_REQUEST, "请选择要设置的家庭成员");
        if (!DictConst.isBookPermLevel(level)) throw new BizException(ResultCode.BAD_REQUEST, "未知的账本权限档位");
        String targetRole = sysRoleMapper.selectRoleCodeByUserAndFamily(targetUserId, operator.getFamilyId());
        if (targetRole == null) throw new BizException(ResultCode.NOT_FOUND, "该用户不在当前家庭");
        if (ROLE_OWNER.equals(targetRole)) throw new BizException(ResultCode.BAD_REQUEST, "家长始终拥有账本全部权限");

        String normalized = DictConst.bookPermLevel(level);
        BookPermission existing = selectOne(operator.getFamilyId(), targetUserId);
        if (existing == null) {
            BookPermission row = new BookPermission();
            row.setFamilyId(operator.getFamilyId());
            row.setUserId(targetUserId);
            row.setPermLevel(normalized);
            row.setCreatedBy(operator.getUserId());
            permMapper.insert(row);
            return;
        }
        // 只 SET 业务列:updatedAt 交给 DB 的 ON UPDATE CURRENT_TIMESTAMP
        permMapper.update(null, new LambdaUpdateWrapper<BookPermission>()
                .eq(BookPermission::getId, existing.getId())
                .set(BookPermission::getPermLevel, normalized));
    }

    /** 家庭成员 + 各自账本档位(一次批量取配置行,禁 N+1) */
    private List<Map<String, Object>> membersWithLevel(Long familyId) {
        List<Map<String, Object>> raw = sysUserMapper.selectMembersByFamily(familyId);
        if (raw == null || raw.isEmpty()) return List.of();
        List<Long> userIds = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            userIds.add(asLong(m.get("id")));
        }
        Map<Long, String> explicit = new HashMap<>();
        for (BookPermission p : permMapper.selectList(new LambdaQueryWrapper<BookPermission>()
                .eq(BookPermission::getFamilyId, familyId)
                .in(BookPermission::getUserId, userIds))) {
            explicit.put(p.getUserId(), p.getPermLevel());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            Long userId = asLong(m.get("id"));
            String roleCode = (String) m.get("roleCode");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("nickname", m.get("nickname"));
            row.put("username", m.get("username"));
            row.put("avatar", m.get("avatar"));
            row.put("roleCode", roleCode);
            row.put("level", ROLE_OWNER.equals(roleCode) ? DictConst.BOOK_PERM_MANAGE
                    : explicit.containsKey(userId) ? DictConst.bookPermLevel(explicit.get(userId))
                    : DictConst.BOOK_PERM_WRITE);
            row.put("explicit", explicit.containsKey(userId));
            out.add(row);
        }
        return out;
    }

    private BookPermission selectOne(Long familyId, Long userId) {
        List<BookPermission> rows = permMapper.selectList(new LambdaQueryWrapper<BookPermission>()
                .eq(BookPermission::getFamilyId, familyId)
                .eq(BookPermission::getUserId, userId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 档位排序:NONE < READ < WRITE < MANAGE(脏值按默认 WRITE 参与比较) */
    private static int rank(String level) {
        return switch (DictConst.bookPermLevel(level)) {
            case DictConst.BOOK_PERM_NONE -> 0;
            case DictConst.BOOK_PERM_READ -> 1;
            case DictConst.BOOK_PERM_MANAGE -> 3;
            default -> 2;
        };
    }

    private static Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }
}
