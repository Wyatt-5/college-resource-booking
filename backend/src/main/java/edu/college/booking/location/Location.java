package edu.college.booking.location;

public record Location(long id, String name, LocationType type, LocationStatus status, String imageUrl) {
    public Location(long id, String name, LocationType type, LocationStatus status) {
        this(id, name, type, status, null);
    }
}
