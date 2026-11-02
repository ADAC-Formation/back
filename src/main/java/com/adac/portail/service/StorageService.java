package com.adac.portail.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String upload(MultipartFile file, String path, String contentType);

    byte[] download(String path);

    void delete(String path);
}
