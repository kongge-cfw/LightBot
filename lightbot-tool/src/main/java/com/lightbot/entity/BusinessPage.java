package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 业务办理页模板表
 *
 * @author finch
 * @since 2026-08-04
 */
@Data
@TableName(value = "business_page", autoResultMap = true)
@Schema(description = "业务办理页模板")
public class BusinessPage {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("page_type")
    @Schema(description = "页面类型码")
    private String pageType;

    @TableField("display_name")
    @Schema(description = "展示名称")
    private String displayName;

    @TableField("description")
    @Schema(description = "描述")
    private String description;

    @TableField("default_title")
    @Schema(description = "默认标题")
    private String defaultTitle;

    @TableField("page_html")
    @Schema(description = "开发者直接登记的 H5 HTML（iframe srcdoc）")
    private String pageHtml;

    @TableField("page_url")
    @Schema(description = "可选外链 H5（无 pageHtml 时使用）")
    private String pageUrl;

    @TableField(value = "allowed_modes", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "允许模式 JSON 数组")
    private String allowedModes;

    @TableField(value = "allowed_actions", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "允许操作 JSON 数组")
    private String allowedActions;

    @TableField(value = "allowed_prop_keys", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "props 白名单 JSON 数组")
    private String allowedPropKeys;

    @TableField(value = "allowed_option_keys", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "options 白名单 JSON 数组")
    private String allowedOptionKeys;

    @TableField(value = "default_props", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "默认 props JSON 对象")
    private String defaultProps;

    @TableField(value = "form_schema", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "可选兜底通用表单 schema（无 pageUrl 时使用）")
    private String formSchema;

    @TableField("builtin")
    @Schema(description = "兼容字段；平台不再使用内置种子")
    private Integer builtin;

    @TableField("enabled")
    @Schema(description = "是否启用")
    private Integer enabled;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除")
    private Integer deleted;
}
