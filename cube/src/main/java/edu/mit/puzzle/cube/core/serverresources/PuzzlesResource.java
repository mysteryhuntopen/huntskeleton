package edu.mit.puzzle.cube.core.serverresources;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.Puzzles;
import edu.mit.puzzle.cube.core.model.Visibility;
import edu.mit.puzzle.cube.core.permissions.AnswersPermission;
import edu.mit.puzzle.cube.core.permissions.PermissionAction;
import edu.mit.puzzle.cube.core.permissions.TeamsPermission;

import org.apache.shiro.subject.Subject;
import org.restlet.resource.Get;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import java.lang.System;

public class PuzzlesResource extends AbstractCubeResource {

    @Get
    public Puzzles handleGet() {
        Subject subject = getSubject();
        Optional<String> teamId = Optional.ofNullable(getQueryValue("teamId"));
        Optional<String> puzzleId = Optional.ofNullable(getQueryValue("puzzleId"));
        Optional<String> siteMode = Optional.ofNullable(getQueryValue("siteMode"));

        if (teamId.isPresent()) {
            subject.checkPermission(
                    new TeamsPermission(teamId.get(), PermissionAction.READ));

            Map<String, Puzzle> unfilteredPuzzles;
            Map<String, Visibility> retrievedVisibilities;
            if (!puzzleId.isPresent()) {
                unfilteredPuzzles = puzzleStore.getPuzzles();
                retrievedVisibilities = Maps.uniqueIndex(
                        huntStatusStore.getVisibilitiesForTeam(teamId.get()),
                        Visibility::getPuzzleId
                );
            } else {
                List<String> puzzleIds = Splitter.on(",").splitToList(puzzleId.get());
                puzzleIds = puzzleStore.getCanonicalPuzzleIds(puzzleIds);
                unfilteredPuzzles = puzzleStore.getPuzzles(puzzleIds);
                retrievedVisibilities = Maps.uniqueIndex(
                        huntStatusStore.getVisibilitiesForTeam(teamId.get(), puzzleIds),
                        Visibility::getPuzzleId
                );
            }

            // For writingteam and admin, they are allowed to see all puzzles, even those that they haven't "unlocked" (whatever that means)
            // If the site is in solution mode, they are also allowed to see all puzzles
            if (subject.isPermitted(new AnswersPermission()) || siteMode.equals(Optional.of("solution"))) {
                return Puzzles.builder()
                        .setPuzzles(unfilteredPuzzles.values().stream().collect(Collectors.toList()))
                        .build();
            }

            final Set<String> invisibleStatuses = huntStatusStore.getVisibilityStatusSet().getInvisibleStatuses();
            List<Puzzle> puzzles = unfilteredPuzzles.entrySet().stream()
                    .filter(entry -> {
                        Visibility visibility = retrievedVisibilities.get(entry.getKey());
                        if (visibility == null) {
                            return false;
                        }
                        return !invisibleStatuses.contains(visibility.getStatus());
                    })
                    .map(entry -> entry.getValue().strip(subject, retrievedVisibilities.get(entry.getKey()), siteMode))
                    .collect(Collectors.toList());
            return Puzzles.builder()
                    .setPuzzles(puzzles)
                    .build();
        } else {
            subject.checkPermission(
                    new TeamsPermission("*", PermissionAction.READ));

            Map<String, Puzzle> unfilteredPuzzles = puzzleStore.getPuzzles();
            return Puzzles.builder()
                    .setPuzzles(ImmutableList.copyOf(unfilteredPuzzles.values()))
                    .build();
        }

    }

}
