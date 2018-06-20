package edu.mit.puzzle.cube.core.exampleruns;

import com.fasterxml.jackson.databind.JsonNode;

import edu.mit.puzzle.cube.JsonUtils;
import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.RestletTest;
import edu.mit.puzzle.cube.core.db.CubeJdbcRealm;
import edu.mit.puzzle.cube.huntimpl.linearexample.LinearExampleHuntDefinition;

import org.apache.shiro.realm.Realm;
import org.junit.Test;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinearHuntRunTest extends RestletTest {
    protected static final ChallengeResponse TESTERTEAM_CREDENTIALS =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "testerteam", "testerteampassword");

    @Override
    protected HuntDefinition createHuntDefinition() {
        return new LinearExampleHuntDefinition();
    }

    @Override
    protected Realm createAuthenticationRealm() {
        CubeJdbcRealm realm = new CubeJdbcRealm();
        realm.setDataSource(serviceEnvironment.getConnectionFactory().getDataSource());
        return realm;
    }

    @Test
    public void testSubmittingAndUnlock() throws IOException {
        JsonNode json = getAllSubmissions();
        assertEquals(0, json.get("submissions").size());

        json = getVisibility("testerteam","puzzle1");
        assertEquals("INVISIBLE", json.get("status").asText());
        json = getVisibility("testerteam","puzzle2");
        assertEquals("INVISIBLE", json.get("status").asText());

        currentUserCredentials = TESTERTEAM_CREDENTIALS;

        json = getVisibility("testerteam","puzzle1");
        assertEquals("INVISIBLE", json.get("status").asText());
        json = getVisibility("testerteam","puzzle2");
        assertEquals("INVISIBLE", json.get("status").asText());
        json = getVisibility("testerteam","puzzle3");
        assertEquals("INVISIBLE", json.get("status").asText());

        json = get("/puzzles?teamId=testerteam");
        assertEquals(0, json.get("puzzles").size());

        json = get("/run");
        assertThat(json.get("startTimestamp").isNull()).isTrue();

        currentUserCredentials = ADMIN_CREDENTIALS;

        postHuntStart();

        json = get("/run");
        assertThat(json.get("startTimestamp").isNull()).isFalse();

        json = getVisibility("testerteam","puzzle1");
        assertEquals("UNLOCKED", json.get("status").asText());
        json = getVisibility("testerteam","puzzle2");
        assertEquals("VISIBLE", json.get("status").asText());
        json = getVisibility("testerteam","puzzle3");
        assertEquals("INVISIBLE", json.get("status").asText());

        currentUserCredentials = TESTERTEAM_CREDENTIALS;

        json = getVisibility("testerteam","puzzle1");
        assertEquals("UNLOCKED", json.get("status").asText());
        json = getVisibility("testerteam","puzzle2");
        assertEquals("VISIBLE", json.get("status").asText());
        json = getVisibility("testerteam","puzzle3");
        assertEquals("INVISIBLE", json.get("status").asText());

        json = get("/puzzles?teamId=testerteam");
        assertEquals(2, json.get("puzzles").size());
        JsonNode puzzle1Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle1"));
        assertEquals("puzzle1", puzzle1Node.get("puzzleId").asText());
        assertEquals("puzzle1", puzzle1Node.get("puzzleProperties").get("DisplayNameProperty").get("displayName").asText());
        assertEquals(0, puzzle1Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());
        JsonNode puzzle2Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle2"));
        assertEquals("puzzle2", puzzle2Node.get("puzzleId").asText());
        assertEquals(1, puzzle2Node.get("puzzleProperties").size());
        assertEquals(0, puzzle2Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());

        currentUserCredentials = TESTERTEAM_CREDENTIALS;

        postNewSubmission("testerteam", "puzzle1", "guess");

        currentUserCredentials = ADMIN_CREDENTIALS;

        json = getSubmission(1);
        assertEquals("SUBMITTED", json.get("status").asText());
        json = getAllSubmissions();
        assertEquals(1, json.get("submissions").size());

        postUpdateSubmission(1, "CORRECT");

        json = getSubmission(1);
        assertEquals("CORRECT", json.get("status").asText());
        assertEquals("adminuser", json.get("callerUsername").asText());

        json = getVisibility("testerteam", "puzzle1");
        assertEquals("SOLVED", json.get("status").asText());
        assertThat(json.get("solvedAnswers").size()).isEqualTo(1);
        assertThat(json.get("solvedAnswers").get(0).asText()).isEqualTo("ANSWER1");

        json = getVisibility("testerteam", "puzzle2");
        assertEquals("UNLOCKED", json.get("status").asText());
        assertThat(json.get("solvedAnswers").size()).isEqualTo(0);

        json = getVisibility("testerteam", "puzzle3");
        assertEquals("VISIBLE", json.get("status").asText());
        assertThat(json.get("solvedAnswers").size()).isEqualTo(0);

        json = getVisibility("testerteam", "puzzle5");
        assertEquals("INVISIBLE", json.get("status").asText());
        assertThat(json.get("solvedAnswers").size()).isEqualTo(0);

        currentUserCredentials = TESTERTEAM_CREDENTIALS;

        json = getVisibility("testerteam","puzzle1");
        assertEquals("SOLVED", json.get("status").asText());
        json = getVisibility("testerteam","puzzle2");
        assertEquals("UNLOCKED", json.get("status").asText());
        json = getVisibility("testerteam","puzzle3");
        assertEquals("VISIBLE", json.get("status").asText());

        json = get("/puzzles?teamId=testerteam");
        assertEquals(3, json.get("puzzles").size());
        puzzle1Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle1"));
        assertEquals("puzzle1", puzzle1Node.get("puzzleId").asText());
        assertEquals("puzzle1", puzzle1Node.get("puzzleProperties").get("DisplayNameProperty").get("displayName").asText());
        assertEquals(1, puzzle1Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());
        assertEquals("ANSWER1", puzzle1Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .get(0).get("canonicalAnswer").asText());
        puzzle2Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle2"));
        assertEquals("puzzle2", puzzle2Node.get("puzzleId").asText());
        assertEquals("puzzle2", puzzle2Node.get("puzzleProperties").get("DisplayNameProperty").get("displayName").asText());
        assertEquals(0, puzzle2Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());
        JsonNode puzzle3Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle3"));
        assertEquals("puzzle3", puzzle3Node.get("puzzleId").asText());
        assertEquals(1, puzzle3Node.get("puzzleProperties").size());
        assertEquals(0, puzzle3Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());

        currentUserCredentials = ADMIN_CREDENTIALS;
        post("/puzzles/puzzle2", "{\"puzzleId\": \"puzzle2\", \"puzzleProperties\": {\"DisplayNameProperty\": {\"displayName\": \"Updated puzzle 2\", \"visibilityRequirement\":[\"UNLOCKED\",\"SOLVED\"]}}}");

        currentUserCredentials = TESTERTEAM_CREDENTIALS;
        json = get("/puzzles?teamId=testerteam");
        assertEquals(3, json.get("puzzles").size());
        puzzle1Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle1"));
        assertEquals("puzzle1", puzzle1Node.get("puzzleId").asText());
        assertEquals("puzzle1", puzzle1Node.get("puzzleProperties").get("DisplayNameProperty").get("displayName").asText());
        assertEquals(1, puzzle1Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());
        assertEquals("ANSWER1", puzzle1Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .get(0).get("canonicalAnswer").asText());
        puzzle2Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle2"));
        assertEquals("puzzle2", puzzle2Node.get("puzzleId").asText());
        assertEquals("Updated puzzle 2", puzzle2Node.get("puzzleProperties").get("DisplayNameProperty").get("displayName").asText());
        assertEquals(0, puzzle2Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());
        puzzle3Node = JsonUtils.getOnlyElementForPredicate(
                json.get("puzzles"), n -> n.get("puzzleId").asText().equals("puzzle3"));
        assertEquals("puzzle3", puzzle3Node.get("puzzleId").asText());
        assertEquals(1, puzzle3Node.get("puzzleProperties").size());
        assertEquals(0, puzzle3Node
                .get("puzzleProperties")
                .get("AnswersProperty")
                .get("answers")
                .size());

        currentUserCredentials = ADMIN_CREDENTIALS;
        postFullRelease("puzzle5");
        json = getVisibility("testerteam", "puzzle5");
        assertEquals("UNLOCKED", json.get("status").asText());
    }

}
