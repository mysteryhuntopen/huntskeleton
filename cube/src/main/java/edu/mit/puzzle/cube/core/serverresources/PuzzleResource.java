package edu.mit.puzzle.cube.core.serverresources;

import edu.mit.puzzle.cube.core.model.PostResult;
import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.User;
import edu.mit.puzzle.cube.core.model.Visibility;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;
import edu.mit.puzzle.cube.core.permissions.PuzzlesPermission;

import org.apache.shiro.subject.Subject;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import java.util.Map;
import java.util.Optional;

public class PuzzleResource extends AbstractCubeResource {

    private String getId() {
        String idString = (String) getRequest().getAttributes().get("id");
        if (idString == null) {
            throw new IllegalArgumentException("puzzle id must be specified");
        }
        return idString;
    }

    @Get
    public Puzzle handleGet() {
        Subject subject = getSubject();
        subject.checkPermission(new PuzzlesPermission(PermissionAction.READ));

        Optional<String> siteMode = Optional.ofNullable(getQueryValue("siteMode"));

        String puzzleId = getId();
        puzzleId = puzzleStore.getCanonicalPuzzleId(puzzleId);
        Puzzle puzzle = puzzleStore.getPuzzle(puzzleId);

        User user = userStore.getUser((String) subject.getPrincipal());
        if (user.getTeamId() != null) {
            Visibility visibility = huntStatusStore.getVisibility(user.getTeamId(), puzzleId);

            // If the puzzle has an invisible status for this team, pretend that it doesn't exist, unless we're in solution mode, in which case we don't care
            if (huntStatusStore.getVisibilityStatusSet().getInvisibleStatuses().contains(visibility.getStatus()) && !siteMode.equals(Optional.of("solution"))) {
                throw new ResourceException(
                        Status.CLIENT_ERROR_NOT_FOUND,
                        String.format("The puzzle id '%s' does not exist", puzzleId));
            }

            puzzle = puzzle.strip(subject, visibility, siteMode);
        }

        return puzzle;
    }

    @Post
    public PostResult handlePost(Puzzle puzzle) {
        getSubject().checkPermission(new PuzzlesPermission(PermissionAction.UPDATE));
        String puzzleId = getId();
        boolean updated = false;
        for (Map.Entry<String, Puzzle.Property> entry : puzzle.getPuzzleProperties().entrySet()) {
            Class<? extends Puzzle.Property> propertyClass = Puzzle.Property.getClass(entry.getKey());
            if (puzzleStore.setPuzzleProperty(puzzleId, propertyClass, entry.getValue())) {
                updated = true;
            }
        }
        return PostResult.builder().setUpdated(updated).build();
    }
}
