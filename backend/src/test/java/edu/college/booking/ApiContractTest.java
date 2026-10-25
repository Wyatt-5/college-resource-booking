package edu.college.booking;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.college.booking.health.HealthController;
import edu.college.booking.location.Location;
import edu.college.booking.location.LocationController;
import edu.college.booking.location.LocationService;
import edu.college.booking.location.LocationStatus;
import edu.college.booking.location.LocationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {HealthController.class, LocationController.class})
class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @Test
    void healthReturnsExpectedContract() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void locationsReturnsExpectedContract() throws Exception {
        given(locationService.listLocations()).willReturn(
                List.of(new Location(1L, "办公室 A", LocationType.OFFICE, LocationStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("办公室 A"))
                .andExpect(jsonPath("$[0].type").value("OFFICE"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void unknownApiPathReturnsSharedErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.requestId").isString());
    }
}
