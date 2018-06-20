package edu.mit.puzzle.cube.core;

import org.restlet.data.MediaType;
import org.restlet.ext.jackson.JacksonConverter;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;

public class CubeJacksonConverter extends JacksonConverter {
    @Override
    protected <T> JacksonRepresentation<T> create(MediaType mediaType, T source) {
        return new CubeJacksonRepresentation<T>(mediaType, source);
    }

    @Override
    protected <T> JacksonRepresentation<T> create(Representation source, Class<T> objectClass) {
        return new CubeJacksonRepresentation<>(source, objectClass);
    }
}
