package com.lightbot.service.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.CodeExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Groovy 脚本引擎（JSR-223）
 * <p>安全级别 L2：关键字黑名单 + 超时控制。入口约定 {@code main(params)}。</p>
 *
 * @author finch
 * @since 2026-07-01
 */
@Slf4j
@Component
public class GroovyEngine implements CodeEngine {

    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_OUTPUT_LENGTH = 10000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 危险访问模式 */
    private static final Pattern DANGEROUS_ACCESS = Pattern.compile(
            "\\b(Runtime\\.getRuntime|ProcessBuilder|ProcessHandle|Desktop"
                    + "|java\\.lang\\.Runtime|java\\.lang\\.ProcessBuilder"
                    + "|java\\.io\\.|java\\.net\\.|java\\.nio\\.file\\."
                    + "|javax\\.script\\.ScriptEngineManager|Class\\.forName"
                    + "|System\\.exit|System\\.setOut|System\\.setErr"
                    + "|groovy\\.util\\.Eval|GroovyShell|GroovyClassLoader"
                    + "|Reflective|Method\\.invoke|Field\\.set)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String language() {
        return "groovy";
    }

    @Override
    public boolean isAvailable() {
        return new ScriptEngineManager().getEngineByName("groovy") != null;
    }

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        long timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long start = System.currentTimeMillis();

        String securityError = checkSecurity(code);
        if (securityError != null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error(securityError)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("groovy")
                    .build();
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
        if (engine == null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error("Groovy 执行环境不可用：未加载 groovy-jsr223 依赖")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("groovy")
                    .build();
        }

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outputBuf = new ByteArrayOutputStream();
        PrintStream capturedOut = new PrintStream(outputBuf);

        try {
            return CompletableFuture.supplyAsync(() -> {
                System.setOut(capturedOut);
                try {
                    Bindings bindings = engine.createBindings();
                    bindings.put("params", params != null ? params : Map.of());
                    Object evalResult = engine.eval(code, bindings);

                    Object result = null;
                    if (engine instanceof Invocable invocable) {
                        try {
                            result = invocable.invokeFunction("main", params != null ? params : Map.of());
                        } catch (NoSuchMethodException ignored) {
                            // 无 main 函数时回退 eval 结果或 result 变量
                        }
                    }
                    if (result == null) {
                        result = bindings.get("result");
                    }
                    if (result == null) {
                        result = evalResult;
                    }

                    String output = truncateOutput(outputBuf.toString(StandardCharsets.UTF_8));
                    String returnValue = serializeReturnValue(result);

                    return CodeExecResult.builder()
                            .success(true)
                            .output(output)
                            .returnValue(returnValue)
                            .elapsedMs(System.currentTimeMillis() - start)
                            .language("groovy")
                            .build();
                } catch (Exception e) {
                    String output = truncateOutput(outputBuf.toString(StandardCharsets.UTF_8));
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    return CodeExecResult.builder()
                            .success(false)
                            .output(output)
                            .error("执行错误: " + sanitizeError(cause.getMessage()))
                            .elapsedMs(System.currentTimeMillis() - start)
                            .language("groovy")
                            .build();
                } finally {
                    System.setOut(originalOut);
                }
            }).orTimeout(timeout, TimeUnit.MILLISECONDS).join();
        } catch (java.util.concurrent.CompletionException e) {
            System.setOut(originalOut);
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return CodeExecResult.builder()
                        .success(false)
                        .error("Groovy 执行超时（" + timeout + "ms），请检查是否存在死循环")
                        .elapsedMs(timeout)
                        .language("groovy")
                        .build();
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(cause.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("groovy")
                    .build();
        } catch (Exception e) {
            System.setOut(originalOut);
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(e.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("groovy")
                    .build();
        }
    }

    private String checkSecurity(String code) {
        if (DANGEROUS_ACCESS.matcher(code).find()) {
            return "Groovy 安全校验未通过：包含不允许的系统访问（禁止文件、网络、进程、反射等）";
        }
        return null;
    }

    private String serializeReturnValue(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map || result instanceof Collection) {
            try {
                return truncateOutput(OBJECT_MAPPER.writeValueAsString(result));
            } catch (Exception ignored) {
            }
        }
        return truncateOutput(String.valueOf(result));
    }

    private String truncateOutput(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_OUTPUT_LENGTH
                ? text.substring(0, MAX_OUTPUT_LENGTH) + "... (截断)"
                : text;
    }

    private String sanitizeError(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.replaceAll("[A-Z]:\\\\[\\S]+", "<path>")
                .replaceAll("/[a-z]+/[a-z]+/[\\S]+", "<path>");
    }
}
