package com.lightbot.constant;

/**
 * 用户可维护文本的统一长度上限。
 *
 * @author finch
 * @since 2026-07-26
 */
public final class TextLengthConstants {

    public static final int NAME = 50;
    public static final int DESCRIPTION = 200;
    public static final int IDENTIFIER = 100;
    public static final int VERSION = 32;
    public static final int URL = 2_048;
    public static final int JSON = 8_000;
    public static final int SCHEMA_JSON = 16_000;
    public static final int PROMPT_TEMPLATE = 5_000;
    public static final int DOCUMENT_CONTENT = 5 * 1024 * 1024;

    private TextLengthConstants() {
    }
}
