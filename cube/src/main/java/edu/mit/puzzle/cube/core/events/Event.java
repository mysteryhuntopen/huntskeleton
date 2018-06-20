package edu.mit.puzzle.cube.core.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.Map;

public abstract class Event {
    private static final ImmutableList<Class<? extends Event>> CORE_EVENT_CLASSES = ImmutableList.of(
            FullReleaseEvent.class,
            FullSolveEvent.class,
            HintCompleteEvent.class,
            HuntStartEvent.class,
            PeriodicTimerEvent.class,
            PuzzlePropertyChangeEvent.class,
            SubmissionCompleteEvent.class,
            VisibilityChangeEvent.class
    );

    private static Map<String, Class<? extends Event>> EVENT_CLASSES = new HashMap<>();

    public static void registerEventClass(Class<? extends Event> clazz) {
        if (!clazz.getSimpleName().endsWith("Event")) {
            throw new IllegalArgumentException(String.format(
                    "Event class name %s did not end with 'Event'", clazz.getSimpleName()));
        }
        String eventType = clazz.getSimpleName().substring(0, clazz.getSimpleName().length() - 5);
        EVENT_CLASSES.put(eventType, clazz);
    }

    static {
        for (Class<? extends Event> clazz : CORE_EVENT_CLASSES) {
            registerEventClass(clazz);
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EVENT_TYPE_FIELD_NAME = "eventType";

    @JsonCreator
    public static Event create(JsonNode jsonNode) {
        String eventType = jsonNode.get(EVENT_TYPE_FIELD_NAME).asText();
        Class<? extends Event> clazz = EVENT_CLASSES.get(eventType);
        if (clazz != null) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.remove(EVENT_TYPE_FIELD_NAME);
            return OBJECT_MAPPER.convertValue(objectNode, clazz);
        }
        throw new IllegalArgumentException("unknown eventType: " + eventType);
    }

    public abstract String getEventType();
}
