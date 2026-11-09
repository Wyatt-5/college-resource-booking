package edu.college.booking.location;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public LocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Location> findAll() {
        return jdbcTemplate.query(
                "SELECT l.id, l.name, l.type, l.status, (i.location_id IS NOT NULL) AS has_image FROM locations l LEFT JOIN location_images i ON i.location_id=l.id WHERE l.status='ACTIVE' ORDER BY l.id",
                (resultSet, rowNum) -> new Location(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        LocationType.valueOf(resultSet.getString("type")),
                        LocationStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("has_image") ? "/api/v1/locations/" + resultSet.getLong("id") + "/image" : null));
    }
}
