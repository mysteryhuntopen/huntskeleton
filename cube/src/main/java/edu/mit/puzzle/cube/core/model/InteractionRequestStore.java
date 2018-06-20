package edu.mit.puzzle.cube.core.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import edu.mit.puzzle.cube.core.db.ConnectionFactory;
import edu.mit.puzzle.cube.core.db.DatabaseHelper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class InteractionRequestStore {
    private final ConnectionFactory connectionFactory;
    private final Clock clock;

    @Inject
    public InteractionRequestStore(
            ConnectionFactory connectionFactory
    ) {
        this.connectionFactory = connectionFactory;
        this.clock = Clock.systemUTC();
    }

    public boolean createInteractionRequest(InteractionRequest interactionRequest) {
        return DatabaseHelper.insert(
                connectionFactory,
                "INSERT INTO interaction_requests (puzzleId, teamId, invisible, request, timestamp) " +
                        "VALUES (?,?,?,?,?)",
                Lists.newArrayList(
                        interactionRequest.getPuzzleId(),
                        interactionRequest.getTeamId(),
                        interactionRequest.getInvisible().toString(),
                        interactionRequest.getRequest(),
                        Timestamp.from(clock.instant()))
        ).isPresent();
    }

    public boolean updateInteractionRequest(
            int interactionRequestId,
            InteractionRequestStatus status,
            @Nullable String callerUsername,
            @Nullable String response
    ) {
        boolean updated = DatabaseHelper.update(
                connectionFactory,
                "UPDATE interaction_requests SET status = ?, callerUsername = ?, response = ? " +
                "WHERE interactionRequestId = ? AND (status <> ? OR callerUsername <> ? OR response <> ?)",
                Lists.newArrayList(
                        status.toString(),
                        callerUsername,
                        response,
                        interactionRequestId,
                        status.toString(),
                        callerUsername,
                        response
                )
        ) > 0;

        return updated;
    }

    public Optional<InteractionRequest> getInteractionRequest(int interactionRequestId) {
        List<InteractionRequest> interactionRequests = DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM interaction_requests WHERE interactionRequestId = ?",
                Lists.newArrayList(interactionRequestId),
                InteractionRequest.class
        );

        if (interactionRequests.size() == 0) {
            return Optional.empty();
        } else if (interactionRequests.size() > 1) {
            throw new RuntimeException("Primary key violation in application layer");
        }

        return Optional.of(interactionRequests.get(0));
    }

    public List<InteractionRequest> getNonTerminalInteractionRequests() {
        return DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM interaction_requests WHERE status <> 'COMPLETE'",
                ImmutableList.of(),
                InteractionRequest.class
        );
    }

    public List<InteractionRequest> getInteractionRequestsForTeamAndPuzzle(String teamId, String puzzleId) {
        return DatabaseHelper.query(
                connectionFactory,
                "SELECT * FROM interaction_requests WHERE teamId = ? AND puzzleId = ?",
                ImmutableList.of(teamId, puzzleId),
                InteractionRequest.class
        );
    }
}
