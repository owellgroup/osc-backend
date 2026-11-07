package com.osc.store.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An optional customer upload such as a BOM, RFQ or tender document. */
@Entity
@Table(name = "quote_attachments")
@Getter
@Setter
@NoArgsConstructor
public class QuoteAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(nullable = false)
    private String originalName;

    /** Object key inside the MinIO bucket. */
    @Column(nullable = false, length = 500)
    private String storagePath;

    private String contentType;
    private long sizeBytes;
}
