package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.ItemDTO;
import com.ihomy.entity.House;
import com.ihomy.entity.Item;
import com.ihomy.entity.Room;
import com.ihomy.mapper.FurnitureMapper;
import com.ihomy.mapper.HouseMapper;
import com.ihomy.mapper.ItemMapper;
import com.ihomy.mapper.RoomMapper;
import com.ihomy.service.access.ContentAccessService;
import com.ihomy.service.access.ContentAccessService.VisibleFilter;
import com.ihomy.service.access.ViewerScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 协作房子的物品读写口径:共享家庭能看到并新增物品,行归房主家庭、created_by 记实际操作人;
 * 别人录的 PRIVATE 物品不可见也不可改。
 * 纯逻辑单测(Mockito 桩),不起 Spring/DB。
 */
class ItemServiceShareTest {

    private static final long OWNER_FAMILY = 2L;
    private static final long GRANTEE_FAMILY = 42L;

    private final HouseMapper houseMapper = mock(HouseMapper.class);
    private final RoomMapper roomMapper = mock(RoomMapper.class);
    private final FurnitureMapper furnitureMapper = mock(FurnitureMapper.class);
    private final ItemMapper itemMapper = mock(ItemMapper.class);
    private final ContentAccessService contentAccess = mock(ContentAccessService.class);
    private final HouseShareService houseShareService = mock(HouseShareService.class);

    private final ItemService service = new ItemService(
            houseMapper, roomMapper, furnitureMapper, itemMapper, contentAccess, houseShareService);

    private static ViewerScope viewer(Long userId, long familyId) {
        return ViewerScope.of(userId, familyId, false);
    }

    private static House sharedHouse() {
        House h = new House();
        h.setId(1L);
        h.setFamilyId(OWNER_FAMILY);
        h.setName("和岳府");
        return h;
    }

    private static Room room() {
        Room r = new Room();
        r.setId(10L);
        r.setFamilyId(OWNER_FAMILY);
        r.setHouseId(1L);
        r.setName("厨房");
        return r;
    }

    @Test
    void itemSearchPassesSharedHouseIds() {
        when(houseShareService.sharedHouseIds(GRANTEE_FAMILY)).thenReturn(List.of(1L));
        when(contentAccess.visibleFilter(any()))
                .thenReturn(new VisibleFilter(GRANTEE_FAMILY, null, List.of(), true));

        service.itemList(viewer(9L, GRANTEE_FAMILY), " 钥匙 ", null, null, null, 500);

        verify(itemMapper).selectItemByFamily(eq(GRANTEE_FAMILY), eq("钥匙"), isNull(), isNull(), isNull(),
                any(), eq(List.of(1L)), eq(100));
    }

    @Test
    void itemCreatedInSharedRoomBelongsToOwnerFamilyButRecordsRealAuthor() {
        when(roomMapper.selectById(10L)).thenReturn(room());
        when(houseShareService.houseOfRoom(10L)).thenReturn(sharedHouse());
        when(houseShareService.canWrite(1L, OWNER_FAMILY, viewer(9L, GRANTEE_FAMILY))).thenReturn(true);

        ItemDTO dto = new ItemDTO();
        dto.setName("酱油");
        dto.setRoomId(10L);
        Item created = service.itemCreate(viewer(9L, GRANTEE_FAMILY), dto);

        verify(itemMapper).insert(created);
        assertThat(created.getFamilyId()).isEqualTo(OWNER_FAMILY);
        assertThat(created.getCreatedBy()).isEqualTo(9L);
    }

    @Test
    void writingIntoHouseWithoutGrantIsNotFound() {
        when(roomMapper.selectById(10L)).thenReturn(room());
        when(houseShareService.houseOfRoom(10L)).thenReturn(sharedHouse());
        when(houseShareService.canWrite(any(), any(), any())).thenReturn(false);

        ItemDTO dto = new ItemDTO();
        dto.setName("酱油");
        dto.setRoomId(10L);

        assertThatThrownBy(() -> service.itemCreate(viewer(9L, 7L), dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
        verify(itemMapper, never()).insert(any(Item.class));
    }

    @Test
    void privateItemOfAnotherAuthorInSharedHouseIsNotEditable() {
        Item item = new Item();
        item.setId(100L);
        item.setFamilyId(OWNER_FAMILY);
        item.setRoomId(10L);
        item.setVisibility(DictConst.VIS_PRIVATE);
        item.setCreatedBy(5L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(houseShareService.houseOf(null, 10L)).thenReturn(sharedHouse());
        when(houseShareService.canWrite(1L, OWNER_FAMILY, viewer(9L, GRANTEE_FAMILY))).thenReturn(true);

        assertThatThrownBy(() -> service.itemDelete(100L, viewer(9L, GRANTEE_FAMILY)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
        verify(itemMapper, never()).deleteById(100L);
    }

    @Test
    void takeItemUsesOwnerFamilyOfTheRow() {
        Item item = new Item();
        item.setId(100L);
        item.setFamilyId(OWNER_FAMILY);
        item.setRoomId(10L);
        item.setVisibility(DictConst.VIS_PUBLIC);
        item.setCreatedBy(5L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(houseShareService.houseOf(null, 10L)).thenReturn(sharedHouse());
        when(houseShareService.canWrite(1L, OWNER_FAMILY, viewer(9L, GRANTEE_FAMILY))).thenReturn(true);
        when(itemMapper.takeAmount(eq(100L), eq(OWNER_FAMILY), any())).thenReturn(1);

        service.takeItem(100L, new java.math.BigDecimal("2"), viewer(9L, GRANTEE_FAMILY));

        ArgumentCaptor<java.math.BigDecimal> amount = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        verify(itemMapper).takeAmount(eq(100L), eq(OWNER_FAMILY), amount.capture());
        assertThat(amount.getValue()).isEqualByComparingTo("2");
    }
}
