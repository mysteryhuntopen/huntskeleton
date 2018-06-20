package edu.mit.puzzle.cube.core.permissions;

public class PuzzlesPermission extends ActionLevelPermission {
    private static final long serialVersionUID = 1L;

    public PuzzlesPermission(PermissionAction... actions) {
        super("puzzles", actions);
    }
}
