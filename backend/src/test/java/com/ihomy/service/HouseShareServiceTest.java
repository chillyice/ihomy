package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.House;
import com.ihomy.entity.HouseShare;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.FurnitureMapper;
import com.ihomy.mapper.HouseMapper;
import com.ihomy.mapper.HouseShareMapper;
import com.ihomy.mapper.RoomMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.service.access.ViewerScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 房子协作共享的准入口径:本家可读写、被授权家庭可读写、无关家庭一律当不存在;
 * 授权只能给自己所属的其他家庭,删房子与改共享只归房主家庭家长。
 * 纯逻辑单测(Mockito 桩),不起 Spring/DB。
 */
class HouseShareServiceTest {

    private static final long OWNER_FAMILY = 2L;
    private static final long GRANTEE_FAMILY = 42L;

    private final HouseMapper houseMapper = mock(HouseMapper.class);
    private final RoomMapper roomMapper = mock(RoomMapper.class);
    private final FurnitureMapper furnitureMapper = mock(FurnitureMapper.class);
    private final HouseShareMapper houseShareMapper = mock(HouseShareMapper.class);
    private final FamilyMapper familyMapper = mock(FamilyMapper.class);
    private final SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);

    private final HouseShareService service = new HouseShareService(
            houseMapper, roomMapper, furnitureMapper, houseShareMapper, familyMapper, sysUserRoleMapper);

    private House house() {
        House h = new House();
        h.setId(1L);
        h.setFamilyId(OWNER_FAMILY);
        h.setName("和岳府");
        return h;
    }

    private static ViewerScope viewer(Long userId, long familyId) {
        return ViewerScope.of(userId, familyId, false);
    }

    private static SysUserRole role(long familyId) {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(6L);
        ur.setFamilyId(familyId);
        return ur;
    }

    @Test
    void ownFamilyCanReadAndWrite() {
        House h = house();
        ViewerScope v = viewer(6L, OWNER_FAMILY);

        assertThat(service.canRead(h, v)).isTrue();
        assertThat(service.canWrite(h.getId(), h.getFamilyId(), v)).isTrue();
        // 本家不走授权表
        verify(houseShareMapper, never()).selectCount(any());
    }

    @Test
    void grantedFamilyCanReadAndWrite() {
        House h = house();
        ViewerScope v = viewer(9L, GRANTEE_FAMILY);
        when(houseShareMapper.selectCount(any())).thenReturn(1L);

        assertThat(service.canRead(h, v)).isTrue();
        assertThat(service.canWrite(h.getId(), h.getFamilyId(), v)).isTrue();
    }

    @Test
    void unrelatedFamilyIsTreatedAsNotFound() {
        House h = house();
        ViewerScope v = viewer(9L, 7L);
        when(houseShareMapper.selectCount(any())).thenReturn(0L);
        when(houseMapper.selectById(1L)).thenReturn(h);

        assertThat(service.canRead(h, v)).isFalse();
        assertThat(service.canWrite(h.getId(), h.getFamilyId(), v)).isFalse();
        assertThatThrownBy(() -> service.requireReadable(1L, v))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThatThrownBy(() -> service.requireWritable(1L, v))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void viewerWithoutFamilyHasNoHouseAccess() {
        // 判定的基准是「当前家庭」:没有家庭上下文(如纯 OPS)时既不可读也不可写
        House h = house();
        ViewerScope noFamily = ViewerScope.of(6L, null, false);

        assertThat(service.canRead(h, noFamily)).isFalse();
        assertThat(service.canWrite(h.getId(), h.getFamilyId(), noFamily)).isFalse();
        assertThat(service.canRead(null, viewer(6L, OWNER_FAMILY))).isFalse();
    }

    @Test
    void replaceSharesOnlyAcceptsFamiliesOfTheOperator() {
        when(houseMapper.selectById(1L)).thenReturn(house());
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(role(OWNER_FAMILY)));

        assertThatThrownBy(() -> service.replaceShares(1L, ViewerScope.of(6L, OWNER_FAMILY, true), 6L, List.of(7L)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        // 校验失败不留半截授权
        verify(houseShareMapper, never()).delete(any());
        verify(houseShareMapper, never()).insert(any(HouseShare.class));
    }

    @Test
    void replaceSharesDropsOwnFamilyAndInsertsTargets() {
        when(houseMapper.selectById(1L)).thenReturn(house());
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(role(OWNER_FAMILY), role(GRANTEE_FAMILY)));

        service.replaceShares(1L, ViewerScope.of(6L, OWNER_FAMILY, true), 6L, List.of(OWNER_FAMILY, GRANTEE_FAMILY));

        verify(houseShareMapper).delete(any());
        ArgumentCaptor<HouseShare> captor = ArgumentCaptor.forClass(HouseShare.class);
        verify(houseShareMapper).insert(captor.capture());
        assertThat(captor.getValue().getHouseId()).isEqualTo(1L);
        assertThat(captor.getValue().getGranteeFamilyId()).isEqualTo(GRANTEE_FAMILY);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(6L);
    }

    @Test
    void changingSharesRequiresOwnerOfOwningFamily() {
        when(houseMapper.selectById(1L)).thenReturn(house());

        // 本家普通成员:不允许调整共享
        assertThatThrownBy(() -> service.replaceShares(1L, ViewerScope.of(6L, OWNER_FAMILY, false), 6L, List.of()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        // 被共享家庭(哪怕是家长):房子不属于他,当不存在
        assertThatThrownBy(() -> service.replaceShares(1L, ViewerScope.of(9L, GRANTEE_FAMILY, true), 9L, List.of()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }
}
