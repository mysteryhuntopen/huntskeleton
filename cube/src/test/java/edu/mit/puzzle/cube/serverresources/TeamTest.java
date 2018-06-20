package edu.mit.puzzle.cube.serverresources;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.RestletTest;
import edu.mit.puzzle.cube.core.db.CubeJdbcRealm;
import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.Team;
import edu.mit.puzzle.cube.core.model.VisibilityStatusSet;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;

import org.apache.shiro.realm.Realm;
import org.junit.Test;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;

import java.sql.SQLException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class TeamTest extends RestletTest {
    private static final ChallengeResponse TEAM =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "team", "teampassword");
    private static final ChallengeResponse TEAM2 =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "team2", "team2password");

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
                return ImmutableList.of();
            }

            @Override
            public void addToEventProcessor() {
            }
        };
    }

    public void setUp() throws SQLException {
        super.setUp();
        addTeam(TEAM);
        addTeam(TEAM2);
    }

    @Test
    public void testTeamChangeContactInfo() {
        Team team = Team.builder()
                .setTeamId("team")
                .setEmail("team@team.com")
                .setHeadquarters("10-250")
                .setPrimaryPhone("555-1212")
                .setSecondaryPhone("555-1213")
                .build();

        setCurrentUserCredentials(TEAM);
        post("/teams/team", team);
        JsonNode teamJson = get("/teams/team");
        assertThat(teamJson.get("email").asText()).isEqualTo(team.getEmail());
        assertThat(teamJson.get("headquarters").asText()).isEqualTo(team.getHeadquarters());
        assertThat(teamJson.get("primaryPhone").asText()).isEqualTo(team.getPrimaryPhone());
        assertThat(teamJson.get("secondaryPhone").asText()).isEqualTo(team.getSecondaryPhone());
    }

    @Test
    public void testTeamTryToChangeOtherTeamsContactInfo() {
        Team team = Team.builder()
                .setTeamId("team")
                .setEmail("team@team.com")
                .setPrimaryPhone("555-1212")
                .setSecondaryPhone("555-1213")
                .build();
        setCurrentUserCredentials(TEAM);
        postExpectFailure("/teams/team2", team);
    }
}
