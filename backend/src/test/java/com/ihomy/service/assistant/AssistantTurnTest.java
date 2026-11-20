package com.ihomy.service.assistant;

import com.ihomy.security.LoginUser;
import com.ihomy.service.AiService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantTurnTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiService ai = mock(AiService.class);
    private final AssistantTools tools = mock(AssistantTools.class);
    private final AssistantTurn turn = new AssistantTurn(ai, tools);
    private final LoginUser user = new LoginUser(9L, "test", "CHILD", 7L);

    private JsonNode call(String name) {
        return json.valueToTree(Map.of("role","assistant","tool_calls",List.of(Map.of("id","call42",
                "type","function","function",Map.of("name",name,"arguments","{}")))));
    }

    @Test
    void readResultIsReturnedToModelWithTheCallIdAndCurrentIdentity() {
        when(tools.read("get_schedule",Map.of())).thenReturn(Map.of("title","游泳"));
        when(ai.chatWithTools(eq(7L),anyList(),anyList(),anyLong()))
                .thenReturn(call("get_schedule"))
                .thenAnswer(i -> {
                    List<Map<String,Object>> messages = i.getArgument(1);
                    assertEquals("call42",messages.getLast().get("tool_call_id"));
                    assertTrue(messages.getLast().get("content").toString().contains("游泳"));
                    return json.valueToTree(Map.of("content","今天有游泳课"));
                });
        var reply = turn.run(user,"今天有什么安排",null,List.of(),() -> {});
        assertEquals("今天有游泳课",reply.text());
        assertEquals(1,reply.runs().size());
        assertTrue(reply.drafts().isEmpty());
    }

    @Test
    void writeOnlyPreparesCardAndStopsWithoutRunningBusinessCommand() {
        when(ai.chatWithTools(eq(7L),anyList(),anyList(),anyLong())).thenReturn(call("create_diary"));
        when(tools.isWrite("create_diary")).thenReturn(true);
        var args = Map.<String,Object>of("visibility","PRIVATE");
        when(tools.prepare("create_diary",Map.of())).thenReturn(args);
        when(tools.preview("create_diary",args)).thenReturn("日记");
        var reply = turn.run(user,"记录日记",null,List.of(),() -> {});
        assertEquals(1,reply.drafts().size());
        assertEquals("PRIVATE",reply.drafts().getFirst().args().get("visibility"));
        verify(tools,never()).execute(anyString(),anyMap());
    }

    @Test
    void guestCannotGetWriteCardEvenWhenModelRepeatedlyAsks() {
        user.setRole("GUEST");
        when(ai.chatWithTools(eq(7L),anyList(),anyList(),anyLong())).thenReturn(call("create_diary"));
        when(tools.isWrite("create_diary")).thenReturn(true);
        var reply = turn.run(user,"记录日记",null,List.of(),() -> {});
        assertEquals(3,reply.rounds());
        assertTrue(reply.drafts().isEmpty());
        verify(tools,never()).prepare(anyString(),anyMap());
    }
}
