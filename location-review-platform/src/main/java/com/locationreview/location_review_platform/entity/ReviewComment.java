package com.locationreview.location_review_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "review_comments")
public class ReviewComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User author;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    public Long getId() { return id; }

    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }

    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
