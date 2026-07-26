package com.example.aida.rag;

/**
 * 代码分片（方法级）数据载体。
 * id 由「文件路径 + 方法名 + 起始行」确定，保证重复索引时幂等。
 */
public class CodeChunk {

    private final String id;
    private final String filePath;
    private final String className;
    private final String methodName;
    private final int startLine;
    private final int endLine;
    private final String language;
    private final String code;

    public CodeChunk(String filePath, String className, String methodName,
                     int startLine, int endLine, String language, String code) {
        this.filePath = filePath;
        this.className = className;
        this.methodName = methodName;
        this.startLine = startLine;
        this.endLine = endLine;
        this.language = language;
        this.code = code;
        this.id = filePath + "#" + (methodName == null ? "anonymous" : methodName) + ":" + startLine;
    }

    public String getId() { return id; }
    public String getFilePath() { return filePath; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }

    /** 用于向量化检索的文本：路径 + 签名 + 方法体，让召回同时命中美誉度与实现细节。 */
    public String getContent() {
        return filePath + " " + (className == null ? "" : className + "#") + methodName + "\n" + code;
    }
}
