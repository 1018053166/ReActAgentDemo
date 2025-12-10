package com.example.reactmcp.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统操作工具集
 * 提供安全的本地文件读写、目录管理等能力
 */
@Component
public class FileSystemTools {
    
    private static final Logger log = LoggerFactory.getLogger(FileSystemTools.class);
    private static final String DEFAULT_BASE_PATH = System.getProperty("user.home");
    
    /**
     * 安全路径检查，防止目录遍历攻击
     */
    private Path sanitizePath(String pathStr) throws IOException {
        Path basePath = Paths.get(DEFAULT_BASE_PATH).toRealPath();
        Path requestedPath = basePath.resolve(pathStr).normalize();
        
        if (!requestedPath.startsWith(basePath)) {
            throw new SecurityException("Access denied: Path is outside allowed directory");
        }
        
        return requestedPath;
    }

    @Tool("读取指定文件的完整内容，返回文本格式。参数: filePath - 文件路径(相对于用户目录)")
    public String readFile(String filePath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: read_file                                │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • filePath: {}", filePath);
        
        try {
            Path path = sanitizePath(filePath);
            
            if (!Files.exists(path)) {
                String error = "File not found: " + filePath;
                log.info("│ 📤 返回结果: 文件不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isRegularFile(path)) {
                String error = "Path is not a file: " + filePath;
                log.info("│ 📤 返回结果: 路径不是文件");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            String content = Files.readString(path);
            log.info("│ 📤 返回结果: 成功读取文件，大小 {} 字节", content.length());
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return content;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }

    @Tool("写入内容到指定文件，如果文件不存在则创建，存在则覆盖。参数: filePath - 文件路径, content - 文件内容")
    public String writeFile(String filePath, String content) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: write_file                               │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • filePath: {}", filePath);
        log.info("│    • content length: {} 字节", content.length());
        
        try {
            Path path = sanitizePath(filePath);
            
            // 确保父目录存在
            Files.createDirectories(path.getParent());
            
            // 写入文件
            Files.writeString(path, content);
            
            String result = "File written successfully: " + filePath;
            log.info("│ 📤 返回结果: 文件写入成功");
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }

    @Tool("列出指定目录下的所有文件和子目录。参数: directoryPath - 目录路径, recursive - 是否递归列出(默认false)")
    public String listFiles(String directoryPath, boolean recursive) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: list_files                               │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • directoryPath: {}", directoryPath);
        log.info("│    • recursive: {}", recursive);
        
        try {
            Path path = sanitizePath(directoryPath);
            
            if (!Files.exists(path)) {
                String error = "Directory not found: " + directoryPath;
                log.info("│ 📤 返回结果: 目录不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isDirectory(path)) {
                String error = "Path is not a directory: " + directoryPath;
                log.info("│ 📤 返回结果: 路径不是目录");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            final List<String> entries = new ArrayList<>();
            
            if (recursive) {
                // 递归遍历
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String relativePath = path.relativize(file).toString();
                        entries.add("[FILE] " + relativePath + " (" + attrs.size() + " bytes)");
                        return FileVisitResult.CONTINUE;
                    }
                    
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(path)) {
                            String relativePath = path.relativize(dir).toString();
                            entries.add("[DIR]  " + relativePath + "/");
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                // 只列出直接子项
                try (Stream<Path> stream = Files.list(path)) {
                    List<String> items = stream.map(p -> {
                        try {
                            String name = p.getFileName().toString();
                            if (Files.isDirectory(p)) {
                                return "[DIR]  " + name + "/";
                            } else {
                                long size = Files.size(p);
                                return "[FILE] " + name + " (" + size + " bytes)";
                            }
                        } catch (IOException e) {
                            return "[?]    " + p.getFileName().toString();
                        }
                    }).collect(Collectors.toList());
                    entries.addAll(items);
                }
            }
            
            String result = String.format("Directory: %s\n%s\nTotal: %d entries", 
                    directoryPath, 
                    String.join("\n", entries),
                    entries.size());
            
            log.info("│ 📤 返回结果: 找到 {} 个条目", entries.size());
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }

    @Tool("创建新目录，支持创建父目录。参数: directoryPath - 目录路径")
    public String createDirectory(String directoryPath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: create_directory                         │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • directoryPath: {}", directoryPath);
        
        try {
            Path path = sanitizePath(directoryPath);
            
            if (Files.exists(path)) {
                String error = "Directory already exists: " + directoryPath;
                log.info("│ 📤 返回结果: 目录已存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            Files.createDirectories(path);
            
            String result = "Directory created successfully: " + directoryPath;
            log.info("│ 📤 返回结果: 目录创建成功");
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }

    @Tool("删除指定文件。参数: filePath - 文件路径")
    public String deleteFile(String filePath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: delete_file                              │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • filePath: {}", filePath);
        
        try {
            Path path = sanitizePath(filePath);
            
            if (!Files.exists(path)) {
                String error = "File not found: " + filePath;
                log.info("│ 📤 返回结果: 文件不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isRegularFile(path)) {
                String error = "Path is not a file: " + filePath;
                log.info("│ 📤 返回结果: 路径不是文件");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            Files.delete(path);
            
            String result = "File deleted successfully: " + filePath;
            log.info("│ 📤 返回结果: 文件删除成功");
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }

    @Tool("复制文件从源路径到目标路径。参数: sourcePath - 源文件路径, targetPath - 目标文件路径")
    public String copyFile(String sourcePath, String targetPath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: copy_file                                │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • sourcePath: {}", sourcePath);
        log.info("│    • targetPath: {}", targetPath);
        
        try {
            Path source = sanitizePath(sourcePath);
            Path target = sanitizePath(targetPath);
            
            if (!Files.exists(source)) {
                String error = "Source file not found: " + sourcePath;
                log.info("│ 📤 返回结果: 源文件不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isRegularFile(source)) {
                String error = "Source is not a file: " + sourcePath;
                log.info("│ 📤 返回结果: 源路径不是文件");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            // 确保目标目录存在
            Files.createDirectories(target.getParent());
            
            // 复制文件
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            
            String result = String.format("File copied successfully from %s to %s", sourcePath, targetPath);
            log.info("│ 📤 返回结果: 文件复制成功");
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }

    @Tool("移动或重命名文件。参数: sourcePath - 源文件路径, targetPath - 目标文件路径")
    public String moveFile(String sourcePath, String targetPath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: move_file                                │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • sourcePath: {}", sourcePath);
        log.info("│    • targetPath: {}", targetPath);
        
        try {
            Path source = sanitizePath(sourcePath);
            Path target = sanitizePath(targetPath);
            
            if (!Files.exists(source)) {
                String error = "Source file not found: " + sourcePath;
                log.info("│ 📤 返回结果: 源文件不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isRegularFile(source)) {
                String error = "Source is not a file: " + sourcePath;
                log.info("│ 📤 返回结果: 源路径不是文件");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            // 确保目标目录存在
            Files.createDirectories(target.getParent());
            
            // 移动文件
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            
            String result = String.format("File moved successfully from %s to %s", sourcePath, targetPath);
            log.info("│ 📤 返回结果: 文件移动成功");
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error: " + e.getMessage();
        }
    }
}
