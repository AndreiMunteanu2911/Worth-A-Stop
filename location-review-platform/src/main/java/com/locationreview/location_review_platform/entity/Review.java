package com.locationreview.location_review_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Place place;

    @Column(nullable = false)
    private Byte rating;

    @Column(nullable = false, length = 5000)
    private String body;

    @Column
    private LocalDate visitDate;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    public Long getId() { return id; }

    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }

    public Place getPlace() { return place; }
    public void setPlace(Place place) { this.place = place; }

    public Byte getRating() { return rating; }
    public void setRating(Byte rating) { this.rating = rating; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
