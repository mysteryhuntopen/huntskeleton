package edu.mit.puzzle.cube.core.permissions;

public class InteractionsPermission extends InstanceLevelPermission {
    private static final long serialVersionUID = 1L;

    public InteractionsPermission(String teamId, PermissionAction... actions) {
        super("interactions", teamId, actions);
    }
}
