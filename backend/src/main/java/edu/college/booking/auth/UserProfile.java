package edu.college.booking.auth;

import java.util.List;

public record UserProfile(long id, String loginName, String displayName, String status,
        List<Grant> grants, boolean reviewer) {
}
