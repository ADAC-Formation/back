package com.adac.portail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
public class SupabaseConfig {

    private final String url;
    private final String key;
    private final String bucket;

    public SupabaseConfig(@Value("${supabase.url}") String url,
                           @Value("${supabase.key}") String key,
                           @Value("${supabase.bucket}") String bucket) {
        this.url = url;
        this.key = key;
        this.bucket = bucket;
    }

    public String getUrl() {
        return url;
    }

    public String getKey() {
        return key;
    }

    public String getBucket() {
        return bucket;
    }

    public String buildObjectUrl(String path) {
        return "%s/storage/v1/object/%s/%s".formatted(url, bucket, encodePath(path));
    }

    public String buildPublicUrl(String path) {
        return "%s/storage/v1/object/public/%s/%s".formatted(url, bucket, encodePath(path));
    }

    private String encodePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("path must not start with '/': " + path);
        }

        String[] segments = path.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("invalid path segment in: " + path);
            }
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }
}
