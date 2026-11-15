package com.ihomy.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 房间邀请发送对象:成员走私聊,家庭走对方家庭聊天流;两者至少选一。
 * 目标必须是同家庭或同亲友圈(服务端逐个校验)。
 */
@Data
public class GameRoomInviteDTO {
    @Size(max = 20, message = "一次最多邀请 20 位成员")
    private List<Long> memberIds;

    @Size(max = 10, message = "一次最多邀请 10 个家庭")
    private List<Long> familyIds;
}
