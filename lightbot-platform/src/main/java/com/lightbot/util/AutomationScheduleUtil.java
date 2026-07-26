package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.enums.AutomationScheduleType;
import com.lightbot.enums.ErrorCode;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自动化调度：校验、配置序列化、下次触发计算（分钟级即可）。
 * <p>内部统一转为 Spring 6 段 Cron（秒 分 时 日 月 周），周字段用 Quartz 语义（1=周日…7=周六）。
 *
 * @author finch
 * @since 2026-07-26
 */
public final class AutomationScheduleUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter ONCE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter ONCE_FMT_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AutomationScheduleUtil() {
    }

    public static AutomationScheduleType parseType(String raw) {
        try {
            return AutomationScheduleType.fromValue(raw);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "未知触发方式");
        }
    }

    /**
     * 校验并生成 schedule_config JSON
     */
    public static String buildConfigJson(AutomationScheduleType type, AutomationJobSaveDTO dto) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        switch (type) {
            case ONCE -> {
                LocalDateTime once = parseOnceAt(dto.getOnceAt());
                if (!once.isAfter(LocalDateTime.now(ZONE))) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "执行时刻不能早于当前时间");
                }
                cfg.put("onceAt", once.format(ONCE_FMT));
            }
            case DAILY -> {
                cfg.put("time", requireTime(dto.getTime()));
            }
            case WEEKLY -> {
                List<Integer> days = normalizeWeekdays(dto.getWeekdays());
                if (days.isEmpty()) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "请选择星期");
                }
                cfg.put("weekdays", days);
                cfg.put("time", requireTime(dto.getTime()));
            }
            case MONTHLY -> {
                int day = dto.getMonthDay() == null ? 0 : dto.getMonthDay();
                if (day < 1 || day > 31) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "每月日期须在 1-31");
                }
                cfg.put("monthDay", day);
                cfg.put("time", requireTime(dto.getTime()));
            }
            case CRON -> {
                String cron5 = dto.getCron() == null ? "" : dto.getCron().trim();
                if (!StringUtils.hasText(cron5)) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "Cron 不能为空");
                }
                String springCron = toSpringCron(cron5);
                try {
                    CronExpression.parse(springCron);
                } catch (Exception e) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "Cron 无效: " + e.getMessage());
                }
                cfg.put("cron", cron5);
            }
            default -> throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "未知触发方式");
        }
        try {
            return MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "配置序列化失败");
        }
    }

    public static Map<String, Object> parseConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 计算下一次触发时间；once 已过期返回 null；禁用场景由调用方处理。
     *
     * @param fromExclusive 从此刻之后找下一次（不含等于）
     */
    public static LocalDateTime computeNextRun(AutomationScheduleType type, String configJson, LocalDateTime fromExclusive) {
        Map<String, Object> cfg = parseConfig(configJson);
        LocalDateTime base = fromExclusive != null ? fromExclusive : LocalDateTime.now(ZONE);
        return switch (type) {
            case ONCE -> {
                LocalDateTime once = parseOnceAt(str(cfg.get("onceAt")));
                yield once.isAfter(base) ? once : null;
            }
            case DAILY, WEEKLY, MONTHLY, CRON -> nextByCron(toSpringCronExpression(type, cfg), base);
        };
    }

    private static LocalDateTime nextByCron(String springCron, LocalDateTime base) {
        CronExpression expr = CronExpression.parse(springCron);
        var next = expr.next(base.atZone(ZONE));
        return next != null ? next.toLocalDateTime() : null;
    }

    public static String toSpringCronExpression(AutomationScheduleType type, Map<String, Object> cfg) {
        return switch (type) {
            case DAILY -> {
                int[] hm = parseHm(str(cfg.get("time")));
                yield String.format("0 %d %d * * *", hm[1], hm[0]);
            }
            case WEEKLY -> {
                int[] hm = parseHm(str(cfg.get("time")));
                @SuppressWarnings("unchecked")
                List<Integer> isoDays = normalizeWeekdays((List<Integer>) cfg.get("weekdays"));
                String dow = isoDays.stream().map(AutomationScheduleUtil::isoToQuartzDow)
                        .map(String::valueOf).collect(Collectors.joining(","));
                yield String.format("0 %d %d * * %s", hm[1], hm[0], dow);
            }
            case MONTHLY -> {
                int[] hm = parseHm(str(cfg.get("time")));
                int day = ((Number) cfg.get("monthDay")).intValue();
                yield String.format("0 %d %d %d * *", hm[1], hm[0], day);
            }
            case CRON -> toSpringCron(str(cfg.get("cron")));
            case ONCE -> throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "一次性任务无需 Cron");
        };
    }

    /** 5 段 unix → 6 段 Spring；已是 6 段则原样 */
    public static String toSpringCron(String cron) {
        String c = cron.trim().replaceAll("\\s+", " ");
        String[] parts = c.split(" ");
        if (parts.length == 5) {
            return "0 " + c;
        }
        if (parts.length == 6) {
            return c;
        }
        throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "Cron 须为 5 段或 6 段");
    }

    /** ISO 1=周一…7=周日 → Quartz 1=周日…7=周六 */
    public static int isoToQuartzDow(int iso) {
        if (iso < 1 || iso > 7) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "星期取值须在 1-7");
        }
        return iso == 7 ? 1 : iso + 1;
    }

    public static LocalDateTime parseOnceAt(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "请选择执行时刻");
        }
        String v = raw.trim();
        try {
            if (v.length() <= 16) {
                return LocalDateTime.parse(v, ONCE_FMT);
            }
            return LocalDateTime.parse(v, ONCE_FMT_SEC);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "执行时刻格式应为 yyyy-MM-dd HH:mm");
        }
    }

    private static String requireTime(String time) {
        parseHm(time);
        return time.trim();
    }

    /** @return [hour, minute] */
    private static int[] parseHm(String time) {
        if (!StringUtils.hasText(time) || !time.trim().matches("\\d{1,2}:\\d{2}")) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "请选择执行时间 HH:mm");
        }
        String[] p = time.trim().split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "执行时间不合法");
        }
        return new int[]{h, m};
    }

    private static List<Integer> normalizeWeekdays(List<Integer> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Integer d : weekdays) {
            if (d == null) {
                continue;
            }
            if (d < 1 || d > 7) {
                throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "星期取值须在 1-7");
            }
            if (!out.contains(d)) {
                out.add(d);
            }
        }
        out.sort(Integer::compareTo);
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
