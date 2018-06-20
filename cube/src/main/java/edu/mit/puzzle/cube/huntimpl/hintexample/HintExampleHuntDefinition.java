package edu.mit.puzzle.cube.huntimpl.hintexample;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.events.FullReleaseEvent;
import edu.mit.puzzle.cube.core.events.HintCompleteEvent;
import edu.mit.puzzle.cube.core.events.HuntStartEvent;
import edu.mit.puzzle.cube.core.events.SubmissionCompleteEvent;
import edu.mit.puzzle.cube.core.events.VisibilityChangeEvent;
import edu.mit.puzzle.cube.core.model.HintRequest;
import edu.mit.puzzle.cube.core.model.HintRequestStatus;
import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.Submission;
import edu.mit.puzzle.cube.core.model.SubmissionStatus;
import edu.mit.puzzle.cube.core.model.Team;
import edu.mit.puzzle.cube.core.model.VisibilityStatusSet;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class HintExampleHuntDefinition extends HuntDefinition {
    private static final VisibilityStatusSet VISIBILITY_STATUS_SET = new StandardVisibilityStatusSet();

    // The number of hint tokens currently held by a solving team.
    @AutoValue
    public abstract static class HintTokensProperty extends Team.Property {
        static {
            registerClass(HintTokensProperty.class);
        }

        @JsonCreator
        public static HintTokensProperty create(@JsonProperty("tokens") int tokens) {
            return new AutoValue_HintExampleHuntDefinition_HintTokensProperty(tokens);
        }

        @JsonProperty("tokens") public abstract int getTokens();
    }

    // Determines whether hint requests are allowed for a puzzle.
    @AutoValue
    public abstract static class HintAllowedProperty extends Puzzle.Property {
        static {
            registerClass(HintAllowedProperty.class);
        }

        @JsonCreator
        public static HintAllowedProperty create(@JsonProperty("hintAllowed") boolean hintAllowed) {
            return new AutoValue_HintExampleHuntDefinition_HintAllowedProperty(hintAllowed);
        }

        @JsonProperty("hintAllowed") public abstract boolean getHintAllowed();

        @Override
        public Set<String> getVisibilityRequirement() {
            return ImmutableSet.of("VISIBLE", "UNLOCKED", "SOLVED");
        }
    }

    // The number of hint tokens rewarded when a puzzle is solved.
    @AutoValue
    public abstract static class TokenRewardProperty extends Puzzle.Property {
        static {
            registerClass(TokenRewardProperty.class);
        }

        @JsonCreator
        public static TokenRewardProperty create(@JsonProperty("tokens") int tokens) {
            return new AutoValue_HintExampleHuntDefinition_TokenRewardProperty(tokens);
        }

        @JsonProperty("tokens") public abstract int getTokens();

        // Only reveal the token reward for a puzzle after that puzzle is solved.
        @Override
        public Set<String> getVisibilityRequirement() {
            return ImmutableSet.of("SOLVED");
        }
    }

    @Override
    public VisibilityStatusSet getVisibilityStatusSet() {
        return VISIBILITY_STATUS_SET;
    }

    @Override
    public List<Puzzle> getPuzzles() {
        return ImmutableList.of(
                Puzzle.builder("puzzle1", "ANSWER1")
                        .addPuzzleProperty(HintAllowedProperty.class, HintAllowedProperty.create(true))
                        .build(),
                Puzzle.builder("puzzle2", "ANSWER2")
                        .addPuzzleProperty(HintAllowedProperty.class, HintAllowedProperty.create(true))
                        .addPuzzleProperty(TokenRewardProperty.class, TokenRewardProperty.create(1))
                        .build(),
                Puzzle.builder("puzzle3", "ANSWER3")
                        .addPuzzleProperty(HintAllowedProperty.class, HintAllowedProperty.create(true))
                        .build(),
                Puzzle.builder("meta", "ANSWERMETA")
                        .addPuzzleProperty(HintAllowedProperty.class, HintAllowedProperty.create(false))
                        .build()
        );
    }

    @Override
    public void addToEventProcessor() {
        eventProcessor.addEventProcessor(HuntStartEvent.class, event -> {
            boolean changed = huntStatusStore.recordHuntRunStart();

            if (changed) {
                for (String teamId : huntStatusStore.getTeamIds()) {
                    huntStatusStore.setVisibility(teamId, "puzzle1", "UNLOCKED");
                    huntStatusStore.setVisibility(teamId, "puzzle2", "VISIBLE");
                    huntStatusStore.setVisibility(teamId, "meta", "VISIBLE");

                    // Everyone starts with one hint token.
                    huntStatusStore.setTeamProperty(
                            teamId,
                            HintTokensProperty.class,
                            HintTokensProperty.create(1));
                }
            }
        });

        eventProcessor.addEventProcessor(SubmissionCompleteEvent.class, event -> {
            Submission submission = event.getSubmission();
            if (submission.getStatus().equals(SubmissionStatus.CORRECT)) {
                huntStatusStore.setVisibility(
                        submission.getTeamId(),
                        submission.getPuzzleId(),
                        "SOLVED"
                );
                Puzzle puzzle = puzzleStore.getPuzzle(submission.getPuzzleId());
                TokenRewardProperty tokenRewardProperty = puzzle.getPuzzleProperty(TokenRewardProperty.class);
                if (tokenRewardProperty != null) {
                    huntStatusStore.mutateTeamProperty(
                            submission.getTeamId(),
                            HintTokensProperty.class,
                            hintTokensProperty -> HintTokensProperty.create(
                                    hintTokensProperty.getTokens() + tokenRewardProperty.getTokens())
                    );
                }
            }
        });

        eventProcessor.addEventProcessor(VisibilityChangeEvent.class, event -> {
            String teamId = event.getVisibility().getTeamId();
            String puzzleId = event.getVisibility().getPuzzleId();
            String status = event.getVisibility().getStatus();

            if (status.equals("SOLVED")) {
                if (puzzleId.startsWith("puzzle")) {
                    // We'll unlock the meta after a team solves at least one round puzzle.
                    huntStatusStore.setVisibility(teamId, "meta", "UNLOCKED");
                }
                if (puzzleId.equals("puzzle1")) {
                    huntStatusStore.setVisibility(teamId, "puzzle2", "UNLOCKED");
                    huntStatusStore.setVisibility(teamId, "puzzle3", "VISIBLE");
                }
                if (puzzleId.equals("puzzle2")) {
                    huntStatusStore.setVisibility(teamId, "puzzle3", "UNLOCKED");
                }
            }
        });

        eventProcessor.addEventProcessor(FullReleaseEvent.class, event -> {
            for (String teamId : huntStatusStore.getTeamIds()) {
                huntStatusStore.setVisibility(
                        teamId,
                        event.getPuzzleId(),
                        "UNLOCKED"
                );
            }
        });

        eventProcessor.addEventProcessor(HintCompleteEvent.class, event -> {
            String teamId = event.getHintRequest().getTeamId();
            HintRequestStatus hintRequestStatus = event.getHintRequest().getStatus();
            if (hintRequestStatus == HintRequestStatus.REJECTED) {
                // Refund the team their token back.
                huntStatusStore.mutateTeamProperty(
                        teamId,
                        HintTokensProperty.class,
                        hintTokensProperty -> HintTokensProperty.create(hintTokensProperty.getTokens() + 1)
                );
            }
        });
    }

    @Override
    public boolean handleHintRequest(HintRequest hintRequest) {
        Puzzle puzzle = puzzleStore.getPuzzle(hintRequest.getPuzzleId());
        HintAllowedProperty hintAllowedProperty = puzzle.getPuzzleProperty(HintAllowedProperty.class);
        if (!hintAllowedProperty.getHintAllowed()) {
            return false;
        }
        AtomicBoolean deductedToken = new AtomicBoolean(false);
        huntStatusStore.mutateTeamProperty(
                hintRequest.getTeamId(),
                HintTokensProperty.class,
                hintTokensProperty -> {
                    if (hintTokensProperty.getTokens() > 0) {
                        deductedToken.set(true);
                        return HintTokensProperty.create(hintTokensProperty.getTokens() - 1);
                    }
                    return hintTokensProperty;
                }
        );
        return deductedToken.get();
    }
}
