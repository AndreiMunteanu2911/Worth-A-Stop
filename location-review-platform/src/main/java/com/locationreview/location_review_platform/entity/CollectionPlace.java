package com.locationreview.location_review_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "collection_places")
public class CollectionPlace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Place place;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant addedAt;

    public Long getId() { return id; }

    public Collection getCollection() { return collection; }
    public void setCollection(Collection collection) { this.collection = collection; }

    public Place getPlace() { return place; }
    public void setPlace(Place place) { this.place = place; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
