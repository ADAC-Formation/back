package com.adac.portail.service;

import com.adac.portail.config.SupabaseConfig;
import com.adac.portail.exception.StorageException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** See {@link StorageService} for scope. */
@Service
public class StorageServiceImpl implements StorageService {

    private final SupabaseConfig supabaseConfig;
    private final RestTemplate restTemplate;

    public StorageServiceImpl(SupabaseConfig supabaseConfig, RestTemplateBuilder restTemplateBuilder) {
        this.supabaseConfig = supabaseConfig;
        // Branch-wide review (backend + security): the default RestTemplateBuilder has no
        // timeout, so a hung Supabase connection would pin a Tomcat thread indefinitely.
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String upload(MultipartFile file, String path, String contentType) {
        // exchange(URI, ...) rather than exchange(String, ...): buildObjectUrl already
        // percent-encodes the path (SupabaseConfig.encodePath) — the String overload runs it
        // through UriComponentsBuilder's own template expansion again, double-encoding any "%xx"
        // (branch-wide review: this made the persisted fileUrl point at a different object than
        // the one actually uploaded for any filename with a space or accent).
        URI uri = URI.create(supabaseConfig.buildObjectUrl(path));
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        try {
            HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);
            restTemplate.exchange(uri, HttpMethod.POST, entity, Void.class);
        } catch (IOException e) {
            throw new StorageException("Échec de lecture du fichier à uploader", e);
        } catch (RestClientException e) {
            throw new StorageException("Échec de l'upload vers Supabase Storage", e);
        }
        return uri.toString();
    }

    @Override
    public byte[] download(String path) {
        URI uri = URI.create(supabaseConfig.buildObjectUrl(path));
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
        } catch (RestClientException e) {
            throw new StorageException("Échec du téléchargement depuis Supabase Storage", e);
        }
        byte[] body = response.getBody();
        if (body == null) {
            throw new StorageException("Réponse vide de Supabase Storage", null);
        }
        return body;
    }

    @Override
    public void delete(String path) {
        URI uri = URI.create(supabaseConfig.buildObjectUrl(path));
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        try {
            restTemplate.exchange(uri, HttpMethod.DELETE, entity, Void.class);
        } catch (RestClientException e) {
            if (isNotFound(e)) {
                return; // Already gone — deleting is idempotent from the caller's perspective.
            }
            throw new StorageException("Échec de la suppression sur Supabase Storage", e);
        }
    }

    private boolean isNotFound(RestClientException e) {
        return e instanceof org.springframework.web.client.HttpStatusCodeException httpEx
                && httpEx.getStatusCode().equals(HttpStatusCode.valueOf(404));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseConfig.getKey());
        headers.setBearerAuth(supabaseConfig.getKey());
        return headers;
    }
}
