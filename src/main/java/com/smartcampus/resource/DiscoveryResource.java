package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        Map<String, Object> response = new HashMap<>();
        response.put("api", "Smart Campus Sensor & Room Management API");
        response.put("version", "1.0");
        response.put("contact", "admin@smartcampus.ac.uk");
        response.put("description", "RESTful API for managing campus rooms and IoT sensors");

        Map<String, String> links = new HashMap<>();
        links.put("rooms", "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");
        response.put("resources", links);

        return Response.ok(response).build();
    }
    @GET
    @Path("/crash")
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerCrash() {
        // Deliberately throw an unhandled exception to demonstrate the GlobalExceptionMapper (500)
        throw new RuntimeException("Simulated unexpected failure for demonstration purposes");
    }
}
