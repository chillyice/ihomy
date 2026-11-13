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
