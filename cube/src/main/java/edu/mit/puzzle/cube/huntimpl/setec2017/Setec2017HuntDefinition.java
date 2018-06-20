package edu.mit.puzzle.cube.huntimpl.setec2017;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.google.common.collect.*;
import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.events.*;
import edu.mit.puzzle.cube.core.model.*;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class Setec2017HuntDefinition extends HuntDefinition {
    private static final Logger LOGGER = LoggerFactory.getLogger(Setec2017HuntDefinition.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final VisibilityStatusSet VISIBILITY_STATUS_SET = new StandardVisibilityStatusSet();

    // We need to force Java to load the puzzle and team property classes, to
    // ensure that their static initializers actually run and register the
    // property classes with Puzzle.Property and Team.Property. It would be nice
    // to come up with a cleaner solution for this, but this works for now.
    static {
        try {
            Class.forName("edu.mit.puzzle.cube.core.model.Puzzle$AnswersProperty");
            Class.forName("edu.mit.puzzle.cube.core.model.Puzzle$DisplayNameProperty");
            Class.forName("edu.mit.puzzle.cube.core.model.Puzzle$DisplayIdProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition$CharacterLevelsProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition$GoldProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition$InventoryProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition$SolveRewardProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition$VisibleConstraintProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.setec2017.Setec2017HuntDefinition$UnlockedConstraintProperty");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public VisibilityStatusSet getVisibilityStatusSet() {
        return VISIBILITY_STATUS_SET;
    }

    public enum Character {
        FIGHTER,
        WIZARD,
        CLERIC,
        LINGUIST,
        ECONOMIST,
        CHEMIST;
    }

    public enum InventoryItem {
        ITEM00,
        ITEM01,
        ITEM02,
        ITEM03,
        ITEM04,
        ITEM05,
        ITEM06,
        ITEM07,
        ITEM08,
        ITEM09,
        ITEM10,
        ITEM11,
        ITEM12,
        ITEM13,
        ITEM14,
        ITEM15,
        ITEM16,
        ITEM17,
        ITEM18,
        ITEM19,
        ITEM20,
        ITEM21,
        ITEM22,
        ITEM23,
        ITEM24,
        ITEM25,
        ITEM26,
        ITEM27,
        ITEM28,
        ITEM29,
        ITEM30,
        ITEM31,
        ITEM32,
        ITEM33,
        ITEM34,
        ITEM35,
        ITEM36,
        ITEM37,
        ITEM38,
        ITEM39;
    }

    static class Setec2017PuzzleBuilder {

        private String id;
        private String displayId;
        private String displayName;
        private String singleAnswer;
        private VisibilityConstraint visibleConstraint;
        private VisibilityConstraint unlockedConstraint;
        private SolveRewardProperty solveRewardProperty;

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");
        private static final VisibilityConstraint NO_CONSTRAINT = VisibilityConstraint.builder().build();
        private static final SolveRewardProperty NO_REWARD = SolveRewardProperty.builder().build();

        private Setec2017PuzzleBuilder() {
            this.visibleConstraint = NO_CONSTRAINT;
            this.unlockedConstraint = NO_CONSTRAINT;
            this.solveRewardProperty = NO_REWARD;
        }

        static Setec2017PuzzleBuilder builder() {
            return new Setec2017PuzzleBuilder();
        }

        Puzzle build() {
            checkNotNull(id);
            checkNotNull(singleAnswer);
            if (displayId == null) {
                displayId = id;
            }
            if (displayName == null) {
                displayName = id;
            }

            Puzzle.DisplayNameProperty displayNameProperty = Puzzle.DisplayNameProperty.create(
                    displayName, DISPLAY_PROPERTY_ACCESS_STATUSES);
            Puzzle.DisplayIdProperty displayIdProperty = Puzzle.DisplayIdProperty.create(
                    displayId, DISPLAY_PROPERTY_ACCESS_STATUSES);
            Puzzle.AnswersProperty answersProperty = Puzzle.AnswersProperty.create(ImmutableList.of(
                    Answer.create(singleAnswer)));

            return Puzzle.builder()
                    .setPuzzleId(id)
                    .addPuzzleProperty(Puzzle.DisplayIdProperty.class, displayIdProperty)
                    .addPuzzleProperty(Puzzle.DisplayNameProperty.class, displayNameProperty)
                    .addPuzzleProperty(Puzzle.AnswersProperty.class, answersProperty)
                    .addPuzzleProperty(VisibleConstraintProperty.class,
                        VisibleConstraintProperty.create(visibleConstraint))
                    .addPuzzleProperty(UnlockedConstraintProperty.class,
                        UnlockedConstraintProperty.create(unlockedConstraint))
                    .addPuzzleProperty(SolveRewardProperty.class, solveRewardProperty)
                    .build();
        }

        Setec2017PuzzleBuilder setId(String id) {
            this.id = id;
            return this;
        }

        Setec2017PuzzleBuilder setDisplayId(String displayId) {
            this.displayId = displayId;
            return this;
        }

        Setec2017PuzzleBuilder setDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        Setec2017PuzzleBuilder setSingleAnswer(String answer) {
            this.singleAnswer = answer;
            return this;
        }

        Setec2017PuzzleBuilder setVisibleConstraint(VisibilityConstraint visibilityConstraint) {
            this.visibleConstraint = visibilityConstraint;
            return this;
        }

        Setec2017PuzzleBuilder setUnlockedConstraint(VisibilityConstraint visibilityConstraint) {
            this.unlockedConstraint = visibilityConstraint;
            return this;
        }

        Setec2017PuzzleBuilder setSolveRewardProperty(SolveRewardProperty solveRewardProperty) {
            this.solveRewardProperty = solveRewardProperty;
            return this;
        }

    }

    @AutoValue
    public abstract static class CharacterLevelsProperty extends Team.Property {
        static {
            registerClass(CharacterLevelsProperty.class);
        }

        @JsonCreator
        public static CharacterLevelsProperty create(
                @JsonProperty("levels") ImmutableMap<Character, Integer> levels) {
            return new AutoValue_Setec2017HuntDefinition_CharacterLevelsProperty(levels);
        }

        @JsonProperty("levels")
        public abstract ImmutableMap<Character, Integer> getLevels();
    }

    @AutoValue
    public abstract static class GoldProperty extends Team.Property {
        static {
            registerClass(GoldProperty.class);
        }

        @JsonCreator
        public static GoldProperty create(@JsonProperty("gold") int gold) {
            return new AutoValue_Setec2017HuntDefinition_GoldProperty(gold);
        }

        @JsonProperty("gold") public abstract int getGold();
    }

    @AutoValue
    public abstract static class InventoryProperty extends Team.Property {
        static {
            registerClass(InventoryProperty.class);
        }

        @JsonCreator
        public static InventoryProperty create(
                @JsonProperty("inventoryItems") ImmutableSet<InventoryItem> inventoryItems) {
            return new AutoValue_Setec2017HuntDefinition_InventoryProperty(inventoryItems);
        }

        @JsonProperty("inventoryItems") public abstract ImmutableSet<InventoryItem> getInventoryItems();
    }


    @AutoValue
    @JsonDeserialize(builder = AutoValue_Setec2017HuntDefinition_VisibilityConstraint.Builder.class)
    protected abstract static class VisibilityConstraint {
        private static final ImmutableList<String> VISIBILITY_STATUS_ORDER = ImmutableList.of(
                "INVISIBLE", "VISIBLE", "UNLOCKED", "SOLVED");

        @AutoValue.Builder
        abstract static class Builder {
            // If true, then this visibility constraint will never be satisfied. All other
            // constraints in this object will be ignored.
            @JsonProperty("requiresManualAction") abstract Builder setRequiresManualAction(boolean requiresManualAction);

            // A sum constraint is satisfied when the sum of the levels of a set of characters is
            // greater than or equal to a number. All sum constraints must be satisfied for this
            // visibility constraint to be satisfied.
            abstract ImmutableList.Builder<CharacterLevelConstraint> sumConstraintsBuilder();

            // A max constraint is satisfied when the largest of the levels of a set of characters
            // is greater than or equal to a number. All max constraints must be satisfied for this
            // visibility constraint to be satisfied.
            abstract ImmutableList.Builder<CharacterLevelConstraint> maxConstraintsBuilder();

            // A puzzle visibility status constraint is satisfied when a puzzle has the given
            // visibility status, or has a visibility status that follows the given visibility
            // status in the visibility status order. All puzzle visibility constraints must be
            // satisfied for this visibility constraint to be satisfied.
            abstract ImmutableMap.Builder<String, String> puzzleVisibilityStatusConstraintsBuilder();

            @JsonProperty("sumConstraints") abstract Builder setSumConstraints(List<CharacterLevelConstraint> sumConstraints);
            Builder addSumConstraint(int levels, Character... characters) {
                sumConstraintsBuilder().add(CharacterLevelConstraint.create(levels, characters));
                return this;
            }

            @JsonProperty("maxConstraints") abstract Builder setMaxConstraints(List<CharacterLevelConstraint> maxConstraints);
            Builder addMaxConstraint(int levels, Character... characters) {
                maxConstraintsBuilder().add(CharacterLevelConstraint.create(levels, characters));
                return this;
            }

            @JsonProperty("puzzleVisibilityStatusConstraints") abstract Builder setPuzzleVisibilityStatusConstraints(Map<String,String> puzzleVisibilityStatusConstraints);
            Builder addPuzzleVisibilityStatusConstraint(String puzzleId, String visibility) {
                puzzleVisibilityStatusConstraintsBuilder().put(puzzleId, visibility);
                return this;
            }

            abstract VisibilityConstraint build();
        }

        static Builder builder() {
            return new AutoValue_Setec2017HuntDefinition_VisibilityConstraint.Builder()
                    .setRequiresManualAction(false);
        }

        @JsonProperty("requiresManualAction") abstract boolean getRequiresManualAction();

        @JsonProperty("sumConstraints")
        abstract ImmutableList<CharacterLevelConstraint> getSumConstraints();

        @JsonProperty("maxConstraints")
        abstract ImmutableList<CharacterLevelConstraint> getMaxConstraints();

        @JsonProperty("puzzleVisibilityStatusConstraints") abstract ImmutableMap<String, String> getPuzzleVisibilityStatusConstraints();

        boolean isSatisfied(
                Setec2017HuntDefinition.CharacterLevelsProperty characterLevels,
                Map<String, String> puzzleIdToVisibilityStatus
        ) {
            if (getRequiresManualAction()) {
                return false;
            }

            for (CharacterLevelConstraint characterLevelConstraint : getSumConstraints()) {
                ImmutableSet<Character> characters = characterLevelConstraint.getCharacters();
                Integer minimumLevelSum = characterLevelConstraint.getLevels();
                int sum = 0;
                for (Character character : characters) {
                    Integer level = characterLevels.getLevels().get(character);
                    String visibilityString = puzzleIdToVisibilityStatus.getOrDefault(
                            character.toString().toLowerCase(), "INVISIBLE");
                    if (level != null && (visibilityString.equalsIgnoreCase("UNLOCKED") || visibilityString.equalsIgnoreCase("SOLVED"))) {
                        sum += level;
                    }
                }
                if (sum < minimumLevelSum) {
                    return false;
                }
            }

            for (CharacterLevelConstraint characterLevelConstraint : getMaxConstraints()) {
                ImmutableSet<Character> characters = characterLevelConstraint.getCharacters();
                Integer minimumLevel = characterLevelConstraint.getLevels();
                int max = 0;
                for (Character character : characters) {
                    Integer level = characterLevels.getLevels().get(character);
                    if (level != null && level > max) {
                        max = level;
                    }
                }
                if (max < minimumLevel) {
                    return false;
                }
            }

            for (Map.Entry<String, String> entry : getPuzzleVisibilityStatusConstraints().entrySet()) {
                String puzzleId = entry.getKey();
                String constraintStatus = entry.getValue();
                String status = puzzleIdToVisibilityStatus.get(puzzleId);
                if (status == null) {
                    status = Setec2017HuntDefinition.VISIBILITY_STATUS_SET.getDefaultVisibilityStatus();
                }
                if (VISIBILITY_STATUS_ORDER.indexOf(status) < VISIBILITY_STATUS_ORDER.indexOf(constraintStatus)) {
                    return false;
                }
            }

            return true;
        }

        @AutoValue
        abstract static class CharacterLevelConstraint {
            @JsonProperty("characters") abstract ImmutableSet<Character> getCharacters();
            @JsonProperty("levels") abstract int getLevels();

            @JsonCreator
            static CharacterLevelConstraint create(
                    @JsonProperty("levels") int levels,
                    @JsonProperty("characters") Character... characters
            ) {
                return new AutoValue_Setec2017HuntDefinition_VisibilityConstraint_CharacterLevelConstraint(
                        ImmutableSet.copyOf(characters), levels);
            }
        }
    }

    @AutoValue
    abstract static class VisibleConstraintProperty extends Puzzle.Property {

        static {
            registerClass(VisibleConstraintProperty.class);
        }

        @JsonCreator
        public static VisibleConstraintProperty create(
                @JsonProperty("visibleConstraint") VisibilityConstraint visibleConstraint
        ) {
            return new AutoValue_Setec2017HuntDefinition_VisibleConstraintProperty(visibleConstraint);
        }

        @JsonProperty("visibleConstraint") public abstract VisibilityConstraint getVisibleConstraint();
    }

    @AutoValue
    abstract static class UnlockedConstraintProperty extends Puzzle.Property {

        static {
            registerClass(UnlockedConstraintProperty.class);
        }

        @JsonCreator
        public static UnlockedConstraintProperty create(
                @JsonProperty("unlockedConstraint") VisibilityConstraint unlockedConstraint
        ) {
            return new AutoValue_Setec2017HuntDefinition_UnlockedConstraintProperty(unlockedConstraint);
        }

        @JsonProperty("unlockedConstraint") public abstract VisibilityConstraint getUnlockedConstraint();

        private static final ImmutableSet<String> VISIBILITY_REQUIREMENT = ImmutableSet.of("VISIBLE","UNLOCKED","SOLVED");
        @Override
        public Set<String> getVisibilityRequirement() {
            return VISIBILITY_REQUIREMENT;
        }
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_Setec2017HuntDefinition_SolveRewardProperty.Builder.class)
    abstract static class SolveRewardProperty extends Puzzle.Property {

        static {
            registerClass(SolveRewardProperty.class);
        }

        static Builder builder() {
            return new AutoValue_Setec2017HuntDefinition_SolveRewardProperty.Builder()
                    .setGold(0);
        }

        @AutoValue.Builder
        abstract static class Builder {
            @JsonProperty("gold") abstract Builder setGold(int gold);
            @JsonProperty("characterLevels") abstract Builder setCharacterLevels(Map<Character,Integer> characterLevels);
            @JsonProperty("inventoryItem") abstract Builder setInventoryItem(@Nullable InventoryItem inventoryItem);

            abstract ImmutableMap.Builder<Character, Integer> characterLevelsBuilder();

            Builder addCharacterLevels(Character character, int levels) {
                characterLevelsBuilder().put(character, levels);
                return this;
            }

            abstract SolveRewardProperty build();
        }

        @JsonProperty("gold") abstract int getGold();
        @JsonProperty("characterLevels") abstract ImmutableMap<Character, Integer> getCharacterLevels();

        @Nullable
        @JsonProperty("inventoryItem")
        abstract InventoryItem getInventoryItem();

        private static final ImmutableSet<String> VISIBILITY_REQUIREMENT = ImmutableSet.of("UNLOCKED","SOLVED");
        @Override
        public Set<String> getVisibilityRequirement() {
            return VISIBILITY_REQUIREMENT;
        }
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_Setec2017HuntDefinition_GrantGoldEvent.Builder.class)
    public static abstract class GrantGoldEvent extends Event {
        public String getEventType() {
            return GrantGoldEvent.class.getSimpleName();
        }

        @AutoValue.Builder
        public static abstract class Builder {
            @JsonProperty("teamId") public abstract Builder setTeamId(String teamId);
            @JsonProperty("gold") public abstract Builder setGold(int gold);
            public abstract GrantGoldEvent build();
        }

        public static Builder builder() {
            return new AutoValue_Setec2017HuntDefinition_GrantGoldEvent.Builder();
        }

        @JsonProperty("teamId") public abstract String getTeamId();
        @JsonProperty("gold") public abstract int getGold();
    }
    static {
        Event.registerEventClass(GrantGoldEvent.class);
    }

    @Override
    public List<Puzzle> getPuzzles() {
        return ImmutableList.copyOf(Setec2017Puzzles.PUZZLES.values());
    }

    private class Setec2017CompositeEventProcessor extends CompositeEventProcessor {
        private Set<String> getVisibilityAffectedTeams(Event event) {
            if (event instanceof HuntStartEvent || event instanceof FullReleaseEvent) {
                return ImmutableSet.copyOf(huntStatusStore.getTeamIds());
            }
            if (event instanceof VisibilityChangeEvent) {
                VisibilityChangeEvent visibilityChangeEvent = (VisibilityChangeEvent) event;
                return ImmutableSet.of(visibilityChangeEvent.getVisibility().getTeamId());
            }
            return ImmutableSet.of();
        }

        @Override
        public void process(Event event) {
            super.process(event);
            updateVisibility(getVisibilityAffectedTeams(event));
        }

        @Override
        public void processBatch(List<? extends Event> events) {
            Set<String> affectedTeams = new HashSet<>();
            for (Event event : events) {
                super.process(event);
                affectedTeams.addAll(getVisibilityAffectedTeams(event));
            }
            if (!affectedTeams.isEmpty()) {
                updateVisibility(affectedTeams);
            }
        }
    }

    @Override
    public CompositeEventProcessor generateCompositeEventProcessor() {
        return new Setec2017CompositeEventProcessor();
    }

    private void updateVisibility(Set<String> teamIds) {
        if (teamIds.isEmpty()) {
            return;
        }

        Map<String,CharacterLevelsProperty> teamToCharacterLevels = Maps.toMap(
                teamIds,
                teamId -> huntStatusStore.getTeam(teamId).getTeamProperty(CharacterLevelsProperty.class));
        Map<String,Map<String,String>> teamToPuzzleIdToVisibilityStatus = Maps.toMap(
                teamIds,
                teamId -> huntStatusStore.getVisibilitiesForTeam(teamId).stream()
                        .collect(Collectors.toMap(Visibility::getPuzzleId, Visibility::getStatus)));

        Table<String, String, String> teamPuzzleStatusTable = HashBasedTable.create();

        for (Puzzle puzzle : puzzleStore.getPuzzles().values()) {
            VisibilityConstraint unlockedConstraint = puzzle.getPuzzleProperty(UnlockedConstraintProperty.class).getUnlockedConstraint();
            VisibilityConstraint visibleConstraint = puzzle.getPuzzleProperty(VisibleConstraintProperty.class).getVisibleConstraint();

            for (String teamId : teamIds) {
                CharacterLevelsProperty characterLevels = teamToCharacterLevels.get(teamId);
                Map<String,String> puzzleIdToVisibilityStatus = teamToPuzzleIdToVisibilityStatus.get(teamId);

                if (unlockedConstraint.isSatisfied(
                        characterLevels,
                        puzzleIdToVisibilityStatus
                )) {
                    teamPuzzleStatusTable.put(teamId, puzzle.getPuzzleId(), "UNLOCKED");
                } else if (visibleConstraint.isSatisfied(
                        characterLevels,
                        puzzleIdToVisibilityStatus
                )) {
                    teamPuzzleStatusTable.put(teamId, puzzle.getPuzzleId(), "VISIBLE");
                }
            }
        }

        if (!teamPuzzleStatusTable.isEmpty()) {
            huntStatusStore.setVisibilityBatch(teamPuzzleStatusTable);
        }
    }

    @Override
    public void addToEventProcessor() {
        eventProcessor.addEventProcessor(HuntStartEvent.class, event -> {
            boolean changed = huntStatusStore.recordHuntRunStart();
            if (changed) {
                for (String teamId : huntStatusStore.getTeamIds()) {
                    huntStatusStore.setTeamProperty(
                            teamId,
                            GoldProperty.class,
                            GoldProperty.create(0)
                    );
                    huntStatusStore.setTeamProperty(
                            teamId,
                            CharacterLevelsProperty.class,
                            CharacterLevelsProperty.create(ImmutableMap.of())
                    );
                    huntStatusStore.setTeamProperty(
                            teamId,
                            InventoryProperty.class,
                            InventoryProperty.create(ImmutableSet.of())
                    );
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
            }
        });

        eventProcessor.addEventProcessor(VisibilityChangeEvent.class, event -> {
            Visibility visibility = event.getVisibility();
            if (visibility.getStatus().equals("SOLVED")) {
                Puzzle puzzle = puzzleStore.getPuzzle(visibility.getPuzzleId());
                SolveRewardProperty solveReward = puzzle.getPuzzleProperty(SolveRewardProperty.class);
                if (solveReward.getGold() != 0) {
                    huntStatusStore.mutateTeamProperty(
                            visibility.getTeamId(),
                            GoldProperty.class,
                            gold -> GoldProperty.create(gold.getGold() + solveReward.getGold())
                    );
                }
                if (!solveReward.getCharacterLevels().isEmpty()) {
                    huntStatusStore.mutateTeamProperty(
                            visibility.getTeamId(),
                            CharacterLevelsProperty.class,
                            characterLevels -> {
                                Map<Character, Integer> newLevels = new HashMap<>(characterLevels.getLevels());
                                for (Map.Entry<Character, Integer> entry : solveReward.getCharacterLevels().entrySet()) {
                                    Character character = entry.getKey();
                                    Integer levelsToAdd = entry.getValue();
                                    if (characterLevels.getLevels().containsKey(character)) {
                                        newLevels.put(character, characterLevels.getLevels().get(character) + levelsToAdd);
                                    } else {
                                        newLevels.put(character, levelsToAdd);
                                    }
                                }
                                return CharacterLevelsProperty.create(ImmutableMap.copyOf(newLevels));
                            }
                    );
                }
                if (solveReward.getInventoryItem() != null) {
                    huntStatusStore.mutateTeamProperty(
                            visibility.getTeamId(),
                            InventoryProperty.class,
                            inventory -> InventoryProperty.create(
                                    ImmutableSet.<InventoryItem>builder()
                                            .addAll(inventory.getInventoryItems())
                                            .add(solveReward.getInventoryItem())
                                            .build()
                            )
                    );
                }
            }
        });

        eventProcessor.addEventProcessor(FullReleaseEvent.class, event -> {
            String puzzleId = puzzleStore.getCanonicalPuzzleId(event.getPuzzleId());
            Table<String,String,String> teamPuzzleStatusTable = HashBasedTable.create();
            for (String teamId : huntStatusStore.getTeamIds()) {
                teamPuzzleStatusTable.put(teamId, puzzleId, "UNLOCKED");
            }
            huntStatusStore.setVisibilityBatch(teamPuzzleStatusTable);
        });

        eventProcessor.addEventProcessor(FullSolveEvent.class, event -> {
            String puzzleId = puzzleStore.getCanonicalPuzzleId(event.getPuzzleId());
            Table<String,String,String> teamPuzzleStatusTable = HashBasedTable.create();
            for (String teamId : huntStatusStore.getTeamIds()) {
                teamPuzzleStatusTable.put(teamId, puzzleId, "SOLVED");
            }
            huntStatusStore.setVisibilityBatch(teamPuzzleStatusTable);
        });

        eventProcessor.addEventProcessor(HintCompleteEvent.class, event -> {
            String teamId = event.getHintRequest().getTeamId();
            HintRequestStatus hintRequestStatus = event.getHintRequest().getStatus();
            if (hintRequestStatus == HintRequestStatus.REJECTED) {
                // Refund the team their gold back.
                huntStatusStore.mutateTeamProperty(
                        teamId,
                        GoldProperty.class,
                        goldProperty -> GoldProperty.create(goldProperty.getGold() + 100)
                );
            }
        });

        eventProcessor.addEventProcessor(GrantGoldEvent.class, event -> {
            huntStatusStore.mutateTeamProperty(
                    event.getTeamId(),
                    GoldProperty.class,
                    goldProperty -> GoldProperty.create(goldProperty.getGold() + event.getGold())
            );
        });
    }

    @Override
    public boolean handleHintRequest(HintRequest hintRequest) {
        AtomicBoolean deductedGold = new AtomicBoolean(false);
        huntStatusStore.mutateTeamProperty(
                hintRequest.getTeamId(),
                GoldProperty.class,
                goldProperty -> {
                    if (goldProperty.getGold() > 0) {
                        deductedGold.set(true);
                        return GoldProperty.create(goldProperty.getGold() - 100);
                    }
                    return goldProperty;
                }
        );
        return deductedGold.get();
    }
}
