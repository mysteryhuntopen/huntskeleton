package edu.mit.puzzle.cube.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import edu.mit.puzzle.cube.core.AdjustableClock;
import edu.mit.puzzle.cube.core.db.ConnectionFactory;
import edu.mit.puzzle.cube.core.db.InMemoryConnectionFactory;
import edu.mit.puzzle.cube.core.events.Event;
import edu.mit.puzzle.cube.core.events.EventProcessor;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;

import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class HuntStatusStoreTest {

    private ConnectionFactory connectionFactory;
    private AdjustableClock clock;
    private VisibilityStatusSet visibilityStatusSet;
    private HuntStatusStore huntStatusStore;
    private EventProcessor<Event> eventProcessor;
    private PuzzleStore puzzleStore;

    private static String TEST_TEAM_ID = "testerteam";
    private static String TEST_PUZZLE_ID = "a_test_puzzle";
    private static String TEST_PUZZLE_ID_2 = "another_test_puzzle";
    private static String TEST_PUZZLE_ID_3 = "yet_another_test_puzzle";

    @Before
    public void setup() throws SQLException {
        visibilityStatusSet = new StandardVisibilityStatusSet();
        connectionFactory = new InMemoryConnectionFactory(
                visibilityStatusSet,
                Lists.newArrayList(TEST_TEAM_ID),
                Lists.newArrayList(TEST_PUZZLE_ID,TEST_PUZZLE_ID_2,TEST_PUZZLE_ID_3).stream()
                    .map(puzzleId -> Puzzle.create(puzzleId, "ANSWER"))
                    .collect(Collectors.toList()),
                ImmutableList.<User>of());
        clock = new AdjustableClock(Clock.fixed(Instant.now(), ZoneId.of("UTC")));
        eventProcessor = mock(EventProcessor.class);

        puzzleStore = mock(PuzzleStore.class);
        when(puzzleStore.getPuzzle(any())).thenAnswer(invocation -> {
           return Puzzle.create(invocation.getArgument(0), "ANSWER");
        });
        when(puzzleStore.getPuzzles(any())).thenAnswer(invocation -> {
           Collection<String> puzzleIds = invocation.getArgument(0);
           return puzzleIds.stream()
                   .map(puzzleId -> Puzzle.create(puzzleId, "ANSWER"))
                   .collect(Collectors.toMap(Puzzle::getPuzzleId, Function.identity()));
        });

        huntStatusStore = new HuntStatusStore(
                connectionFactory,
                clock,
                visibilityStatusSet,
                eventProcessor,
                puzzleStore
        );
    }

    @Test
    public void getVisibilityWithNoneSet() {
        assertThat(huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus())
                .isEqualTo(visibilityStatusSet.getDefaultVisibilityStatus());
    }

    @Test
    public void setAndGetVisibility() {
        boolean statusChanged = huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "UNLOCKED");
        assertTrue(statusChanged);
        assertEquals("UNLOCKED", huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        List<VisibilityChange> history = huntStatusStore.getVisibilityHistory(TEST_TEAM_ID, TEST_PUZZLE_ID);
        assertEquals(1, history.size());
        assertEquals(clock.instant(), history.get(0).getTimestamp());
        assertEquals("UNLOCKED", history.get(0).getStatus());

        verify(eventProcessor, times(1)).processBatch(any(List.class));
    }

    @Test
    public void setVisibilityToSameStatus() {
        huntStatusStore.setVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID, "UNLOCKED");
        assertEquals("UNLOCKED", huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        boolean statusChanged = huntStatusStore.setVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID, "UNLOCKED");
        assertFalse(statusChanged);
        assertEquals("UNLOCKED", huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        List<VisibilityChange> history = huntStatusStore.getVisibilityHistory(TEST_TEAM_ID, TEST_PUZZLE_ID);
        assertEquals(1, history.size());
        assertEquals(clock.instant(), history.get(0).getTimestamp());
        assertEquals("UNLOCKED", history.get(0).getStatus());

        verify(eventProcessor, times(1)).processBatch(any(List.class));
    }

    @Test
    public void setVisibilityToLocked() {
        boolean statusChanged = huntStatusStore.setVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID,
                visibilityStatusSet.getDefaultVisibilityStatus());
        assertFalse(statusChanged);
        assertEquals(visibilityStatusSet.getDefaultVisibilityStatus(),
                huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        List<VisibilityChange> history = huntStatusStore.getVisibilityHistory(TEST_TEAM_ID, TEST_PUZZLE_ID);
        assertEquals(0, history.size());

        verifyZeroInteractions(eventProcessor);
    }

    @Test
    public void setVisibilityWithIllegalCurrentStatus() {
        boolean statusChanged = huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "SOLVED");
        assertFalse(statusChanged);
        assertEquals(visibilityStatusSet.getDefaultVisibilityStatus(),
                huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        List<VisibilityChange> history = huntStatusStore.getVisibilityHistory(
                TEST_TEAM_ID, TEST_PUZZLE_ID);
        assertEquals(0, history.size());

        verifyZeroInteractions(eventProcessor);
    }

    @Test
    public void setVisibilityWithIllegalCurrentStatusWithOverride() {
        boolean statusChanged = huntStatusStore.setVisibilityWithoutWorkflowValidation(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "SOLVED");
        assertTrue(statusChanged);
        assertEquals("SOLVED",
                huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        List<VisibilityChange> history = huntStatusStore.getVisibilityHistory(
                TEST_TEAM_ID, TEST_PUZZLE_ID);
        assertEquals(1, history.size());

        verify(eventProcessor, times(1)).processBatch(any(List.class));
    }

    @Test
    public void setVisibilityMultipleTimes() throws InterruptedException {
        boolean statusChanged = huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "UNLOCKED");
        assertTrue(statusChanged);
        assertEquals("UNLOCKED", huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());
        Instant firstTimestamp = clock.instant();

        clock.setWrappedClock(Clock.fixed(clock.instant().plus(5, ChronoUnit.MINUTES), ZoneId.of("UTC")));
        Instant secondTimestamp = clock.instant();

        statusChanged = huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "SOLVED");
        assertTrue(statusChanged);
        assertEquals("SOLVED", huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID).getStatus());

        List<VisibilityChange> history = huntStatusStore.getVisibilityHistory(TEST_TEAM_ID, TEST_PUZZLE_ID);
        assertEquals(2, history.size());

        assertEquals(firstTimestamp, history.get(0).getTimestamp());
        assertEquals("UNLOCKED", history.get(0).getStatus());
        assertEquals(secondTimestamp, history.get(1).getTimestamp());
        assertEquals("SOLVED", history.get(1).getStatus());

        verify(eventProcessor, times(2)).processBatch(any(List.class));
    }

    @Test
    public void setMultipleVisibilities() throws InterruptedException {
        huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "UNLOCKED");
        huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID, "SOLVED");
        huntStatusStore.setVisibility(
                TEST_TEAM_ID, TEST_PUZZLE_ID_2, "UNLOCKED");

        List<Visibility> visibilities = huntStatusStore.getVisibilitiesForTeam(TEST_TEAM_ID);
        Visibility.Builder visibilityBuilder = Visibility.builder()
                .setTeamId(TEST_TEAM_ID);
        assertThat(visibilities).containsExactly(
                visibilityBuilder.setPuzzleId(TEST_PUZZLE_ID).setStatus("SOLVED").setSolvedAnswers(ImmutableList.of("ANSWER")).build(),
                visibilityBuilder.setPuzzleId(TEST_PUZZLE_ID_2).setStatus("UNLOCKED").setSolvedAnswers(ImmutableList.of()).build(),
                visibilityBuilder.setPuzzleId(TEST_PUZZLE_ID_3).setStatus(visibilityStatusSet.getDefaultVisibilityStatus()).setSolvedAnswers(ImmutableList.of()).build()
        );
    }

    @AutoValue
    public abstract static class HuntStatusStoreTestProperty extends Team.Property {
        static {
            registerClass(HuntStatusStoreTestProperty.class);
        }

        @JsonCreator
        public static HuntStatusStoreTestProperty create(@JsonProperty("value") String value) {
            return new AutoValue_HuntStatusStoreTest_HuntStatusStoreTestProperty(value);
        }

        @JsonProperty("value") public abstract String getValue();
    }

    @Test
    public void setTeamProperties() {
        assertThat(huntStatusStore.getTeam(TEST_TEAM_ID).getTeamProperties()).isNull();
        assertThat(huntStatusStore.setTeamProperty(
                TEST_TEAM_ID,
                HuntStatusStoreTestProperty.class,
                HuntStatusStoreTestProperty.create("SOME_VALUE"))).isTrue();

        Team team = huntStatusStore.getTeam(TEST_TEAM_ID);
        assertThat(team.getTeamProperties()).hasSize(1);
        assertThat(team.getTeamProperty(HuntStatusStoreTestProperty.class).getValue())
                .isEqualTo("SOME_VALUE");

        List<Team> teams = huntStatusStore.getTeams();
        assertThat(teams).containsExactly(team);

        Team team2 = Team.builder()
                .setTeamId("team2")
                .setEmail("team2@team2.com")
                .setPrimaryPhone("012-345-6789")
                .setSecondaryPhone("987-654-3210")
                .build();
        huntStatusStore.addTeam(team2);

        Team readTeam2 = huntStatusStore.getTeam("team2");
        assertThat(readTeam2).isEqualTo(team2);
        assertThat(readTeam2.getTeamProperties()).isNull();

        assertThat(huntStatusStore.setTeamProperty(
                "team2",
                HuntStatusStoreTestProperty.class,
                HuntStatusStoreTestProperty.create("TEAM2_VALUE"))).isTrue();

        team2 = huntStatusStore.getTeam("team2");
        assertThat(team2.getTeamProperties()).hasSize(1);
        assertThat(team2.getTeamProperty(HuntStatusStoreTestProperty.class).getValue())
                .isEqualTo("TEAM2_VALUE");

        teams = huntStatusStore.getTeams();
        assertThat(teams).containsExactly(team, team2);
    }

    @Test
    public void updateTeam() {
        Team team = huntStatusStore.getTeam(TEST_TEAM_ID);
        assertThat(team.getEmail()).isNull();
        assertThat(team.getPrimaryPhone()).isNull();
        assertThat(team.getSecondaryPhone()).isNull();

        team = team.toBuilder()
                .setEmail("testteam@testteam.com")
                .setPrimaryPhone("012-345-6789")
                .setSecondaryPhone("987-654-3210")
                .build();
        assertThat(huntStatusStore.updateTeam(team)).isTrue();

        Team readTeam = huntStatusStore.getTeam(TEST_TEAM_ID);
        assertThat(readTeam).isEqualTo(team);
    }

    @Test
    public void getVisibilitiesForTeamWithoutSubmissionAnswers() {
        huntStatusStore.setVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID, "UNLOCKED");
        Visibility expectedVisibility = Visibility.builder()
                .setTeamId(TEST_TEAM_ID)
                .setPuzzleId(TEST_PUZZLE_ID)
                .setStatus("UNLOCKED")
                .build();
        List<Visibility> visibilities = huntStatusStore.getVisibilitiesForTeam(TEST_TEAM_ID);
        assertThat(visibilities).contains(expectedVisibility);
        assertThat(huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID)).isEqualTo(expectedVisibility);

        huntStatusStore.setVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID, "SOLVED");
        expectedVisibility = Visibility.builder()
                .setTeamId(TEST_TEAM_ID)
                .setPuzzleId(TEST_PUZZLE_ID)
                .setStatus("SOLVED")
                .setSolvedAnswers(ImmutableList.of("ANSWER"))
                .build();
        visibilities = huntStatusStore.getVisibilitiesForTeam(TEST_TEAM_ID);
        assertThat(visibilities).contains(expectedVisibility);
        assertThat(huntStatusStore.getVisibility(TEST_TEAM_ID, TEST_PUZZLE_ID)).isEqualTo(expectedVisibility);
    }
}
