package com.ihomy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AssistantMapper {
    Boolean enabled(@Param("fid") long familyId, @Param("lock") boolean lock);
    int setEnabled(@Param("fid") long familyId, @Param("enabled") boolean enabled);
    Long conversation(@Param("uid") long userId, @Param("fid") long familyId);
    void ensureConversation(@Param("uid") long userId, @Param("fid") long familyId);
    int claimTurn(@Param("cid") long conversationId, @Param("key") String key);
    int finishTurn(@Param("cid") long conversationId, @Param("key") String key, @Param("title") String title);
    void releaseTurn(@Param("cid") long conversationId, @Param("key") String key);
    void clearTurn(@Param("cid") long conversationId);
    void insertMessage(Map<String,Object> row);
    List<Map<String,Object>> messages(@Param("cid") long conversationId, @Param("before") Long before, @Param("limit") int limit);
    Long pruneBefore(@Param("cid") long conversationId);
    void deleteMessages(@Param("cid") long conversationId, @Param("through") long maxId);
    void cancelOld(@Param("cid") long conversationId, @Param("through") long maxId);
    void insertAction(Map<String,Object> row);
    Map<String,Object> lockAction(@Param("uid") long userId, @Param("fid") long familyId, @Param("id") long actionId);
    List<Map<String,Object>> actions(@Param("cid") long conversationId, @Param("lo") long firstId, @Param("hi") long lastId);
    int pending(@Param("uid") long userId, @Param("fid") long familyId);
    int decide(@Param("id") long id, @Param("status") String status, @Param("args") String args,
               @Param("result") String result, @Param("error") String error);
    int deleteExpired();
}
