package edu.mit.puzzle.cube.huntimpl.setec2017;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.RestletTest;
import edu.mit.puzzle.cube.core.db.CubeJdbcRealm;
import edu.mit.puzzle.cube.core.events.FullReleaseEvent;
import edu.mit.puzzle.cube.core.events.FullSolveEvent;
import edu.mit.puzzle.cube.core.model.HintRequest;
import edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition.Character;
import edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition.InventoryItem;

import org.apache.shiro.realm.Realm;
import org.junit.Test;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class Setec2017HuntRunTest extends RestletTest {
    protected static final ChallengeResponse TESTERTEAM_CREDENTIALS =
            new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "testerteam", "testerteampassword");

    @Override
    protected HuntDefinition createHuntDefinition() {
        return new Setec2017HuntDefinition();
    }

    @Override
    protected Realm createAuthenticationRealm() {
        CubeJdbcRealm realm = new CubeJdbcRealm();
        realm.setDataSource(serviceEnvironment.getConnectionFactory().getDataSource());
        return realm;
    }

    private int getGold() {
        JsonNode json = get("/teams/testerteam");
        return json.get("teamProperties").get("GoldProperty").get("gold").asInt();
    }

    private int getCharacterLevel(Character character) {
        JsonNode json = get("/teams/testerteam");
        JsonNode characterNode = json
                .get("teamProperties")
                .get("CharacterLevelsProperty")
                .get("levels")
                .get(character.name());
        if (characterNode != null) {
            return characterNode.asInt();
        } else {
            return 0;
        }
    }

    private ImmutableSet<InventoryItem> getInventoryItems() {
        JsonNode json = get("/teams/testerteam");
        JsonNode inventoryItemsNode = json
                .get("teamProperties")
                .get("InventoryProperty")
                .get("inventoryItems");
        ImmutableSet.Builder<InventoryItem> inventoryItems = ImmutableSet.builder();
        for (JsonNode inventoryItemNode : inventoryItemsNode) {
            inventoryItems.add(InventoryItem.valueOf(inventoryItemNode.asText()));
        }
        return inventoryItems.build();
    }

    @Test
    public void testRun() throws IOException {
        postHuntStart();

        assertThat(getCharacterLevel(Character.FIGHTER)).isEqualTo(0);
        assertThat(getGold()).isEqualTo(0);
        assertThat(getInventoryItems()).isEqualTo(ImmutableSet.of());

        JsonNode json = getVisibility("testerteam", "fighter");
        assertThat(json.get("status").asText()).isEqualTo("UNLOCKED");
        json = getVisibility("testerteam", "f5");
        assertThat(json.get("status").asText()).isEqualTo("UNLOCKED");
        json = getVisibility("testerteam", "f4");
        assertThat(json.get("status").asText()).isEqualTo("VISIBLE");
        json = getVisibility("testerteam", "f8");
        assertThat(json.get("status").asText()).isEqualTo("VISIBLE");
        json = getVisibility("testerteam", "dynast");
        assertThat(json.get("status").asText()).isEqualTo("INVISIBLE");

        currentUserCredentials = TESTERTEAM_CREDENTIALS;
        postNewSubmission("testerteam", "f5", "FIGHTER1");
        currentUserCredentials = ADMIN_CREDENTIALS;
        postUpdateSubmission(1, "CORRECT");

        assertThat(getCharacterLevel(Character.FIGHTER)).isEqualTo(1);
        assertThat(getCharacterLevel(Character.WIZARD)).isEqualTo(0);
        assertThat(getGold()).isEqualTo(0);
        assertThat(getInventoryItems()).isEmpty();
        json = getVisibility("testerteam", "f5");
        assertThat(json.get("status").asText()).isEqualTo("SOLVED");
        json = getVisibility("testerteam", "f3");
        assertThat(json.get("status").asText()).isEqualTo("UNLOCKED");
        json = getVisibility("testerteam", "f4");
        assertThat(json.get("status").asText()).isEqualTo("VISIBLE");
        json = getVisibility("testerteam", "f6");
        assertThat(json.get("status").asText()).isEqualTo("VISIBLE");

        currentUserCredentials = TESTERTEAM_CREDENTIALS;
        postNewSubmission("testerteam", "f3", "FIGHTER1");
        currentUserCredentials = ADMIN_CREDENTIALS;
        postUpdateSubmission(2, "CORRECT");

        assertThat(getInventoryItems()).isEmpty();
        json = getVisibility("testerteam", "f1");
        assertThat(json.get("status").asText()).isEqualTo("UNLOCKED");
        json = getVisibility("testerteam", "f8");
        assertThat(json.get("status").asText()).isEqualTo("VISIBLE");
        json = getVisibility("testerteam", "f6");
        assertThat(json.get("status").asText()).isEqualTo("VISIBLE");

        json = post(
                "/hintrequests",
                HintRequest.builder()
                        .setTeamId("testerteam")
                        .setPuzzleId("f1")
                        .setRequest("help")
                        .build()
        );
        assertThat(json.get("created").asBoolean()).isFalse();
        assertThat(getGold()).isEqualTo(0);

        post(
                "/events",
                "{\"eventType\": \"GrantGold\", \"teamId\": \"testerteam\", \"gold\": 10}"
        );
        assertThat(getGold()).isEqualTo(10);

        post(
                "/events",
                "{\"eventType\": \"GrantGold\", \"teamId\": \"testerteam\", \"gold\": 20}"
        );
        assertThat(getGold()).isEqualTo(30);

        assertThat(getCharacterLevel(Character.FIGHTER)).isEqualTo(2);
        assertThat(getCharacterLevel(Character.CLERIC)).isEqualTo(0);
        post(
                "/events",
                "{\"eventType\": \"FullRelease\", \"puzzleId\": \"eventa\"}"
        );
        post(
                "/events",
                "{\"eventType\": \"FullSolve\", \"puzzleId\": \"eventa\"}"
        );
        json = getVisibility("testerteam", "eventa");
        assertThat(json.get("solvedAnswers").size()).isEqualTo(1);
        assertThat(json.get("solvedAnswers").get(0).asText()).isEqualTo("EVENTA");
        assertThat(getCharacterLevel(Character.FIGHTER)).isEqualTo(3);
        assertThat(getCharacterLevel(Character.CLERIC)).isEqualTo(1);
    }
}
