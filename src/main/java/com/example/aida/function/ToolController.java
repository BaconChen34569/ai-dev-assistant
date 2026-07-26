package com.example.aida.function;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具调试接口（无需 LLM 即可验证 Function Calling 链路）：
 * <ul>
 *   <li>{@code GET /api/tool/list} — 列出当前所有可调用工具及其参数；</li>
 *   <li>{@code POST /api/tool/call} — 直接按名调用某个工具，验证反射分发是否正确。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tool")
public class ToolController {

    private final FunctionRegistry registry;

    public ToolController(FunctionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/list")
    public List<ToolInfo> list() {
        return registry.getDefinitions().stream()
                .map(ToolInfo::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/call")
    public Map<String, Object> call(@RequestBody ToolCallRequest req) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            Object r = registry.getInvoker().call(req.toolName(), req.args());
            resp.put("ok", true);
            resp.put("result", r == null ? "" : r.toString());
        } catch (Exception e) {
            resp.put("ok", false);
            resp.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return resp;
    }
}
