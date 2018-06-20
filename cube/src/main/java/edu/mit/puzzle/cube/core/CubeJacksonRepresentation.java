package edu.mit.puzzle.cube.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.restlet.data.MediaType;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;

public class CubeJacksonRepresentation<T> extends JacksonRepresentation<T> {
    public CubeJacksonRepresentation(MediaType mediaType, T object) {
        super(mediaType, object);
    }

    public CubeJacksonRepresentation(Representation representation, Class<T> objectClass) {
        super(representation, objectClass);
    }

    public CubeJacksonRepresentation(T object) {
        super(object);
    }

    @Override
    protected ObjectMapper createObjectMapper() {
        return super.createObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
    }
}
