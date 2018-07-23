package edu.mit.puzzle.cube.core;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.representation.OutputRepresentation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class PrometheusRestlet extends Restlet {

    private class PrometheusTextFormatRepresentation extends OutputRepresentation {
        PrometheusTextFormatRepresentation() {
            super(MediaType.TEXT_PLAIN);
            setCharacterSet(CharacterSet.UTF_8);
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            try (Writer writer = new OutputStreamWriter(outputStream)) {
                TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
                writer.flush();
            }
        }
    }

    @Override
    public void handle(Request request, Response response) {
        if (request.getMethod() != Method.GET) {
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            response.commit();
            return;
        }

        response.setStatus(Status.SUCCESS_OK);
        response.setEntity(new PrometheusTextFormatRepresentation());
        response.commit();
    }
}
