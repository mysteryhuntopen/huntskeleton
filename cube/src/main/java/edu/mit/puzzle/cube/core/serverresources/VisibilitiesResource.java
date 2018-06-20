package edu.mit.puzzle.cube.core.serverresources;

import com.google.common.base.Splitter;

import edu.mit.puzzle.cube.core.model.Visibilities;
import edu.mit.puzzle.cube.core.model.Visibility;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;
import edu.mit.puzzle.cube.core.permissions.VisibilitiesPermission;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class VisibilitiesResource extends AbstractCubeResource {

    @Get
    public Visibilities handleGet() {
        Optional<String> teamId = Optional.ofNullable(getQueryValue("teamId"));
        if (teamId.isPresent()) {
            getSubject().checkPermission(
                    new VisibilitiesPermission(teamId.get(), PermissionAction.READ));
        } else {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    "A team id must be specified");
        }
        Optional<String> puzzleIdQueryValue = Optional.ofNullable(getQueryValue("puzzleId"));
        List<Visibility> visibilities;
        if (!puzzleIdQueryValue.isPresent()) {
            visibilities = huntStatusStore.getVisibilitiesForTeam(teamId.get());
        } else {
            List<String> puzzleIds = Splitter.on(",").splitToList(puzzleIdQueryValue.get());
            puzzleIds = puzzleStore.getCanonicalPuzzleIds(puzzleIds);
            visibilities = huntStatusStore.getVisibilitiesForTeam(teamId.get(), puzzleIds);
        }

        if (!getSubject().isPermitted(new VisibilitiesPermission("*", PermissionAction.READ))) {
            visibilities = visibilities.stream()
                    .filter(v -> !v.getStatus().equalsIgnoreCase("INVISIBLE"))
                    .collect(Collectors.toList());
        }

        return Visibilities.builder()
                .setVisibilities(visibilities)
                .build();
    }
}
