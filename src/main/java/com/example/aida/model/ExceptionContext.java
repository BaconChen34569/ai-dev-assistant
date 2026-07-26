package com.example.aida.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExceptionContext {
    private String exceptionType;   // 全限定类名，如 java.lang.NullPointerException
    private String shortType;       // 简短名，如 NullPointerException
    private String message;         // 异常信息
    private StackFrame rootFrame;   // 顶层（最靠上的）调用栈帧
    private List<StackFrame> frames;
    private String packageHint;     // 疑似业务包名，用于 RAG 检索定向
    private String rawTrace;

    @Data
    @Builder
    public static class StackFrame {
        private String declaringClass;
        private String methodName;
        private String fileName;
        private int lineNumber;
        private String raw;
    }
}
