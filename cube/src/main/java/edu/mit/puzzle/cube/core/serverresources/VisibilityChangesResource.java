package edu.mit.puzzle.cube.core.serverresources;

import edu.mit.puzzle.cube.core.model.VisibilityChange;
import edu.mit.puzzle.cube.core.model.VisibilityChanges;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;
import edu.mit.puzzle.cube.core.permissions.VisibilitiesPermission;

import org.restlet.resource.Get;

import java.util.List;
import java.util.Optional;

public class VisibilityChangesResource extends AbstractCubeResource {

    @Get
    public VisibilityChanges handleGet() {
        Optional<String> teamId = Optional.ofNullable(getQueryValue("teamId"));
        List<VisibilityChange> visibilityChanges;
        if (teamId.isPresent()) {
            getSubject().checkPermission(
                    new VisibilitiesPermission(teamId.get(), PermissionAction.READ));
            visibilityChanges = huntStatusStore.getTeamVisibilityHistory(teamId.get());
        } else {
            getSubject().checkPermission(
                    new VisibilitiesPermission("*", PermissionAction.READ));
            visibilityChanges = huntStatusStore.getVisibilityChanges();
        }
        return VisibilityChanges.builder()
                .setVisibilityChanges(visibilityChanges)
                .build();
    }
}
