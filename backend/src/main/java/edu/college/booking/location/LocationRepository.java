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
                "SELECT id, name, type, status FROM locations ORDER BY id",
                (resultSet, rowNum) -> new Location(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        LocationType.valueOf(resultSet.getString("type")),
                        LocationStatus.valueOf(resultSet.getString("status"))));
    }
}
