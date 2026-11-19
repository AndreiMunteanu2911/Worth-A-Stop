package com.locationreview.location_review_platform.places;

public class PlaceProviderException extends RuntimeException {
    private final int status;
    public PlaceProviderException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
