package edu.mit.puzzle.cube.serverresources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.RestletTest;
import edu.mit.puzzle.cube.core.db.CubeJdbcRealm;
import edu.mit.puzzle.cube.core.model.InteractionRequest;
import edu.mit.puzzle.cube.core.model.InteractionRequestStatus;
import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.VisibilityStatusSet;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;

import org.apache.shiro.realm.Realm;
import org.junit.Test;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Status;

import java.sql.SQLException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class InteractionRequestsTest extends RestletTest {
    private static final String PUZZLE_ID = "puzzle1";

    private static final ChallengeResponse USER_ONE =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "userone", "useronepassword");
    private static final ChallengeResponse USER_TWO =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "usertwo", "usertwopassword");
    private static final ChallengeResponse TEAM =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "team", "teampassword");

    @Override
    protected Realm createAuthenticationRealm() {
        CubeJdbcRealm realm = new CubeJdbcRealm();
        realm.setDataSource(serviceEnvironment.getConnectionFactory().getDataSource());
        return realm;
    }

    protected HuntDefinition createHuntDefinition() {
        return new HuntDefinition() {
            @Override
            public VisibilityStatusSet getVisibilityStatusSet() {
                return new StandardVisibilityStatusSet();
            }

            @Override
            public List<Puzzle> getPuzzles() {
                return ImmutableList.of(Puzzle.create(PUZZLE_ID, "ANSWER"));
            }

            @Override
            public void addToEventProcessor() {
            }
        };
    }

    public void setUp() throws SQLException {
        super.setUp();
        addUser(USER_ONE, ImmutableList.of("writingteam"));
        addUser(USER_TWO, ImmutableList.of("writingteam"));
        addTeam(TEAM);
        postHuntStart();
    }

    @Test
    public void testRequestInteractionForInvisiblePuzzle() {
        setCurrentUserCredentials(TEAM);
        Status status = postExpectFailure(
                "/interactionrequests",
                InteractionRequest.builder()
                        .setTeamId(TEAM.getIdentifier())
                        .setPuzzleId(PUZZLE_ID)
                        .setRequest("help")
                        .build()
        );
        assertThat(status.getCode()).isEqualTo(400);
    }

    @Test
    public void testRequestAndResolveInteraction() {
        postVisibility(TEAM.getIdentifier(), PUZZLE_ID, "UNLOCKED");

        setCurrentUserCredentials(TEAM);
        post(
                "/interactionrequests",
                InteractionRequest.builder()
                        .setTeamId(TEAM.getIdentifier())
                        .setPuzzleId(PUZZLE_ID)
                        .setRequest("help")
                        .build()
        );

        JsonNode teamGetResult = get(
                String.format("/interactionrequests?teamId=%s&puzzleId=%s", TEAM.getIdentifier(), PUZZLE_ID)
        );
        assertThat(teamGetResult.get("interactionRequests").size()).isEqualTo(1);
        JsonNode interactionRequest = teamGetResult.get("interactionRequests").get(0);
        assertThat(interactionRequest.get("teamId").asText()).isEqualTo(TEAM.getIdentifier());
        assertThat(interactionRequest.get("puzzleId").asText()).isEqualTo(PUZZLE_ID);
        assertThat(interactionRequest.get("status").asText()).isEqualTo(InteractionRequestStatus.REQUESTED.name());
        assertThat(interactionRequest.get("request").asText()).isEqualTo("help");

        setCurrentUserCredentials(USER_ONE);
        post(
                String.format("/interactionrequests/%d", interactionRequest.get("interactionRequestId").asInt()),
                InteractionRequest.builder()
                        .setInteractionRequestId(interactionRequest.get("interactionRequestId").asInt())
                        .setStatus(InteractionRequestStatus.ASSIGNED)
                        .build()
        );

        JsonNode userGetResult = get(
                String.format("/interactionrequests", TEAM.getIdentifier(), PUZZLE_ID)
        );
        assertThat(userGetResult.get("interactionRequests").size()).isEqualTo(1);
        interactionRequest = userGetResult.get("interactionRequests").get(0);
        assertThat(interactionRequest.get("teamId").asText()).isEqualTo(TEAM.getIdentifier());
        assertThat(interactionRequest.get("puzzleId").asText()).isEqualTo(PUZZLE_ID);
        assertThat(interactionRequest.get("status").asText()).isEqualTo(InteractionRequestStatus.ASSIGNED.name());
        assertThat(interactionRequest.get("callerUsername").asText()).isEqualTo(USER_ONE.getIdentifier());
        assertThat(interactionRequest.get("request").asText()).isEqualTo("help");

        post(
                String.format("/interactionrequests/%d", interactionRequest.get("interactionRequestId").asInt()),
                InteractionRequest.builder()
                        .setInteractionRequestId(interactionRequest.get("interactionRequestId").asInt())
                        .setResponse("response")
                        .setStatus(InteractionRequestStatus.COMPLETE)
                        .build()
        );

        userGetResult = get(
                String.format("/interactionrequests", TEAM.getIdentifier(), PUZZLE_ID)
        );
        assertThat(userGetResult.get("interactionRequests").size()).isEqualTo(0);

        setCurrentUserCredentials(TEAM);
        teamGetResult = get(
                String.format("/interactionrequests?teamId=%s&puzzleId=%s", TEAM.getIdentifier(), PUZZLE_ID)
        );
        assertThat(teamGetResult.get("interactionRequests").size()).isEqualTo(1);
        interactionRequest = teamGetResult.get("interactionRequests").get(0);
        assertThat(interactionRequest.get("teamId").asText()).isEqualTo(TEAM.getIdentifier());
        assertThat(interactionRequest.get("puzzleId").asText()).isEqualTo(PUZZLE_ID);
        assertThat(interactionRequest.get("status").asText()).isEqualTo(InteractionRequestStatus.COMPLETE.name());
        assertThat(interactionRequest.get("request").asText()).isEqualTo("help");
        assertThat(interactionRequest.get("response").asText()).isEqualTo("response");
    }
}
