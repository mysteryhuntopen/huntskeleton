package edu.mit.puzzle.cube.core.serverresources;

import edu.mit.puzzle.cube.core.model.InteractionRequest;
import edu.mit.puzzle.cube.core.model.PostResult;
import edu.mit.puzzle.cube.core.permissions.InteractionsPermission;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;

import org.apache.shiro.subject.Subject;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;

import java.util.Optional;

public class InteractionRequestResource extends AbstractCubeResource {

    private int getId() {
        String idString = (String) getRequest().getAttributes().get("id");
        if (idString == null) {
            throw new IllegalArgumentException("id must be specified");
        }
        try {
            return Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id is not valid");
        }
    }

    @Get
    public InteractionRequest handleGet() {
        int id = getId();
        Optional<InteractionRequest> interactionRequest = interactionRequestStore.getInteractionRequest(id);

        if (interactionRequest.isPresent()) {
            getSubject().checkPermission(
                    new InteractionsPermission(interactionRequest.get().getTeamId(), PermissionAction.READ));
            return interactionRequest.get();
        } else {
            throw new ResourceException(
                    Status.CLIENT_ERROR_NOT_FOUND,
                    String.format("Interaction request %d does not exist", id));
        }
    }

    @Post
    public PostResult handlePost(InteractionRequest interactionRequest) {
        int id = getId();
        if (interactionRequest.getStatus() == null) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    "A status must be specified when updating an interaction request");
        }

        Optional<InteractionRequest> existingInteractionRequest = interactionRequestStore.getInteractionRequest(id);
        if (!existingInteractionRequest.isPresent()) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_NOT_FOUND,
                    String.format("Interaction request %d does not exist", id));
        }

        Subject subject = getSubject();
        subject.checkPermission(
                new InteractionsPermission(existingInteractionRequest.get().getTeamId(), PermissionAction.UPDATE));

        if (existingInteractionRequest.get().getStatus().isTerminal()) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    "This interaction request has already been handled and may no longer be changed.");
        }

        String currentUsername = (String) subject.getPrincipal();

        if (existingInteractionRequest.get().getStatus().isAssigned()
                && interactionRequest.getStatus().isAssigned()
                && !existingInteractionRequest.get().getCallerUsername().equals(currentUsername)) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST,
                    String.format(
                            "This interaction request is already claimed by %s. It must be unassigned " +
                            "before you can change it.",
                            existingInteractionRequest.get().getCallerUsername()));
        }

        String callerUsername = null;
        if (interactionRequest.getStatus().isAssigned()) {
            callerUsername = currentUsername;
        }

        boolean changed = interactionRequestStore.updateInteractionRequest(
                id, interactionRequest.getStatus(), callerUsername, interactionRequest.getResponse());
        return PostResult.builder().setUpdated(changed).build();
    }
}
