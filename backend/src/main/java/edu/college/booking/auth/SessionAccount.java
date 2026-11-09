package edu.college.booking.auth;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Keeps a non-secret credential fingerprint in the session even after Spring erases credentials. */
public class SessionAccount extends User {
    private static final long serialVersionUID = 1L;
    private final byte[] credentialFingerprint;
    public SessionAccount(String username, String passwordHash, boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        super(username,passwordHash,enabled,true,true,true,authorities);
        credentialFingerprint = fingerprint(passwordHash);
    }
    public boolean matchesPasswordHash(String hash) { return MessageDigest.isEqual(credentialFingerprint, fingerprint(hash)); }
    private static byte[] fingerprint(String hash) {
        try { return MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
