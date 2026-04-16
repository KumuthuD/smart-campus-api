package com.smartcampus.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

@Provider
public class LinkedResourceNotFoundExceptionMapper
        implements ExceptionMapper<LinkedResourceNotFoundException> {

    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "UNPROCESSABLE_ENTITY");
        error.put("status", "422");
        error.put("message", exception.getMessage());
        error.put("hint", "Ensure the referenced roomId exists before registering a sensor.");

        return Response.status(422) // 422 is not in Response.Status enum in older versions
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}
