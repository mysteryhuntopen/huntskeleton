package edu.mit.puzzle.cube.core.permissions;

import edu.mit.puzzle.cube.core.events.Event;

public class EventsPermission extends InstanceLevelPermission {
    private static final long serialVersionUID = 1L;

    public EventsPermission(Event event, PermissionAction... actions) {
        this(event.getEventType(), actions);
    }

    public EventsPermission(String eventType, PermissionAction... actions) {
        super("events", eventType, actions);
    }
}
