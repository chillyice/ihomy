package com.ihomy.service.access;

/**
 * 参与可见性判定的内容类型:code 同时是 content_audience.content_type 的取值。
 * 新增可分享内容类型时在这里登记,可见性/分享判定与受众表都以此为准。
 */
public enum ContentKind {

    BLOG("blog"),
    DIARY("diary"),
    PHOTO("photo"),
    ALBUM("album"),
    VIDEO("video"),
    WISH("wish"),
    BOOK("book"),
    /** 纪念日/提醒本身没有 visibility 列,按家庭级可见(FAMILY)处理,分享口径与内容一致 */
    ANNIVERSARY("anniversary"),
    REMINDER("reminder"),
    /** 家庭小游戏(导入/网页游戏):四档可见性,可分享到亲友圈 */
    GAME("game"),
    /** 物品定位条目:四档可见性,可分享到亲友圈 */
    ITEM("item"),
    /** 智能家居设备:只支持家庭/私密两档(控制别家设备无意义) */
    IOT_DEVICE("iot_device"),
    /** 保险箱:只有 PRIVATE/FAMILY 两档,永不进亲友圈 */
    VAULT("vault");

    private final String code;

    ContentKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
