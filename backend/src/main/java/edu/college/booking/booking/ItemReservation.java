package edu.college.booking.booking;

import java.time.Instant;

public record ItemReservation(
        long id, String resourceType, long resourceId, long applicantId, String itemName,
        Instant startAt, Instant endAt, String purpose, int quantity, String status,
        long submissionSeq, Instant submittedAt) {}
