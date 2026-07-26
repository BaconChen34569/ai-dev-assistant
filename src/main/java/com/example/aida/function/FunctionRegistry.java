package com.example.aida.function;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册器：启动时扫描 Spring 容器，把所有带 {@link AiTool} 注解的
 * Bean 方法，通过反射收集为 {@link ToolDefinition}，并用 {@link ToolInvokerFactory}
 * 创建动态代理调用入口。
 *
 * <p>这就是简历里说的「Java 反射 + 动态代理，把本地工具封装成 Spring Bean
 * 接入 Function Calling」的落点：业务方只写 Bean + {@code @AiTool}，
 * 无需任何注册样板代码，新工具即被自动纳入 Agent 可调用集合。
 */
@Component
public class FunctionRegistry {

    private final List<ToolDefinition> definitions = new ArrayList<>();
    private final ToolInvoker invoker;

    public FunctionRegistry(ApplicationContext ctx) {
        Map<String, ToolDefinition> map = new LinkedHashMap<>();

        for (String beanName : ctx.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = ctx.getBean(beanName);
            } catch (Exception e) {
                // 某些懒加载/有副作用的 Bean 在此跳过，不影响工具扫描
                continue;
            }
            if (bean instanceof FunctionRegistry) {
                continue; // 避免自引用
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method m : targetClass.getMethods()) {
                AiTool ann = m.getAnnotation(AiTool.class);
                if (ann == null) {
                    continue;
                }
                String toolName = ann.name().isEmpty() ? m.getName() : ann.name();
                if (map.containsKey(toolName)) {
                    continue; // 同名工具只取首个，避免重复注册
                }
                List<ParamMeta> params = new ArrayList<>();
                java.lang.reflect.Parameter[] ps = m.getParameters();
                String[] pNames = ann.paramNames();
                String[] pDescs = ann.paramDescriptions();
                for (int i = 0; i < ps.length; i++) {
                    String pn = (i < pNames.length && !pNames[i].isEmpty()) ? pNames[i] : ("arg" + i);
                    String pd = (i < pDescs.length) ? pDescs[i] : "";
                    params.add(new ParamMeta(pn, ps[i].getType(), pd));
                }
                map.put(toolName, new ToolDefinition(toolName, ann.description(), params, bean, m));
            }
        }

        this.definitions.addAll(map.values());
        this.invoker = ToolInvokerFactory.create(map);
    }

    public List<ToolDefinition> getDefinitions() {
        return definitions;
    }

    public ToolInvoker getInvoker() {
        return invoker;
    }
}
