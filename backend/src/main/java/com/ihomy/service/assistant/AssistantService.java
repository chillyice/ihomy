package com.ihomy.service.assistant;

import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.mapper.AssistantMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AiService;
import com.ihomy.service.AuthGuardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {
    private final AssistantMapper db;
    private final SysRoleMapper roles;
    private final SecurityHelper security;
    private final AiService ai;
    private final AssistantTurn turns;
    private final AssistantTools tools;
    private final AuthGuardService guard;
    private final TransactionOperations tx;
    private final ObjectMapper json = new ObjectMapper();
    private static final TypeReference<Map<String,Object>> OBJECT = new TypeReference<>() {};

    private LoginUser member() {
        LoginUser u = security.current();
        if (u == null) throw new BizException(ResultCode.UNAUTHORIZED);
        if (u.getFamilyId() == null || "OPS".equals(u.getRole())) throw new BizException(ResultCode.FORBIDDEN);
        // 角色可能在令牌签发后被撤销或变更，后续业务服务也必须看到同一个有效身份。
        if (u.getRole() == null || !u.getRole().equals(roles.selectRoleCodeByUserAndFamily(u.getUserId(), u.getFamilyId()))) {
            throw new BizException(ResultCode.FORBIDDEN, "家庭成员权限已变更，请刷新登录状态");
        }
        return u;
    }

    private void enabled(LoginUser u, boolean lock) {
        if (!Boolean.TRUE.equals(db.enabled(u.getFamilyId(), lock))) {
            throw new BizException(ResultCode.FORBIDDEN, "本家庭已停用助手");
        }
    }

    public Map<String,Object> status() {
        LoginUser u = member();
        boolean on = Boolean.TRUE.equals(db.enabled(u.getFamilyId(), false));
        var llm = ai.capability(u.getFamilyId(), AiConst.FEATURE_ASSISTANT);
        var asr = ai.capability(u.getFamilyId(), AiConst.FEATURE_ASR);
        boolean available = on && Boolean.TRUE.equals(llm.get("available"));
        return Map.of("enabled",on,"available",available,"model",llm.get("model"),
                "asrAvailable",available && Boolean.TRUE.equals(asr.get("available")),
                "reason",!on ? "本家庭已停用助手" : available ? "" : "请在家庭 AI 设置中为助手选择模型");
    }

    public void setEnabled(boolean on) {
        LoginUser u = member();
        if ("GUEST".equals(u.getRole())) throw new BizException(ResultCode.FORBIDDEN);
        db.setEnabled(u.getFamilyId(), on);
    }

    public Map<String,Object> transcribe(MultipartFile audio, String language, Integer rate) {
        LoginUser u = member();
        enabled(u, false);
        if (audio == null || audio.isEmpty() || audio.getSize() > 20L * 1024 * 1024) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择小于 20 MB 的录音");
        }
        guard.checkRate("assistant-asr", u.getUserId().toString(), 60, 3600);
        return ai.transcribe(u.getFamilyId(), audio, language, rate);
    }

    public List<Map<String,Object>> catalogForOps() {
        Long uid = security.currentUserId();
        if (uid == null || roles.countOpsRole(uid) == 0) throw new BizException(ResultCode.FORBIDDEN);
        return tools.catalog();
    }

    public int pending() {
        LoginUser u = member();
        return db.pending(u.getUserId(), u.getFamilyId());
    }

    public Map<String,Object> conversation(Long beforeId) {
        LoginUser u = member();
        return tx.execute(s -> {
            Long cid = db.conversation(u.getUserId(), u.getFamilyId());
            List<Map<String,Object>> rows = cid == null ? List.of() : db.messages(cid, beforeId, 100);
            if (!rows.isEmpty()) {
                Collections.reverse(rows);
                Map<Long,List<Map<String,Object>>> byMessage = new HashMap<>();
                for (var card : db.actions(cid, id(rows.getFirst()), id(rows.getLast()))) {
                    long messageId = ((Number)card.get("messageId")).longValue();
                    byMessage.computeIfAbsent(messageId, k -> new ArrayList<>()).add(cardView(card));
                }
                for (var m : rows) {
                    String payload = (String)m.remove("payload");
                    if (payload != null) m.putAll(decode(payload));
                    m.put("pendingActions", byMessage.getOrDefault(id(m), List.of()));
                }
            }
            Map<String,Object> data = new LinkedHashMap<>();
            data.put("conversationId", cid);
            data.put("messages", rows);
            return data;
        });
    }

    public void clear() {
        LoginUser u = member();
        tx.executeWithoutResult(s -> {
            Long cid = db.conversation(u.getUserId(), u.getFamilyId());
            if (cid != null) {
                db.clearTurn(cid);
                trim(cid, Long.MAX_VALUE);
            }
        });
    }

    public Map<String,Object> chat(String text, Long requested, String path) {
        LoginUser u = member();
        enabled(u,false);
        guard.checkRate("assistant", u.getUserId().toString(), 30, 3600);
        String key = UUID.randomUUID().toString();
        long cid = tx.execute(s -> {
            db.ensureConversation(u.getUserId(),u.getFamilyId());
            Long id = db.conversation(u.getUserId(),u.getFamilyId());
            if (requested != null && !requested.equals(id)) throw new BizException(ResultCode.NOT_FOUND);
            if (db.claimTurn(id,key) != 1) throw new BizException(ResultCode.CONFLICT, "上一个问题仍在处理");
            return id;
        });
        try {
            var answer = turns.run(u, text, path, history(cid), () -> enabled(member(), false));
            return tx.execute(s -> {
                enabled(member(),true);
                if (db.finishTurn(cid,key,text) != 1) throw new BizException(ResultCode.CONFLICT, "对话已清空或更新");
                append(cid, "USER", text, null);
                String payload = json.writeValueAsString(Map.of("toolRuns",answer.runs(),"notices",answer.notices()));
                long mid = append(cid, "ASSISTANT", answer.text(), payload);
                List<Map<String,Object>> cards = new ArrayList<>();
                for (var draft : answer.drafts()) {
                    Map<String,Object> row = new HashMap<>(Map.of("cid",cid,"messageId",mid,"uid",u.getUserId(),
                            "fid",u.getFamilyId(),"toolName",draft.name(),"preview",draft.preview(),
                            "args",json.writeValueAsString(draft.args()),"status","PENDING"));
                    db.insertAction(row);
                    cards.add(cardView(row));
                }
                Long through = db.pruneBefore(cid);
                if (through != null) trim(cid, through);
                return Map.<String,Object>of("conversationId",cid,"messageId",mid,"reply",answer.text(),
                        "toolRuns",answer.runs(),"pendingActions",cards,"notices",answer.notices(),
                        "usage",Map.of("rounds",answer.rounds()));
            });
        } finally {
            db.releaseTurn(cid,key);
        }
    }

    private List<Map<String,Object>> history(long cid) {
        List<Map<String,Object>> out = new ArrayList<>();
        int remaining = 12000;
        for (var row : db.messages(cid,null,20)) {
            String content = AssistantTurn.limit((String)row.get("content"),remaining);
            out.add(Map.of("role",row.get("role").toString().toLowerCase(Locale.ROOT),"content",content));
            remaining -= content.length();
            if (remaining <= 0) break;
        }
        Collections.reverse(out);
        return out;
    }

    private long append(long cid, String role, String text, String payload) {
        Map<String,Object> row = new HashMap<>(Map.of("cid",cid,"role",role,"content",text));
        row.put("payload",payload);
        db.insertMessage(row);
        return id(row);
    }

    private void trim(long cid, long through) {
        db.cancelOld(cid,through);
        db.deleteMessages(cid,through);
    }

    public Map<String,Object> decide(long id, boolean confirm, String visibility) {
        LoginUser u = member();
        try {
            return tx.execute(s -> {
                if (confirm) enabled(u,true);
                Map<String,Object> row = ownedAction(u,id);
                if (!"PENDING".equals(row.get("status"))) return cardView(row);
                if (!confirm) {
                    db.decide(id,"CANCELLED",null,null,null);
                } else {
                    if ("GUEST".equals(u.getRole())) throw new BizException(ResultCode.FORBIDDEN);
                    String name = row.get("toolName").toString();
                    Map<String,Object> args = decode(row.get("args").toString());
                    if (visibility != null) {
                        if (!"create_diary".equals(name) || !Set.of("PRIVATE","FAMILY").contains(visibility)) {
                            throw new BizException(ResultCode.BAD_REQUEST, "这项操作不能更改可见范围");
                        }
                        args.put("visibility",visibility);
                    }
                    Map<String,Object> result;
                    try {
                        result = tools.execute(name,args);
                    } catch (RuntimeException e) {
                        throw new ActionFailure(e);
                    }
                    db.decide(id,"DONE",json.writeValueAsString(args),json.writeValueAsString(result),null);
                    log.info("[AI助手] action={} tool={} uid={} fid={}",id,name,u.getUserId(),u.getFamilyId());
                }
                return cardView(ownedAction(u,id));
            });
        } catch (ActionFailure e) {
            Throwable cause = e.getCause();
            String reason = cause instanceof BizException ? cause.getMessage() : "操作失败，请重新核对后再试";
            if (!(cause instanceof BizException)) log.error("[AI助手] action={} uid={}", id,u.getUserId(),cause);
            // 业务写入已整体回滚。若另一个请求先完成，回放其结果，不覆盖最终状态。
            return tx.execute(s -> {
                var row = ownedAction(u,id);
                if ("PENDING".equals(row.get("status"))) db.decide(id,"FAILED",null,null,AssistantTurn.limit(reason,500));
                return cardView(ownedAction(u,id));
            });
        }
    }

    private Map<String,Object> ownedAction(LoginUser u,long id) {
        var row = db.lockAction(u.getUserId(),u.getFamilyId(),id);
        if (row == null) throw new BizException(ResultCode.NOT_FOUND);
        return row;
    }

    private Map<String,Object> cardView(Map<String,Object> row) {
        Map<String,Object> card = new LinkedHashMap<>();
        for (String key : List.of("id","toolName","status","preview","error")) {
            if (row.get(key) != null) card.put(key,row.get(key));
        }
        if ("create_diary".equals(row.get("toolName"))) {
            card.put("visibility",decode((String)row.get("args")).getOrDefault("visibility","PRIVATE"));
        }
        if (row.get("result") != null) card.put("result",decode(row.get("result").toString()));
        return card;
    }

    private Map<String,Object> decode(String value) { return json.readValue(value,OBJECT); }
    private static long id(Map<String,Object> row) { return ((Number)row.get("id")).longValue(); }
    private static final class ActionFailure extends RuntimeException {
        ActionFailure(RuntimeException cause) { super(cause); }
    }

    @Scheduled(cron="0 25 4 * * *")
    public void cleanActions() { db.deleteExpired(); }
}
