package com.adac.portail.service;

public record DownloadedDocument(byte[] content, String fileName, String mimeType) {
}
