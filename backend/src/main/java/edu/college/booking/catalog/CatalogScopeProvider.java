package edu.college.booking.catalog;

import java.util.Set;

/** Optional integration point implemented by the team's resource-grant service. */
public interface CatalogScopeProvider {
    Set<Long> manageableLocations(String verifiedPrincipalName);
}
