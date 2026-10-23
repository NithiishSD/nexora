package com.nexora.documents.application;

// A stored file ready to download: its sanitised name, the content type detected at upload, and the bytes.
public record DocumentContent(String fileName, String contentType, byte[] bytes) {
}
