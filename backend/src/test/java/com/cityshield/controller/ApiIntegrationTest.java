package com.cityshield.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end API checks on an in-memory H2 database seeded from the bundled CSV files.
 * Written for {@code mvn test}; it needs the Spring Boot dependencies from Maven Central.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired
    MockMvc mvc;

    private String body(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("health reports UP with the academic disclaimer")
    void health() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    @DisplayName("seed data is loaded: 50 zones and at least 100 roads")
    void seed() throws Exception {
        mvc.perform(get("/api/v1/zones")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(50));
        mvc.perform(get("/api/v1/roads")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("invalid incident is rejected with 400 and field details")
    void invalidIncident() throws Exception {
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FIRE\",\"zoneId\":1,\"description\":\"abc\",\"severity\":9,\"urgency\":3,\"affectedPeople\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("creating an incident returns 201, a score in 0..100, and it shows up in the priority queue")
    void createAndQueue() throws Exception {
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FIRE\",\"zoneId\":1,\"description\":\"Warehouse fire spreading\",\"severity\":5,"
                                + "\"urgency\":5,\"affectedPeople\":4000,\"requirements\":[]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.priorityScore").exists());
        mvc.perform(get("/api/v1/priority/queue?limit=5")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].rank").value(1));
    }

    @Test
    @DisplayName("distribution preview never changes inventory")
    void previewDoesNotMutate() throws Exception {
        String before = body("/api/v1/inventory/summary");
        mvc.perform(post("/api/v1/distribution/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceTypeId\":1,\"blockedRoadIds\":[]}")).andExpect(status().isOk());
        assertEquals(before, body("/api/v1/inventory/summary"));
    }

    @Test
    @DisplayName("allocation preview does not mark any team busy")
    void allocationPreviewReadOnly() throws Exception {
        String before = body("/api/v1/teams");
        mvc.perform(post("/api/v1/allocation/preview").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        assertEquals(before, body("/api/v1/teams"));
    }

    @Test
    @DisplayName("illegal status transition returns 409")
    void illegalTransition() throws Exception {
        mvc.perform(patch("/api/v1/incidents/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("percentile endpoint validates its range and answers for P90")
    void percentile() throws Exception {
        mvc.perform(post("/api/v1/analytics/percentile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"metric\":\"PRIORITY\",\"percentile\":150}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/analytics/percentile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"metric\":\"PRIORITY\",\"percentile\":90,\"seed\":42}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBaseline.agrees").value(true));
    }

    @Test
    @DisplayName("connectivity, coverage, dashboard and empty search pattern")
    void misc() throws Exception {
        mvc.perform(post("/api/v1/network/connectivity").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.componentCount").exists());
        mvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totalIncidents").exists());
        mvc.perform(post("/api/v1/search/incidents").contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
