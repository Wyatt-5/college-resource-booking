package edu.college.booking.auth;

public record Account(long id, String loginName, String displayName, String passwordHash, String status) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
