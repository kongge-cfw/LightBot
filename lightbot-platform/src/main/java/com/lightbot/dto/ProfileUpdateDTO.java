package com.lightbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateDTO {

    @Size(min = 1, max = 8, message = "昵称长度1-8")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不超过254字")
    private String email;

    @Size(max = 32, message = "手机号不超过32字")
    private String phone;

    @Size(max = 512, message = "头像地址不超过512字")
    private String avatarFrame;

    @Min(value = 0, message = "等级最小为0")
    @Max(value = 6, message = "等级最大为6")
    private Integer level;
}
