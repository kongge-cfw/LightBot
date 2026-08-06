package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员更新用户请求
 *
 * @author finch
 * @since 2026-06-18
 */
@Data
public class AdminUserUpdateDTO {

    @NotNull(message = "用户ID不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Size(min = 1, max = 8, message = "昵称长度1-8")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不超过254字")
    private String email;

    @Size(max = 32, message = "手机号不超过32字")
    private String phone;

    private UserRole role;

    private UserStatus status;
}
