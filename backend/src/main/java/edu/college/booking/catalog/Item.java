package edu.college.booking.catalog;

public record Item(long id, long locationId, String name, String description,
        boolean isValuable, String status, Rules rules, UnitSummary unitSummary,
        java.time.Instant createdAt, java.util.List<ItemImage> images) {
    public record Rules(Integer advanceDays, Integer maxBorrowDays) {}
    // Current physical state only; not availability for a reservation time interval.
    public record UnitSummary(long total, long available) {}
}
