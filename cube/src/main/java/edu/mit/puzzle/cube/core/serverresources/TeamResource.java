package edu.mit.puzzle.cube.core.serverresources;

import com.google.common.base.Preconditions;

import edu.mit.puzzle.cube.core.model.PostResult;
import edu.mit.puzzle.cube.core.model.Team;
import edu.mit.puzzle.cube.core.model.User;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;
import edu.mit.puzzle.cube.core.permissions.TeamsPermission;

import org.restlet.resource.Get;
import org.restlet.resource.Post;

public class TeamResource extends AbstractCubeResource {

    private String getId() {
        String idString = (String) getRequest().getAttributes().get("id");
        if (idString == null) {
            throw new IllegalArgumentException("id must be specified");
        }
        return idString;
    }

    @Get
    public Team handleGet() {
        String id = getId();
        getSubject().checkPermission(
                new TeamsPermission(id, PermissionAction.READ));
        return huntStatusStore.getTeam(id);
    }

    @Post
    public PostResult handlePost(Team team) {
        String teamId = getId();
        getSubject().checkPermission(
                new TeamsPermission(teamId, PermissionAction.UPDATE));
        Preconditions.checkArgument(
                team.getTeamId().equals(teamId),
                "The teamId field must be specified"
        );
        team.validate();

        boolean didUpdate = huntStatusStore.updateTeam(team);

        if ((team.getPassword() != null) && (team.getPassword().length() > 0)) {
            User user = User.builder()
                .setUsername(team.getTeamId())
                .setPassword(team.getPassword())
                .setTeamId(team.getTeamId())
                .build();

            boolean userDidUpdate = userStore.updateUser(user);
            didUpdate = didUpdate || userDidUpdate;
        }

        return PostResult.builder().setUpdated(didUpdate).build();
    }
}
