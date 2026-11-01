package edu.college.booking.catalog;

import java.util.Set;

/** Reads current grants for a server-verified login name. */
public interface CatalogScopeProvider {
    boolean managesAllLocations(String verifiedPrincipalName);
    Set<Long> manageableLocations(String verifiedPrincipalName);
}
