package com.adac.portail.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {

    private Long id;

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String mimeType;
    private UserResponse uploadedBy;
    private Long formationId;
    private Long inscriptionId;
    private OffsetDateTime createdAt;
}
