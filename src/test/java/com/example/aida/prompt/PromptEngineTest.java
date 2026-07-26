package com.example.aida.prompt;

import com.example.aida.model.ExceptionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PromptEngineTest {

    @Autowired
    PromptTemplateEngine engine;
    @Autowired
    ExceptionContextExtractor extractor;

    @Test
    void renderSubstitutesNamedVars() {
        String out = engine.render("hello {{name}}, code={{code}}", Map.of("name", "曼波", "code", "X"));
        assertEquals("hello 曼波, code=X", out);
    }

    @Test
    void renderMissingVarBecomesEmpty() {
        assertEquals("a  b", engine.render("a {{missing}} b", Map.of()));
    }

    @Test
    void extractorParsesNpe() {
        String trace = """
                java.lang.NullPointerException: Cannot invoke "com.demo.User.getName()" because "u" is null
                    at com.demo.Service.handle(Service.java:42)
                    at com.demo.Controller.run(Controller.java:10)
                    at java.base/java.lang.Thread.run(Thread.java:840)""";
        ExceptionContext ctx = extractor.extract(trace);
        assertEquals("java.lang.NullPointerException", ctx.getExceptionType());
        assertEquals("NullPointerException", ctx.getShortType());
        assertEquals("com.demo.Service", ctx.getRootFrame().getDeclaringClass());
        assertEquals("handle", ctx.getRootFrame().getMethodName());
        assertEquals(42, ctx.getRootFrame().getLineNumber());
        assertEquals("com.demo", ctx.getPackageHint());
    }

    @Test
    void extractorHandlesThreadPrefix() {
        String trace = """
                Exception in thread "main" java.lang.IllegalStateException: bean not found
                    at com.demo.Boot.start(Boot.java:7)""";
        ExceptionContext ctx = extractor.extract(trace);
        assertEquals("java.lang.IllegalStateException", ctx.getExceptionType());
        assertEquals("com.demo.Boot", ctx.getRootFrame().getDeclaringClass());
        assertEquals("start", ctx.getRootFrame().getMethodName());
        assertEquals(7, ctx.getRootFrame().getLineNumber());
    }
}
