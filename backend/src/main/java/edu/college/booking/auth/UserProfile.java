package edu.college.booking.auth;

import java.util.List;

public record UserProfile(long id, String loginName, String displayName, String status,
        List<Grant> grants, boolean reviewer, String department) {
    public UserProfile(long id, String loginName, String displayName, String status, List<Grant> grants, boolean reviewer) {
        this(id, loginName, displayName, status, grants, reviewer, "");
    }
}
