package edu.mit.puzzle.cube.core.serverresources;

import com.google.common.base.Preconditions;

import edu.mit.puzzle.cube.core.model.InteractionRequest;
import edu.mit.puzzle.cube.core.model.InteractionRequests;
import edu.mit.puzzle.cube.core.model.PostResult;
import edu.mit.puzzle.cube.core.model.Visibility;
import edu.mit.puzzle.cube.core.permissions.InteractionsPermission;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import java.util.List;

public class InteractionRequestsResource extends AbstractCubeResource {
    @Get
    public InteractionRequests handleGet() {
        List<InteractionRequest> interactionRequests;
        String teamId = getQueryValue("teamId");
        if (teamId != null && !teamId.isEmpty()) {
            String puzzleId = getQueryValue("puzzleId");
            Preconditions.checkArgument(
                    puzzleId != null && !puzzleId.isEmpty(),
                    "puzzleId must be specified"
            );
            puzzleId = puzzleStore.getCanonicalPuzzleId(puzzleId);

            getSubject().checkPermission(
                    new InteractionsPermission(teamId, PermissionAction.READ)
            );
            interactionRequests = interactionRequestStore.getInteractionRequestsForTeamAndPuzzle(teamId, puzzleId);
        } else {
            getSubject().checkPermission(
                    new InteractionsPermission("*", PermissionAction.READ)
            );
            interactionRequests = interactionRequestStore.getNonTerminalInteractionRequests();
        }
        return InteractionRequests.builder().setInteractionRequests(interactionRequests).build();
    }

    @Post
    public PostResult handlePost(InteractionRequest interactionRequest) {
        getSubject().checkPermission(
                new InteractionsPermission(interactionRequest.getTeamId(), PermissionAction.CREATE));

        interactionRequest = interactionRequest.toBuilder()
            .setPuzzleId(puzzleStore.getCanonicalPuzzleId(interactionRequest.getPuzzleId()))
            .build();

        Visibility visibility = huntStatusStore.getVisibility(
                interactionRequest.getTeamId(),
                interactionRequest.getPuzzleId()
        );
        if (huntStatusStore.getVisibilityStatusSet().getInvisibleStatuses().contains(visibility.getStatus())) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    "An interaction request is not allowed due to puzzle visibility");
        }

        boolean success = interactionRequestStore.createInteractionRequest(interactionRequest);
        return PostResult.builder().setCreated(success).build();
    }
}
