package com.ihomy.common;

/**
 * 聊天消息类型(family_chat_message.msg_type)。
 * 协议面常量,含义不进 sys_dict_item(口径同 GameConst),前端映射见 utils/chatMsg.js。
 * payload 为该类型的结构化 JSON 负载(≤4KB),TEXT 恒为 NULL。
 */
public final class ChatMsgType {

    private ChatMsgType() {
    }

    /** 纯文本(存量默认) */
    public static final String TEXT = "TEXT";
    /** 图片:content=附言,payload={url,width,height} */
    public static final String IMAGE = "IMAGE";
    /** 站外链接:content=URL,payload=unfurl 预览{title,description,image,siteName} */
    public static final String LINK = "LINK";
    /** 站内对象卡:content=标题快照,payload={objType,objId,sub,url,cover} */
    public static final String CARD = "CARD";
    /** 游戏邀请:content=游戏名,payload={gameCode,matchId} */
    public static final String GAME_INVITE = "GAME_INVITE";
    /** 通话记录:content=摘要,payload={media,result,duration} */
    public static final String CALL = "CALL";

    public static boolean isKnown(String value) {
        return TEXT.equals(value) || IMAGE.equals(value) || LINK.equals(value)
                || CARD.equals(value) || GAME_INVITE.equals(value) || CALL.equals(value);
    }
}
