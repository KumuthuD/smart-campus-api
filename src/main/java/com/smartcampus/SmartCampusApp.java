package com.smartcampus;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api/v1")
public class SmartCampusApp extends Application {
    // Jersey package scanning is configured in Main.java via ResourceConfig.
    // This class declares the application path for JAX-RS.
}
