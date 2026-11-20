package com.locationreview.location_review_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookmarks")
public class Bookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Place place;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Place getPlace() { return place; }
    public void setPlace(Place place) { this.place = place; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
