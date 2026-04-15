package com.smartcampus;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central in-memory data store for the Smart Campus API.
 * Uses ConcurrentHashMaps for thread-safe access across multiple requests.
 */
public class DataStore {

    // Map of roomId -> Room
    public static final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // Map of sensorId -> Sensor
    public static final Map<String, Sensor> sensors = new ConcurrentHashMap<>();

    // Map of sensorId -> List<SensorReading>
    public static final Map<String, List<SensorReading>> readings = new ConcurrentHashMap<>();

    // Prevent instantiation
    private DataStore() {}
}
