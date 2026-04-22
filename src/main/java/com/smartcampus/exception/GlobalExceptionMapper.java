package com.smartcampus.exception;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        // IMPORTANT: Let JAX-RS standard errors (like 404, 400, 405) pass through normally
        if (exception instanceof javax.ws.rs.WebApplicationException) {
            return ((javax.ws.rs.WebApplicationException) exception).getResponse();
        }

        // Log the full stack trace INTERNALLY (for developer debugging)
        LOGGER.log(Level.SEVERE, "Unexpected error occurred: " + exception.getMessage(), exception);

        // Return a clean, generic response EXTERNALLY (never expose stack trace)
        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_SERVER_ERROR");
        error.put("status", "500");
        error.put("message", "An unexpected error occurred. Please contact the system administrator.");
        error.put("reference", "Check server logs for details.");

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}
