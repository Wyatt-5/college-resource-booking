package edu.college.booking.catalog;

import java.util.Set;
import org.springframework.http.HttpStatus;

/** Authentication module supplies a verified server-side identity, never client-supplied roles. */
public interface CatalogAccess {
    Actor current();
    record Actor(String id, boolean allLocations, Set<Long> locations) {
        public Actor { locations = Set.copyOf(locations); }
        public boolean manages(long location) { return allLocations || locations.contains(location); }
        public void require(long location) {
            if (!manages(location)) throw new CatalogException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有该地点的管理权限");
        }
    }
}
