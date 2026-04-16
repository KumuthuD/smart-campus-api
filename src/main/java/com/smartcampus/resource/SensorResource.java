package com.smartcampus.resource;

import com.smartcampus.DataStore;
import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    // GET /api/v1/sensors — list all sensors (with optional ?type= filter)
    @GET
    public Response getAllSensors(@QueryParam("type") String type) {
        Collection<Sensor> allSensors = DataStore.sensors.values();
        if (type != null && !type.isBlank()) {
            // Case-insensitive filtering for robustness
            List<Sensor> filtered = allSensors.stream()
                    .filter(s -> s.getType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
            return Response.ok(filtered).build();
        }
        return Response.ok(new ArrayList<>(allSensors)).build();
    }

    // POST /api/v1/sensors — register a new sensor (validates roomId exists)
    @POST
    public Response createSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("BAD_REQUEST", "Sensor ID is required"))
                    .build();
        }
        if (DataStore.sensors.containsKey(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(errorBody("CONFLICT", "Sensor '" + sensor.getId() + "' already exists"))
                    .build();
        }
        // Validate that the referenced roomId actually exists
        if (sensor.getRoomId() == null || !DataStore.rooms.containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                "Room '" + sensor.getRoomId() + "' does not exist. " +
                "Cannot register sensor without a valid room reference."
            );
        }
        // Set default status if not provided
        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }
        DataStore.sensors.put(sensor.getId(), sensor);
        // Update the parent room's sensorIds list
        Room room = DataStore.rooms.get(sensor.getRoomId());
        if (room != null && !room.getSensorIds().contains(sensor.getId())) {
            room.getSensorIds().add(sensor.getId());
        }
        // Initialise the readings list for this sensor
        DataStore.readings.putIfAbsent(sensor.getId(), new ArrayList<>());
        return Response.status(Response.Status.CREATED)
                .header("Location", "/api/v1/sensors/" + sensor.getId())
                .entity(sensor)
                .build();
    }

    // GET /api/v1/sensors/{sensorId} — get a specific sensor
    @GET
    @Path("/{sensorId}")
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = DataStore.sensors.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorBody("NOT_FOUND", "Sensor '" + sensorId + "' not found"))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    // Sub-resource locator for readings — delegates to SensorReadingResource
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
        // Validate sensor exists before delegating
        if (!DataStore.sensors.containsKey(sensorId)) {
            throw new NotFoundException("Sensor '" + sensorId + "' not found");
        }
        return new SensorReadingResource(sensorId);
    }

    private Map<String, String> errorBody(String error, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
