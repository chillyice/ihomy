package com.ihomy.service.assistant;

import com.ihomy.common.BizException;
import com.ihomy.mapper.AssistantMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionCallback;

import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantDecisionTest {
    private final AssistantMapper db = mock(AssistantMapper.class);
    private final AssistantTools tools = mock(AssistantTools.class);
    private final SecurityHelper security = mock(SecurityHelper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final TransactionOperations tx = new TransactionOperations() {
        public <T> T execute(TransactionCallback<T> call) { return call.doInTransaction(null); }
    };
    private final AssistantService service = new AssistantService(db,roles,security,null,null,tools,null,tx);
    private final Map<String,Object> card = new HashMap<>(Map.of("id",1L,"status","PENDING",
            "toolName","create_diary","args","{\"visibility\":\"PRIVATE\"}","preview","日记"));

    @BeforeEach
    void setup() {
        when(security.current()).thenReturn(new LoginUser(9L,"user","MEMBER",7L));
        when(roles.selectRoleCodeByUserAndFamily(9L,7L)).thenReturn("MEMBER");
        when(db.enabled(7L,true)).thenReturn(true);
        when(db.lockAction(9L,7L,1L)).thenReturn(card);
    }

    @Test
    void diaryVisibilityIsChosenAtConfirmationAndReplayedOnRetry() {
        when(tools.execute("create_diary",Map.of("visibility","FAMILY"))).thenReturn(Map.of("id",99L));
        when(db.decide(eq(1L),eq("DONE"),anyString(),anyString(),isNull())).thenAnswer(i -> {
            card.put("status","DONE");
            card.put("args",i.getArgument(2));
            card.put("result",i.getArgument(3));
            return 1;
        });
        var saved = service.decide(1L,true,"FAMILY");
        assertEquals("FAMILY",saved.get("visibility"));
        assertEquals(saved,service.decide(1L,true,"PRIVATE"));
        verify(tools,times(1)).execute(anyString(),anyMap());
    }

    @Test
    void revokedMembershipOrDisabledSwitchRejectsExistingCard() {
        when(db.enabled(7L,true)).thenReturn(false);
        assertThrows(BizException.class, () -> service.decide(1L,true,null));
        when(roles.selectRoleCodeByUserAndFamily(9L,7L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.conversation(null));
        verifyNoInteractions(tools);
    }

    @Test
    void actionBelongingToDifferentUserIsNotFound() {
        when(db.lockAction(9L,7L,2L)).thenReturn(null);
        BizException error = assertThrows(BizException.class, () -> service.decide(2L,true,null));
        assertEquals(404,error.getCode());
        verifyNoInteractions(tools);
    }
}
