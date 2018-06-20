package edu.mit.puzzle.cube.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;

import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.db.ConnectionFactory;
import edu.mit.puzzle.cube.core.db.DatabaseHelper;
import edu.mit.puzzle.cube.core.db.DatabaseHelper.SQLRetryException;
import edu.mit.puzzle.cube.core.events.Event;
import edu.mit.puzzle.cube.core.events.EventProcessor;
import edu.mit.puzzle.cube.core.events.VisibilityChangeEvent;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class HuntStatusStore {
    private static Logger LOGGER = LoggerFactory.getLogger(HuntStatusStore.class);

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

    private final ConnectionFactory connectionFactory;
    private final Clock clock;
    private final VisibilityStatusSet visibilityStatusSet;
    private final EventProcessor<Event> eventProcessor;
    private final PuzzleStore puzzleStore;

    @Inject
    public HuntStatusStore(
        ConnectionFactory connectionFactory,
        HuntDefinition huntDefinition,
        EventProcessor<Event> eventProcessor,
        PuzzleStore puzzleStore
    ) {
        this(
                connectionFactory,
                Clock.systemUTC(),
                huntDefinition.getVisibilityStatusSet(),
                eventProcessor,
                puzzleStore
        );
    }

    public HuntStatusStore(
            ConnectionFactory connectionFactory,
            Clock clock,
            VisibilityStatusSet visibilityStatusSet,
            EventProcessor<Event> eventProcessor,
            PuzzleStore puzzleStore
    ) {
        this.connectionFactory = checkNotNull(connectionFactory);
        this.clock = checkNotNull(clock);
        this.visibilityStatusSet = checkNotNull(visibilityStatusSet);
        this.eventProcessor = checkNotNull(eventProcessor);
        this.puzzleStore = puzzleStore;
    }

    public VisibilityStatusSet getVisibilityStatusSet() {
        return this.visibilityStatusSet;
    }

    public Visibility getVisibility(String teamId, String puzzleId) {
        return getExplicitVisibility(teamId, puzzleId)
                .orElse(Visibility.builder()
                        .setTeamId(teamId)
                        .setPuzzleId(puzzleId)
                        .setStatus(visibilityStatusSet.getDefaultVisibilityStatus())
                        .build()
                );
    }

    public List<Visibility> getVisibilitiesForTeam(String teamId, List<String> puzzleIds) {
        List<Object> puzzleIdParameters = ImmutableList.copyOf(puzzleIds);
        String puzzleIdParameterPlaceholders = String.join(
                ",",
                Stream.generate(() -> "?").limit(puzzleIds.size()).collect(Collectors.toList()));

        String visibilitiesQuery = String.format(
                "SELECT " +
                "  ? AS teamId, " +
                "  puzzles.puzzleId AS puzzleId, " +
                "  CASE WHEN visibilities.status IS NOT NULL " +
                "    THEN visibilities.status " +
                "    ELSE ? " +
                "  END AS status " +
                "FROM puzzles " +
                "LEFT JOIN visibilities ON " +
                "  puzzles.puzzleId = visibilities.puzzleId AND visibilities.teamId = ? " +
                "WHERE puzzles.puzzleId in (%s)",
                puzzleIdParameterPlaceholders);
        List<Object> visibilitiesQueryParameters = ImmutableList.builder()
                .add(teamId)
                .add(visibilityStatusSet.getDefaultVisibilityStatus())
                .add(teamId)
                .addAll(puzzleIdParameters)
                .build();
        List<Visibility> visibilities = DatabaseHelper.query(
                connectionFactory,
                visibilitiesQuery,
                visibilitiesQueryParameters,
                Visibility.class
        );

        String submissionsQuery = String.format(
                "SELECT puzzleId, canonicalAnswer FROM submissions " +
                "WHERE teamId = ? AND canonicalAnswer IS NOT NULL AND puzzleId in (%s)",
                puzzleIdParameterPlaceholders);
        List<Object> submissionsQueryParameters = ImmutableList.builder()
                .add(teamId)
                .addAll(puzzleIdParameters)
                .build();
        List<Submission> submissions = DatabaseHelper.query(
                connectionFactory,
                submissionsQuery,
                submissionsQueryParameters,
                Submission.class
        );

        return fillInVisibilityAnswers(visibilities, submissions);
    }

    public List<Visibility> getVisibilitiesForTeam(String teamId) {
        List<Visibility> visibilities = DatabaseHelper.query(
                connectionFactory,
                "SELECT " +
                "  ? AS teamId, " +
                "  puzzles.puzzleId AS puzzleId, " +
                "  CASE WHEN visibilities.status IS NOT NULL " +
                "    THEN visibilities.status " +
                "    ELSE ? " +
                "  END AS status " +
                "FROM puzzles " +
                "LEFT JOIN visibilities ON " +
                "  puzzles.puzzleId = visibilities.puzzleId AND visibilities.teamId = ?",
                Lists.newArrayList(teamId, visibilityStatusSet.getDefaultVisibilityStatus(), teamId),
                Visibility.class
        );

        String submissionsQuery = "SELECT puzzleId, canonicalAnswer FROM submissions " +
                "WHERE teamId = ? AND canonicalAnswer IS NOT NULL";
        List<Submission> submissions = DatabaseHelper.query(
                connectionFactory,
                submissionsQuery,
                ImmutableList.of(teamId),
                Submission.class
        );

        return fillInVisibilityAnswers(visibilities, submissions);
    }

    private List<Visibility> fillInVisibilityAnswers(
            List<Visibility> visibilities,
            List<Submission> submissions
    ) {
        ImmutableListMultimap<String, Submission> submissionIndex = Multimaps.index(
                submissions, Submission::getPuzzleId
        );

        Set<String> solvedPuzzlesMissingAnswers = visibilities.stream()
                .filter(visibility -> visibility.getStatus().equals("SOLVED"))
                .map(Visibility::getPuzzleId)
                .filter(puzzleId -> {
                    for (Submission submission : submissionIndex.get(puzzleId)) {
                        if (submission.getCanonicalAnswer() != null && !submission.getCanonicalAnswer().isEmpty()) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toSet());
        final Optional<Map<String, Puzzle>> puzzleIndex = solvedPuzzlesMissingAnswers.isEmpty()
                ? Optional.empty()
                : Optional.of(puzzleStore.getPuzzles(solvedPuzzlesMissingAnswers));

        visibilities = visibilities.stream().map(visibility -> {
            List<Submission> visibilitySubmissions = submissionIndex.get(visibility.getPuzzleId());
            if (!visibilitySubmissions.isEmpty()) {
                List<String> solvedAnswers = ImmutableList.copyOf(visibilitySubmissions.stream()
                        .map(Submission::getCanonicalAnswer)
                        .collect(Collectors.toSet()));
                return visibility.toBuilder()
                        .setSolvedAnswers(solvedAnswers)
                        .build();
            } else if (puzzleIndex.isPresent()) {
                Puzzle puzzle = puzzleIndex.get().get(visibility.getPuzzleId());
                if (puzzle != null) {
                    List<Answer> answers = puzzle.getAnswers();
                    if (answers != null) {
                        return visibility.toBuilder()
                                .setSolvedAnswers(answers.stream()
                                        .map(Answer::getCanonicalAnswer)
                                        .collect(Collectors.toList()))
                                .build();
                    }
                }
            }
            return visibility;
        }).collect(Collectors.toList());

        return visibilities;
    }

    public boolean recordHuntRunStart() {
        Integer updates = DatabaseHelper.update(
                connectionFactory,
                "UPDATE run SET startTimestamp = ? WHERE startTimestamp IS NULL",
                Lists.newArrayList(Timestamp.from(clock.instant()))
        );
        return updates > 0;
    }

    public Optional<Run> getHuntRunProperties() {
        List<Run> runs = DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM run",
                Lists.newArrayList(),
                Run.class
        );

        if (runs.size() == 1) {
            return Optional.of(runs.get(0));
        } else {
            return Optional.empty();
        }
    }

    public Set<String> getTeamIds() {
        List<Team> teams = DatabaseHelper.query(
                connectionFactory,
                "SELECT teamId FROM teams",
                Lists.newArrayList(),
                Team.class
        );

        return teams.stream().map(Team::getTeamId).collect(Collectors.toSet());
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntStatusStore_TeamPropertiesRow.Builder.class)
    abstract static class TeamPropertiesRow {
        @AutoValue.Builder
        abstract static class Builder {
            @JsonProperty("teamId") abstract Builder setTeamId(String teamId);
            @JsonProperty("propertyKey") abstract Builder setPropertyKey(String propertyKey);
            @JsonProperty("propertyValue") abstract Builder setPropertyValue(String propertyValue);
            abstract TeamPropertiesRow build();
        }
        @JsonProperty("teamId") abstract String getTeamId();
        @JsonProperty("propertyKey") abstract String getPropertyKey();
        @JsonProperty("propertyValue") abstract String getPropertyValue();
    }

    private Map<String, Map<String, Team.Property>> deserializeTeamProperties(
            List<TeamPropertiesRow> teamPropertiesResults
    ) {
        Map<String, Map<String, Team.Property>> allTeamProperties = new HashMap<>();
        for (TeamPropertiesRow row : teamPropertiesResults) {
            Map<String, Team.Property> teamProperties = allTeamProperties.get(row.getTeamId());
            if (teamProperties == null) {
                teamProperties = new HashMap<>();
                allTeamProperties.put(row.getTeamId(), teamProperties);
            }

            Class<? extends Team.Property> propertyClass = Team.Property.getClass(row.getPropertyKey());
            if (propertyClass == null) {
                throw new RuntimeException(String.format("Unknown team property class '%s'", row.getPropertyKey()));
            }
            try {
                Team.Property property = OBJECT_MAPPER.readValue(row.getPropertyValue(), propertyClass);
                teamProperties.put(row.getPropertyKey(), property);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return allTeamProperties;
    }

    public Team getTeam(String teamId) {
        List<TeamPropertiesRow> teamPropertiesResults = DatabaseHelper.query(
                connectionFactory,
                "SELECT teamId, propertyKey, propertyValue FROM team_properties " +
                        "WHERE teamId = ?",
                Lists.newArrayList(teamId),
                TeamPropertiesRow.class
        );
        Map<String, Team.Property> teamProperties =
                deserializeTeamProperties(teamPropertiesResults).get(teamId);

        List<Team> teams = DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM teams WHERE teamId = ?",
                Lists.newArrayList(teamId),
                Team.class
        );

        Team team;
        try {
            team = Iterables.getOnlyElement(teams);
        } catch (NoSuchElementException e) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_NOT_FOUND.getCode(),
                    e,
                    "Failed to get team");
        }

        return team.toBuilder()
                .setTeamProperties(teamProperties)
                .build();
    }

    public List<Team> getTeams() {
        List<TeamPropertiesRow> teamPropertiesResults = DatabaseHelper.query(
                connectionFactory,
                "SELECT teamId, propertyKey, propertyValue FROM team_properties",
                ImmutableList.of(),
                TeamPropertiesRow.class
        );
        Map<String, Map<String, Team.Property>> allTeamProperties =
                deserializeTeamProperties(teamPropertiesResults);

        List<Team> teams = DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM teams",
                ImmutableList.of(),
                Team.class
        );

        return teams.stream()
                .map(team -> team.toBuilder().setTeamProperties(
                        allTeamProperties.get(team.getTeamId())).build())
                .collect(Collectors.toList());
    }

    public boolean setTeamProperty(
            String teamId,
            Class<? extends Team.Property> propertyClass,
            Team.Property property) {
        String propertyKey = propertyClass.getSimpleName();
        Preconditions.checkArgument(
                propertyClass.isInstance(property),
                "Team property is not an instance of %s",
                propertyKey);
        String propertyValue;
        try {
            propertyValue = OBJECT_MAPPER.writeValueAsString(property);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Optional<Integer> generatedId = DatabaseHelper.insert(
                connectionFactory,
                "INSERT INTO team_properties (teamId, propertyKey, propertyValue) SELECT ?, ?, ? " +
                        "WHERE NOT EXISTS (SELECT 1 FROM team_properties WHERE teamId = ? AND propertyKey = ?)",
                Lists.newArrayList(teamId, propertyKey, propertyValue, teamId, propertyKey));
        if (generatedId.isPresent()) {
            return true;
        }

        int updates = DatabaseHelper.update(
                connectionFactory,
                "UPDATE team_properties SET propertyValue = ? " +
                        "WHERE teamId = ? AND propertyKey = ?",
                Lists.newArrayList(propertyValue, teamId, propertyKey)
        );
        return updates > 0;
    }

    public <P extends Team.Property> P getTeamProperty(
            String teamId,
            Class<P> propertyClass) {
        String propertyKey = propertyClass.getSimpleName();
        return DatabaseHelper.retry(() -> {
            try (
                Connection connection = connectionFactory.getConnection();
                PreparedStatement getPropertyStatement = connection.prepareStatement(
                        "SELECT propertyValue FROM team_properties WHERE teamId = ? AND propertyKey = ?");
            ) {
                getPropertyStatement.setString(1, teamId);
                getPropertyStatement.setString(2, propertyKey);
                ResultSet resultSet = getPropertyStatement.executeQuery();
                if (!resultSet.next()) {
                    throw new RuntimeException("failed to read team property from database");
                }
                return OBJECT_MAPPER.readValue(resultSet.getString(1), propertyClass);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public <P extends Team.Property> boolean mutateTeamProperty(
            String teamId,
            Class<P> propertyClass,
            Function<P, P> mutator) {
        String propertyKey = propertyClass.getSimpleName();
        return DatabaseHelper.retry(() -> {
            try (
                    Connection connection = connectionFactory.getConnection();
                    PreparedStatement getPropertyStatement = connection.prepareStatement(
                            "SELECT propertyValue FROM team_properties WHERE teamId = ? AND propertyKey = ?");
                    PreparedStatement updatePropertyStatement = connection.prepareStatement(
                            "UPDATE team_properties SET propertyValue = ? WHERE teamId = ? AND propertyKey = ?")
            ) {
                connection.setAutoCommit(false);

                getPropertyStatement.setString(1, teamId);
                getPropertyStatement.setString(2, propertyKey);
                ResultSet resultSet = getPropertyStatement.executeQuery();
                if (!resultSet.next()) {
                    throw new RuntimeException("failed to read team property from database");
                }
                P property = OBJECT_MAPPER.readValue(resultSet.getString(1), propertyClass);

                P mutatedProperty = mutator.apply(property);
                String mutatedPropertyJson = OBJECT_MAPPER.writeValueAsString(mutatedProperty);

                updatePropertyStatement.setString(1, mutatedPropertyJson);
                updatePropertyStatement.setString(2, teamId);
                updatePropertyStatement.setString(3, propertyKey);
                boolean updated = updatePropertyStatement.executeUpdate() > 0;

                connection.commit();

                return updated;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void addTeam(Team team) {
        try {
            DatabaseHelper.retry(() -> {
                try (
                        Connection connection = connectionFactory.getConnection();
                        PreparedStatement insertTeamStatement = connection.prepareStatement(
                                "INSERT INTO teams (teamId, teamName, email, headquarters, primaryPhone, secondaryPhone) VALUES (?,?,?,?,?,?)")
                ) {
                    insertTeamStatement.setString(1, team.getTeamId());
                    insertTeamStatement.setString(2, team.getTeamName());
                    insertTeamStatement.setString(3, team.getEmail());
                    insertTeamStatement.setString(4, team.getHeadquarters());
                    insertTeamStatement.setString(5, team.getPrimaryPhone());
                    insertTeamStatement.setString(6, team.getSecondaryPhone());
                    insertTeamStatement.executeUpdate();
                }
                return null;
            });
        } catch (SQLRetryException e) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST.getCode(),
                    e.getException(),
                    "Failed to add team to the database");
        }
    }

    public boolean updateTeam(Team team) {
        try {
            return DatabaseHelper.retry(() -> {
                try (
                        Connection connection = connectionFactory.getConnection();
                        PreparedStatement insertTeamStatement = connection.prepareStatement(
                                "UPDATE teams SET email = ?, teamName = ?, headquarters = ?, primaryPhone = ?, secondaryPhone = ? " +
                                "WHERE teamId = ?")
                ) {
                    insertTeamStatement.setString(1, team.getEmail());
                    insertTeamStatement.setString(2, team.getTeamName());
                    insertTeamStatement.setString(3, team.getHeadquarters());
                    insertTeamStatement.setString(4, team.getPrimaryPhone());
                    insertTeamStatement.setString(5, team.getSecondaryPhone());
                    insertTeamStatement.setString(6, team.getTeamId());
                    return insertTeamStatement.executeUpdate() > 0;
                }
            });
        } catch (SQLRetryException e) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_BAD_REQUEST.getCode(),
                    e.getException(),
                    "Failed to update team in the database");
        }
    }

    private Optional<Visibility> getExplicitVisibility(String teamId, String puzzleId) {
        List<Visibility> visibilities = DatabaseHelper.query(
                connectionFactory,
                "SELECT teamId, puzzleId, status " +
                "FROM visibilities " +
                "WHERE teamId = ? AND puzzleId = ?",
                Lists.newArrayList(teamId, puzzleId),
                Visibility.class
        );
        if (visibilities.isEmpty()) {
            return Optional.empty();
        }
        Visibility visibility = Iterables.getOnlyElement(visibilities);

        Set<String> solvedAnswers;

        String submissionsQuery = "SELECT canonicalAnswer FROM submissions " +
                "WHERE teamId = ? AND puzzleId = ? AND canonicalAnswer IS NOT NULL";
        List<Submission> submissions = DatabaseHelper.query(
                connectionFactory,
                submissionsQuery,
                ImmutableList.of(teamId, puzzleId),
                Submission.class
        );
        solvedAnswers = submissions.stream()
                .map(Submission::getCanonicalAnswer)
                .collect(Collectors.toSet());

        if (visibility.getStatus().equals("SOLVED") && solvedAnswers.isEmpty()) {
            Puzzle puzzle = puzzleStore.getPuzzle(puzzleId);
            List<Answer> answers = puzzle.getAnswers();
            if (answers != null) {
                solvedAnswers.addAll(answers.stream()
                        .map(Answer::getCanonicalAnswer)
                        .collect(Collectors.toSet()));
            }
        }

        return Optional.of(visibility.toBuilder()
                .setSolvedAnswers(ImmutableList.copyOf(solvedAnswers))
                .build());
    }

    private boolean createExplicitDefaultVisibility(String teamId, String puzzleId) {
        Optional<Integer> generatedId = DatabaseHelper.insert(
                connectionFactory,
                "INSERT INTO visibilities (teamId, puzzleId) SELECT ?, ? " +
                        "WHERE NOT EXISTS (SELECT 1 FROM visibilities WHERE teamId = ? AND puzzleId = ?)",
                Lists.newArrayList(teamId, puzzleId, teamId, puzzleId));
        return generatedId.isPresent();
    }

    private void createExplicitDefaultVisibilities(Multimap<String,String> teamToPuzzles) {
        List<List<Object>> parametersList = teamToPuzzles.entries().stream()
                .map(entry -> Lists.<Object>newArrayList(
                        entry.getKey(), entry.getValue(), entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        DatabaseHelper.insertBatch(
                connectionFactory,
                "INSERT INTO visibilities (teamId, puzzleId) SELECT ?, ? " +
                        "WHERE NOT EXISTS (SELECT 1 FROM visibilities WHERE teamId = ? AND puzzleId = ?)",
                parametersList
        );
    }

    public boolean setVisibility(
            String teamId,
            String puzzleId,
            String status
    ) {
        return internalSetVisibilityBatch(ImmutableTable.of(teamId, puzzleId, status), true);
    }

    public boolean setVisibilityWithoutWorkflowValidation(
            String teamId,
            String puzzleId,
            String status
    ) {
        return internalSetVisibilityBatch(ImmutableTable.of(teamId, puzzleId, status), false);
    }

    public boolean setVisibilityBatch(
            Table<String,String,String> teamPuzzleStatusTable
    ) {
        return internalSetVisibilityBatch(teamPuzzleStatusTable, true);
    }

    public boolean setVisibilityBatchWithoutWorkflowValidation(
            Table<String,String,String> teamPuzzleStatusTable
    ) {
        return internalSetVisibilityBatch(teamPuzzleStatusTable, false);
    }

    private boolean internalSetVisibilityBatch(
            Table<String,String,String> teamPuzzleStatusTable,
            boolean useWorkflowValidation
    ) {
        Set<String> statuses = Sets.newHashSet(teamPuzzleStatusTable.values());

        Set<String> disallowedStatuses = Sets.filter(statuses, status -> !visibilityStatusSet.isAllowedStatus(status));
        if (!disallowedStatuses.isEmpty()) {
            LOGGER.error("Attempted to set visibilities to invalid status(es): " + Joiner.on(", ").join(disallowedStatuses));
            statuses.removeAll(disallowedStatuses);
        }

        if (useWorkflowValidation) {
            Set<String> unsettableStatuses = Sets.filter(statuses, status -> visibilityStatusSet.getAllowedAntecedents(status).isEmpty());
            if (!unsettableStatuses.isEmpty()) {
                LOGGER.warn("Attempted to set visibilities to unsettable status(es): " + Joiner.on(", ").join(unsettableStatuses));
                statuses.removeAll(unsettableStatuses);
            }
        }

        List<Visibility> updatedVisibilities = Lists.newArrayList();

        for (String status : statuses) {
            Multimap<String,String> teamToPuzzles = HashMultimap.create();
            teamPuzzleStatusTable.cellSet().stream()
                    .filter(cell -> cell.getValue().equals(status))
                    .forEach(cell -> teamToPuzzles.put(cell.getRowKey(), cell.getColumnKey()));
            createExplicitDefaultVisibilities(teamToPuzzles);

            String preparedUpdateSql = "UPDATE visibilities SET status = ? " +
                    "WHERE teamId = ? AND puzzleId = ?";
            Stream<ImmutableList.Builder<Object>> builderStream = teamToPuzzles.entries().stream()
                    .map(entry -> new ImmutableList.Builder<Object>()
                            .add(status)
                            .add(entry.getKey())
                            .add(entry.getValue()));
            if (useWorkflowValidation) {
                Set<String> allowedCurrentStatuses = visibilityStatusSet.getAllowedAntecedents(status);
                preparedUpdateSql += " AND (" +
                        Joiner.on(" OR ").join(allowedCurrentStatuses.stream()
                                .map(s -> "status = ?")
                                .collect(Collectors.toList())) +
                        ")";
                builderStream = builderStream.map(builder -> builder.addAll(allowedCurrentStatuses));
            }

            List<List<Object>> parametersList = builderStream
                    .map(ImmutableList.Builder::build)
                    .collect(Collectors.toList());

            List<Integer> updateCounts = DatabaseHelper.updateBatch(
                    connectionFactory,
                    preparedUpdateSql,
                    parametersList
            );

            List<Visibility> updatedVisibilitiesForStatus = IntStream.range(0, parametersList.size())
                    .filter(index -> updateCounts.get(index) > 0)
                    .mapToObj(index -> Visibility.builder()
                            .setStatus((String) parametersList.get(index).get(0))
                            .setTeamId((String) parametersList.get(index).get(1))
                            .setPuzzleId((String) parametersList.get(index).get(2))
                            .build())
                    .collect(Collectors.toList());

            updatedVisibilities.addAll(updatedVisibilitiesForStatus);
        }

        Timestamp timestamp = Timestamp.from(clock.instant());
        DatabaseHelper.insertBatch(
                connectionFactory,
                "INSERT INTO visibility_history (teamId, puzzleId, status, timestamp) VALUES (?, ?, ?, ?)",
                updatedVisibilities.stream()
                        .map(v -> Lists.<Object>newArrayList(
                                v.getTeamId(),
                                v.getPuzzleId(),
                                v.getStatus(),
                                timestamp
                        )).collect(Collectors.toList())
        );

        List<VisibilityChangeEvent> changeEvents = updatedVisibilities.stream()
                .map(v -> VisibilityChangeEvent.builder().setVisibility(v).build())
                .collect(Collectors.toList());

        if (!changeEvents.isEmpty()) {
            eventProcessor.processBatch(changeEvents);
        }
        return !changeEvents.isEmpty();
    }


    public List<VisibilityChange> getVisibilityHistory(String teamId, String puzzleId) {
        return DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM visibility_history WHERE " +
                        "teamId = ? AND puzzleId = ? ORDER BY timestamp ASC",
                Lists.newArrayList(teamId, puzzleId),
                VisibilityChange.class
        );
    }

    public List<VisibilityChange> getTeamVisibilityHistory(String teamId) {
        return DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM visibility_history WHERE " +
                        "teamId = ? ORDER BY visibilityHistoryId",
                Lists.newArrayList(teamId),
                VisibilityChange.class
        );
    }

    // TODO: introduce some filtering and/or pagination on this API - always reading all
    // visibility changes may not scale.
    public List<VisibilityChange> getVisibilityChanges() {
        return DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM visibility_history",
                ImmutableList.<Object>of(),
                VisibilityChange.class
        );
    }
}
