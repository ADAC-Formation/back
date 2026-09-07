package com.adac.portail.service;

import com.adac.portail.config.SupabaseConfig;
import com.adac.portail.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TICKET-026 — HTTP plumbing to Supabase Storage. Mocks {@link RestTemplate}: this ticket isn't
 * about exercising real Supabase (no such integration harness exists in this repo), just proving
 * {@link StorageServiceImpl} builds the right request against the URLs {@link SupabaseConfig}
 * already produces (see its own {@code StorageServiceImpl (TICKET-026)} Javadoc reference).
 *
 * <p>Branch-wide review: {@code exchange(URI, ...)} — not {@code exchange(String, ...)}, which
 * would double-encode {@code buildObjectUrl}'s already-percent-encoded path — so every assertion
 * below matches against a {@link URI}, not a {@link String}.</p>
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    private static final URI OBJECT_URI =
            URI.create("https://xxxx.supabase.co/storage/v1/object/adac-documents/formations/1/programme.pdf");

    private final SupabaseConfig supabaseConfig =
            new SupabaseConfig("https://xxxx.supabase.co", "service-key", "adac-documents");

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.connectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.readTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        storageService = new StorageServiceImpl(supabaseConfig, restTemplateBuilder);
    }

    @Test
    void configuresConnectAndReadTimeouts() {
        verify(restTemplateBuilder).connectTimeout(Duration.ofSeconds(5));
        verify(restTemplateBuilder).readTimeout(Duration.ofSeconds(30));
    }

    @Test
    void uploadSendsFileBytesToTheAuthenticatedObjectUriAndReturnsIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "programme.pdf", "application/pdf", "contenu".getBytes());
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        String url = storageService.upload(file, "formations/1/programme.pdf", "application/pdf");

        assertThat(url).isEqualTo(OBJECT_URI.toString());
    }

    @Test
    void uploadUsesTheServerSuppliedContentTypeNotTheClientsHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "programme.pdf", "text/html", "contenu".getBytes()); // client lies about the type
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        storageService.upload(file, "formations/1/programme.pdf", "application/pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<byte[]>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(OBJECT_URI), eq(HttpMethod.POST), captor.capture(), eq(Void.class));
        assertThat(captor.getValue().getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    }

    @Test
    void uploadSendsServiceKeyAsApikeyAndBearerToken() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "programme.pdf", "application/pdf", "contenu".getBytes());
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        storageService.upload(file, "formations/1/programme.pdf", "application/pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<byte[]>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(OBJECT_URI), eq(HttpMethod.POST), captor.capture(), eq(Void.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertThat(headers.getFirst("apikey")).isEqualTo("service-key");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer service-key");
        assertThat(captor.getValue().getBody()).isEqualTo("contenu".getBytes());
    }

    @Test
    void uploadWrapsAnHttpFailureAsStorageException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "programme.pdf", "application/pdf", "contenu".getBytes());
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> storageService.upload(file, "formations/1/programme.pdf", "application/pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void downloadReturnsBodyBytesFromTheAuthenticatedObjectUri() {
        byte[] bytes = "contenu-pdf".getBytes();
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(bytes));

        byte[] result = storageService.download("formations/1/programme.pdf");

        assertThat(result).isEqualTo(bytes);
    }

    @Test
    void downloadWithNullBodyThrowsStorageException() {
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok().build());

        assertThatThrownBy(() -> storageService.download("formations/1/programme.pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void deleteSendsDeleteToTheAuthenticatedObjectUri() {
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.noContent().build());

        assertThatCode(() -> storageService.delete("formations/1/programme.pdf")).doesNotThrowAnyException();
    }

    @Test
    void deleteOfAnAlreadyGoneObjectIsNotAnError() {
        when(restTemplate.exchange(eq(OBJECT_URI), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatCode(() -> storageService.delete("formations/1/programme.pdf")).doesNotThrowAnyException();
    }
}
