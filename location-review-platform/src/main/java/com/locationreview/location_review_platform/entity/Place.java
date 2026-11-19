package com.locationreview.location_review_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "places")
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OsmType osmType;

    @Column(nullable = false)
    private Long osmId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String osmTagValue;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 500)
    private String address;

    @Column(length = 2048)
    private String website;

    @Column(length = 100)
    private String phone;

    @Column(length = 500)
    private String openingHours;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant metadataFetchedAt;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    public Long getId() { return id; }

    public OsmType getOsmType() { return osmType; }
    public void setOsmType(OsmType osmType) { this.osmType = osmType; }

    public Long getOsmId() { return osmId; }
    public void setOsmId(Long osmId) { this.osmId = osmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getOsmTagValue() { return osmTagValue; }
    public void setOsmTagValue(String osmTagValue) { this.osmTagValue = osmTagValue; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getMetadataFetchedAt() { return metadataFetchedAt; }
    public void setMetadataFetchedAt(Instant metadataFetchedAt) { this.metadataFetchedAt = metadataFetchedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
