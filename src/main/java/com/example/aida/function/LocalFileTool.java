package com.example.aida.function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * 本地文件工具：受沙箱约束的读/写/列目录。
 *
 * <p>安全边界：所有路径先 {@code sandbox.resolve(...).normalize()}，
 * 再校验结果仍以 {@code sandbox} 为前缀，越权访问直接拒绝。
 * 这是「把本地文件读写封装成 Agent 可调工具」时的必备护栏，
 * 否则 LLM 生成的任意路径可能读到系统敏感文件。
 */
@Component
public class LocalFileTool {

    private final Path sandbox;

    public LocalFileTool(@Value("${aida.tools.sandbox-base:}") String sandboxBase) {
        String base = (sandboxBase == null || sandboxBase.isBlank())
                ? System.getProperty("user.dir")
                : sandboxBase;
        this.sandbox = Paths.get(base).toAbsolutePath().normalize();
    }

    @AiTool(
            name = "readFile",
            description = "读取本地文件内容（受沙箱限制，仅能访问允许目录下的文件）",
            paramNames = "path",
            paramDescriptions = "文件相对沙箱根目录的路径")
    public String readFile(String path) throws IOException {
        Path target = resolve(path);
        return Files.readString(target);
    }

    @AiTool(
            name = "writeFile",
            description = "向本地文件写入内容（受沙箱限制）",
            paramNames = {"path", "content"},
            paramDescriptions = {"目标文件相对路径", "要写入的内容"})
    public String writeFile(String path, String content) throws IOException {
        Path target = resolve(path);
        Files.writeString(target, content == null ? "" : content);
        return "written: " + target;
    }

    @AiTool(
            name = "listDirectory",
            description = "列出沙箱目录下的一级文件/子目录",
            paramNames = "path",
            paramDescriptions = "目录相对沙箱根目录的路径")
    public String listDirectory(String path) throws IOException {
        Path target = resolve(path);
        if (!Files.isDirectory(target)) {
            return "不是目录: " + target;
        }
        return Files.list(target)
                .map(p -> (Files.isDirectory(p) ? "[D] " : "[F] ") + p.getFileName())
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    /** 解析并做沙箱越权校验。 */
    private Path resolve(String p) {
        if (p == null || p.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path target = sandbox.resolve(p).normalize();
        if (!target.startsWith(sandbox)) {
            throw new SecurityException("越权访问被拒绝: " + p);
        }
        return target;
    }

    public Path getSandbox() {
        return sandbox;
    }
}
