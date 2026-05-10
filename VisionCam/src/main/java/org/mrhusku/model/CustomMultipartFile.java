package org.mrhusku.model;

import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

public class CustomMultipartFile implements MultipartFile {
    private final byte[] content;
    private final String name;
    private final String filename;

    public CustomMultipartFile(byte[] content, String name, String filename) {
        this.content = content;
        this.name = name;
        this.filename = filename;
    }

    @Override public String getName() { return name; }
    @Override public String getOriginalFilename() { return filename; }
    @Override public String getContentType() { return "image/png"; }
    @Override public boolean isEmpty() { return content == null || content.length == 0; }
    @Override public long getSize() { return content.length; }
    @Override public byte[] getBytes() { return content; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
    @Override public void transferTo(File dest) throws IllegalStateException { }
}