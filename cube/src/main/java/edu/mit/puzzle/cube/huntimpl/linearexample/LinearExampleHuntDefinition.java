package edu.mit.puzzle.cube.huntimpl.linearexample;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;

import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.events.FullReleaseEvent;
import edu.mit.puzzle.cube.core.events.HuntStartEvent;
import edu.mit.puzzle.cube.core.events.SubmissionCompleteEvent;
import edu.mit.puzzle.cube.core.events.VisibilityChangeEvent;
import edu.mit.puzzle.cube.core.model.Answer;
import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.Puzzle.AnswersProperty;
import edu.mit.puzzle.cube.core.model.Puzzle.DisplayNameProperty;
import edu.mit.puzzle.cube.core.model.Submission;
import edu.mit.puzzle.cube.core.model.SubmissionStatus;
import edu.mit.puzzle.cube.core.model.VisibilityStatusSet;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;

import java.util.List;
import java.util.Map;

public class LinearExampleHuntDefinition extends HuntDefinition {

    private static final VisibilityStatusSet VISIBILITY_STATUS_SET = new StandardVisibilityStatusSet();

    @Override
    public VisibilityStatusSet getVisibilityStatusSet() {
        return VISIBILITY_STATUS_SET;
    }

    private static final List<Puzzle> PUZZLES;
    static {
        ImmutableList.Builder<Puzzle> puzzlesBuilder = ImmutableList.builder();
        for (int i = 1; i <= 7 ; ++i) {
            puzzlesBuilder.add(Puzzle.builder()
                    .setPuzzleId("puzzle" + i)
                    .addPuzzleProperty(
                            DisplayNameProperty.class,
                            DisplayNameProperty.create(
                                    "puzzle" + i,
                                    ImmutableSet.of("UNLOCKED","SOLVED")
                            )
                    )
                    .addPuzzleProperty(
                            AnswersProperty.class,
                            AnswersProperty.create(Answer.createSingle("ANSWER" + i))
                    )
                    .build()
            );
        }
        PUZZLES = puzzlesBuilder.build();
    }

    @Override
    public List<Puzzle> getPuzzles() {
        return PUZZLES;
    }

    private static final Map<String,String> DIRECT_UNLOCK_PREREQS;
    static {
        ImmutableMap.Builder<String,String> directPrereqBuilder = ImmutableMap.builder();
        for (int i = 1; i <= 6; ++i) {
            directPrereqBuilder.put("puzzle" + i, "puzzle" + (i+1));
        }
        DIRECT_UNLOCK_PREREQS = directPrereqBuilder.build();
    }
    private static final Map<String,String> DIRECT_VISIBLE_PREREQS;
    static {
        ImmutableMap.Builder<String,String> directPrereqBuilder = ImmutableMap.builder();
        for (int i = 1; i <= 5; ++i) {
            directPrereqBuilder.put("puzzle" + i, "puzzle" + (i+2));
        }
        DIRECT_VISIBLE_PREREQS = directPrereqBuilder.build();
    }

    @Override
    public void addToEventProcessor() {
        eventProcessor.addEventProcessor(SubmissionCompleteEvent.class, event -> {
            Submission submission = event.getSubmission();
            if (submission.getStatus().equals(SubmissionStatus.CORRECT)) {
                huntStatusStore.setVisibility(
                        submission.getTeamId(),
                        submission.getPuzzleId(),
                        "SOLVED"
                );
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

        eventProcessor.addEventProcessor(HuntStartEvent.class, event -> {
            boolean changed = huntStatusStore.recordHuntRunStart();
            if (changed) {
                ImmutableTable.Builder<String,String,String> visibilityUpdateBatchBuilder =
                        ImmutableTable.builder();
                huntStatusStore.getTeamIds().forEach(teamId ->
                        visibilityUpdateBatchBuilder.put(teamId, "puzzle1", "UNLOCKED"));
                huntStatusStore.getTeamIds().forEach(teamId ->
                        visibilityUpdateBatchBuilder.put(teamId, "puzzle2", "VISIBLE"));
                huntStatusStore.setVisibilityBatch(visibilityUpdateBatchBuilder.build());
            }
        });

        for (Map.Entry<String,String> directPrereqEntry : DIRECT_UNLOCK_PREREQS.entrySet()) {
            eventProcessor.addEventProcessor(VisibilityChangeEvent.class, event -> {
                String teamId = event.getVisibility().getTeamId();
                String puzzleId = event.getVisibility().getPuzzleId();
                String status = event.getVisibility().getStatus();

                if (status.equals("SOLVED") && puzzleId.equals(directPrereqEntry.getKey())) {
                    huntStatusStore.setVisibility(teamId, directPrereqEntry.getValue(),
                            "UNLOCKED");
                }
            });
        }

        for (Map.Entry<String,String> directVisibleEntry : DIRECT_VISIBLE_PREREQS.entrySet()) {
            eventProcessor.addEventProcessor(VisibilityChangeEvent.class, event -> {
                String teamId = event.getVisibility().getTeamId();
                String puzzleId = event.getVisibility().getPuzzleId();
                String status = event.getVisibility().getStatus();

                if (status.equals("SOLVED") && puzzleId.equals(directVisibleEntry.getKey())) {
                    huntStatusStore.setVisibility(teamId, directVisibleEntry.getValue(),
                            "VISIBLE");
                }
            });
        }
    }
}
