package edu.mit.puzzle.cube.huntimpl.insideout2018;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.google.common.collect.*;
import edu.mit.puzzle.cube.core.events.*;
import edu.mit.puzzle.cube.core.model.*;
import edu.mit.puzzle.cube.modules.model.StandardVisibilityStatusSet;

import javax.annotation.Nullable;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class HuntDefinition extends edu.mit.puzzle.cube.core.HuntDefinition {
    // We need to force Java to load the puzzle and team property classes, to
    // ensure that their static initializers actually run and register the
    // property classes with Puzzle.Property and Team.Property. It would be nice
    // to come up with a cleaner solution for this, but this works for now.
    static {
        try {
            Class.forName("edu.mit.puzzle.cube.core.model.Puzzle$AnswersProperty");
            Class.forName("edu.mit.puzzle.cube.core.model.Puzzle$DisplayNameProperty");
            Class.forName("edu.mit.puzzle.cube.core.model.Puzzle$DisplayIdProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$ScoresProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$ScoreUpdateEvent");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$GrantScoreEvent");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$IslandOpenOrderProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$TrailingTideSingleRoundEvent");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$TrailingTideWholeIslandEvent");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$GoContentDeliveredProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$GoContentDeliveredEvent");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$EmotionsProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$SadnessProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$AngerProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$SymbolProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$RadioProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$ScifiDoorColorProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$ScifiDoorBoxesProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$TileProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$RoadsProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$ChitProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$BrainpowerGroupProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$InteractionProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$FeedersProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$SolveRewardProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$VisibleConstraintProperty");
            Class.forName("edu.mit.puzzle.cube.huntimpl.insideout2018.HuntDefinition$UnlockedConstraintProperty");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private final Clock clock = Clock.systemUTC();

    private static final VisibilityStatusSet VISIBILITY_STATUS_SET = new StandardVisibilityStatusSet();
    private static final ImmutableList<String> VISIBILITY_STATUS_ORDER = ImmutableList.of(
            "INVISIBLE", "VISIBLE", "UNLOCKED", "SOLVED");

    public static final ImmutableSet<String> ISLANDS = ImmutableSet.of("pokemon", "games", "scifi", "hacking");

    public enum Score {
        BRAINPOWER,
        BRAINPOWER_DRIP,
        MEMORY_SOLVES,
        BUZZY_BUCKS,
        BUZZY_BUCKS_DRIP,
        GRAND_FINALE_PROGRESS,
        EMOTION_META_SOLVES,
        EMOTION_ENCOUNTER_COMPLETIONS,
        HACKING_SCOUT_SOLVES,
        HACKING_BUILD_SOLVES,
        HACKING_DEPLOY_SOLVES,
        HACKING_SUBMETA_SOLVES,
        POKEMON_PATH_PROGRESS,
    }

    @AutoValue
    public abstract static class ScoresProperty extends Team.Property {
        static {
            registerClass(ScoresProperty.class);
        }

        @JsonCreator
        public static ScoresProperty create(@JsonProperty("scores") ImmutableMap<Score, Integer> scores) {
            return new AutoValue_HuntDefinition_ScoresProperty(scores);
        }

        @JsonProperty("scores")
        public abstract ImmutableMap<Score, Integer> getScores();

        public ScoresProperty addScores(Map<Score, Integer> addedScores) {
            Map<Score, Integer> newScores = new HashMap<>(getScores());
            for (Map.Entry<Score, Integer> entry : addedScores.entrySet()) {
                newScores.put(entry.getKey(), entry.getValue() + newScores.getOrDefault(entry.getKey(), 0));
            }
            return create(ImmutableMap.copyOf(newScores));
        }
    }

    @AutoValue
    static abstract class ScoreUpdateEvent extends Event {
        static {
            registerEventClass(ScoreUpdateEvent.class);
        }

        public String getEventType() {
            return ScoreUpdateEvent.class.getSimpleName();
        }

        abstract String getTeamId();

        static Builder builder() {
            return new AutoValue_HuntDefinition_ScoreUpdateEvent.Builder();
        }

        @AutoValue.Builder
        static abstract class Builder {
            abstract Builder setTeamId(String teamId);

            abstract ScoreUpdateEvent build();
        }
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntDefinition_GrantScoreEvent.Builder.class)
    static abstract class GrantScoreEvent extends Event {
        static {
            registerEventClass(GrantScoreEvent.class);
        }

        public String getEventType() {
            return GrantScoreEvent.class.getSimpleName();
        }

        @JsonProperty("teamId") abstract String getTeamId();

        @JsonProperty("scoreType") abstract Score getScoreType();

        @JsonProperty("scoreAmount") abstract int getScoreAmount();

        static Builder builder() {
            return new AutoValue_HuntDefinition_GrantScoreEvent.Builder();
        }

        @AutoValue.Builder
        static abstract class Builder {
            @JsonProperty("teamId") abstract Builder setTeamId(String teamId);

            @JsonProperty("scoreType") abstract Builder setScoreType(Score scoreType);

            @JsonProperty("scoreAmount") abstract Builder setScoreAmount(int scoreAmount);

            abstract GrantScoreEvent build();
        }
    }

    @AutoValue
    public abstract static class IslandOpenOrderProperty extends Team.Property {
        static {
            registerClass(IslandOpenOrderProperty.class);
        }

        @JsonCreator
        public static IslandOpenOrderProperty create(@JsonProperty("openOrder") ImmutableList<String> openOrder) {
            return new AutoValue_HuntDefinition_IslandOpenOrderProperty(openOrder);
        }

        @JsonProperty("openOrder")
        public abstract ImmutableList<String> getOpenOrder();
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntDefinition_TrailingTideSingleRoundEvent.Builder.class)
    static abstract class TrailingTideSingleRoundEvent extends Event {
        static {
            registerEventClass(TrailingTideSingleRoundEvent.class);
        }

        public String getEventType() {
            return TrailingTideSingleRoundEvent.class.getSimpleName();
        }

        @JsonProperty("teamId") abstract String getTeamId();

        @JsonProperty("roundPrefix") abstract String getRoundPrefix();

        static Builder builder() {
            return new AutoValue_HuntDefinition_TrailingTideSingleRoundEvent.Builder();
        }

        @AutoValue.Builder
        static abstract class Builder {
            @JsonProperty("teamId") abstract Builder setTeamId(String teamId);

            @JsonProperty("roundPrefix") abstract Builder setRoundPrefix(String roundPrefix);

            abstract TrailingTideSingleRoundEvent build();
        }
    }

    /**
     * TrailingTideWholeIslandEvent is the trigger to unlock the Nth island for _all_ teams, unlike
     * TrailingTideSingleRoundEvent, which unlocks a specific island for a specific team. This gets translated into a
     * series of TrailingTideSingleRoundEvents.
     *
     * Note that islandNumber=0 is a special case - that means memories.
     */
    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntDefinition_TrailingTideWholeIslandEvent.Builder.class)
    static abstract class TrailingTideWholeIslandEvent extends Event {
        static {
            registerEventClass(TrailingTideWholeIslandEvent.class);
        }

        public String getEventType() {
            return TrailingTideWholeIslandEvent.class.getSimpleName();
        }

        @JsonProperty("islandNumber") abstract int getIslandNumber();

        static Builder builder() {
            return new AutoValue_HuntDefinition_TrailingTideWholeIslandEvent.Builder();
        }

        @AutoValue.Builder
        static abstract class Builder {
            @JsonProperty("islandNumber") abstract Builder setIslandNumber(int islandNumber);

            abstract TrailingTideWholeIslandEvent build();
        }
    }

    @AutoValue
    public abstract static class GoContentDeliveredProperty extends Team.Property {
        static {
            registerClass(GoContentDeliveredProperty.class);
        }

        @JsonCreator
        public static GoContentDeliveredProperty create(@JsonProperty("goContentDelivered") boolean goContentDelivered) {
            return new AutoValue_HuntDefinition_GoContentDeliveredProperty(goContentDelivered);
        }

        @JsonProperty("goContentDelivered")
        public abstract boolean getGoContentDelivered();

        public GoContentDeliveredProperty GoContentDelivered() {
            return create(true);
        }
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntDefinition_GoContentDeliveredEvent.Builder.class)
    static abstract class GoContentDeliveredEvent extends Event {
        static {
            registerEventClass(GoContentDeliveredEvent.class);
        }

        public String getEventType() {
            return GoContentDeliveredEvent.class.getSimpleName();
        }

        @JsonProperty("teamId") abstract String getTeamId();

        static Builder builder() {
            return new AutoValue_HuntDefinition_GoContentDeliveredEvent.Builder();
        }

        @AutoValue.Builder
        static abstract class Builder {
            @JsonProperty("teamId") abstract Builder setTeamId(String teamId);

            abstract GoContentDeliveredEvent build();
        }
    }

    /**
     * InteractionProperty is set on individual puzzles which *need to be scheduled on unlock.*
     */
    @AutoValue
    public abstract static class InteractionProperty extends Puzzle.Property {
        static {
            registerClass(InteractionProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @JsonCreator
        public static InteractionProperty create(@JsonProperty("interaction") boolean interaction) {
            return new AutoValue_HuntDefinition_InteractionProperty(interaction);
        }

        @JsonProperty("interaction")
        abstract boolean getInteraction();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * EmotionsProperty is set on individual puzzles (not metas) in the emotions round. It is
     * exposed to the frontend and used to determine how to display the puzzles.
     */
    @AutoValue
    public abstract static class EmotionsProperty extends Puzzle.Property {
        static {
            registerClass(EmotionsProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @JsonCreator
        public static EmotionsProperty create(@Nullable @JsonProperty("emotions") Set<Emotion> emotions) {
            return new AutoValue_HuntDefinition_EmotionsProperty(ImmutableSet.copyOf(emotions));
        }

        @JsonProperty("emotions")
        @Nullable
        abstract ImmutableSet<Emotion> getEmotions();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * SadnessProperty is set on individual sadness puzzles in the emotions round. It is exposed
     * to the frontend and used to display the puzzles.
     */
    @AutoValue
    public abstract static class SadnessProperty extends Puzzle.Property {
        static {
            registerClass(SadnessProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @JsonCreator
        public static SadnessProperty create(@JsonProperty("sadness") int sadness) {
            return new AutoValue_HuntDefinition_SadnessProperty(sadness);
        }

        @JsonProperty("sadness")
        abstract int getSadness();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * AngerProperty is set on individual anger puzzles in the emotions round. It is exposed
     * to the frontend as part of the display for the puzzles
     */
    @AutoValue
    public abstract static class AngerProperty extends Puzzle.Property {
        static {
            registerClass(AngerProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @JsonCreator
        public static AngerProperty create(@JsonProperty("anger") int anger) {
            return new AutoValue_HuntDefinition_AngerProperty(anger);
        }

        @JsonProperty("anger")
        abstract int getAnger();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * SymbolProperty is set on individual puzzles in the hacking and pokemon round. It is exposed
     * to the frontend as part of the display for the puzzles
     */
    @AutoValue
    public abstract static class SymbolProperty extends Puzzle.Property {
        static {
            registerClass(SymbolProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("VISIBLE", "UNLOCKED", "SOLVED");

        @JsonCreator
        public static SymbolProperty create(@JsonProperty("symbol") String symbol) {
            return new AutoValue_HuntDefinition_SymbolProperty(symbol);
        }

        @JsonProperty("symbol")
        abstract String getSymbol();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * RadioProperty is set on individual puzzles in the hacking round. It is exposed
     * to the frontend as part of the display for the round
     */
    @AutoValue
    public abstract static class RadioProperty extends Puzzle.Property {
        static {
            registerClass(RadioProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("SOLVED");

        @JsonCreator
        public static RadioProperty create(@JsonProperty("radio") String radio) {
            return new AutoValue_HuntDefinition_RadioProperty(radio);
        }

        @JsonProperty("radio")
        abstract String getRadio();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * ScifiDoorColorProperty is set on individual puzzles in the scifi round. It is exposed
     * to the frontend as part of the display for the puzzles and the round page
     */
    @AutoValue
    public abstract static class ScifiDoorColorProperty extends Puzzle.Property {
        static {
            registerClass(ScifiDoorColorProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("VISIBLE", "UNLOCKED", "SOLVED");

        @JsonCreator
        public static ScifiDoorColorProperty create(@JsonProperty("scifiDoorColor") String color) {
            return new AutoValue_HuntDefinition_ScifiDoorColorProperty(color);
        }

        @JsonProperty("scifiDoorColor")
        abstract String getScifiDoorColor();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * ScifiDoorBoxesProperty is set on individual puzzles in the scifi round. It is exposed
     * to the frontend as part of the display for the puzzles and the round page
     */
    @AutoValue
    public abstract static class ScifiDoorBoxesProperty extends Puzzle.Property {
        static {
            registerClass(ScifiDoorBoxesProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("VISIBLE", "UNLOCKED", "SOLVED");

        @JsonCreator
        public static ScifiDoorBoxesProperty create(@JsonProperty("scifiDoorBoxes") int boxes) {
            return new AutoValue_HuntDefinition_ScifiDoorBoxesProperty(boxes);
        }

        @JsonProperty("scifiDoorBoxes")
        abstract int getScifiDoorBoxes();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    /**
     * TileProperty is set on individual puzzles in the games round. It is exposed
     * to the frontend as part of the display for the puzzles
     */
    @AutoValue
    public abstract static class TileProperty extends Puzzle.Property {
        static {
            registerClass(TileProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("SOLVED");

        @JsonCreator
        public static TileProperty create(@JsonProperty("tile") String tile) {
            return new AutoValue_HuntDefinition_TileProperty(tile);
        }

        @JsonProperty("tile")
        abstract String getTile();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    public enum Road {
        WEST, NORTHWEST, NORTHEAST, EAST, SOUTHEAST, SOUTHWEST,
    }

    /**
     * RoadsProperty is set on individual puzzles (not metas) in the games round. It is
     * exposed to the frontend and used to determine how to display the puzzles.
     */
    @AutoValue
    public abstract static class RoadsProperty extends Puzzle.Property {
        static {
            registerClass(RoadsProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @JsonCreator
        public static RoadsProperty create(@Nullable @JsonProperty("roads") Set<Road> roads) {
            return new AutoValue_HuntDefinition_RoadsProperty(ImmutableSet.copyOf(roads));
        }

        @JsonProperty("roads")
        @Nullable
        abstract ImmutableSet<Road> getRoads();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

     /**
     * ChitProperty is set on individual puzzles in the games round. It is exposed
     * to the frontend as part of the display for the puzzles
     */
    @AutoValue
    public abstract static class ChitProperty extends Puzzle.Property {
        static {
            registerClass(ChitProperty.class);
        }

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("SOLVED");

        @JsonCreator
        public static ChitProperty create(@JsonProperty("chit") int chit) {
            return new AutoValue_HuntDefinition_ChitProperty(chit);
        }

        @JsonProperty("chit")
        abstract int getChit();

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    public enum BrainpowerGroup {
        MEMORIES, HACKING, SCIFI, GAMES, POKEMON;
    }

    /**
     * BrainpowerGroupProperty is set on individual puzzles that contribute brainpower. Because the amount of
     * brainpower granted increases based on the number of solved puzzles in a round, we need to have some construct of
     * round by which we can group them and figure out how much brainpower has already been granted.
     */
    @AutoValue
    public abstract static class BrainpowerGroupProperty extends Puzzle.Property {
        static {
            registerClass(BrainpowerGroupProperty.class);
        }

        @JsonCreator
        public static BrainpowerGroupProperty create(@JsonProperty("brainpowerGroup") BrainpowerGroup brainpowerGroup) {
            return new AutoValue_HuntDefinition_BrainpowerGroupProperty(brainpowerGroup);
        }

        @JsonProperty("brainpowerGroup")
        abstract BrainpowerGroup getBrainpowerGroup();
    }

    public enum Emotion {
        JOY, SADNESS, FEAR, DISGUST, ANGER;
    }

    /**
     * FeedersProperty is set on some meta puzzles, where the set of puzzles feeding into the meta is important for
     * the presentation of the meta.
     */
    @AutoValue
    public abstract static class FeedersProperty extends Puzzle.Property {
        static {
            registerClass(FeedersProperty.class);
        }

        @JsonCreator
        public static FeedersProperty create(@Nullable @JsonProperty("feeders") Set<String> feeders) {
            return new AutoValue_HuntDefinition_FeedersProperty(ImmutableSet.copyOf(feeders));
        }

        @JsonProperty("feeders")
        @Nullable
        abstract ImmutableSet<String> getFeeders();

        private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @Override
        public Set<String> getVisibilityRequirement() {
            return DISPLAY_PROPERTY_ACCESS_STATUSES;
        }
    }

    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntDefinition_SolveRewardProperty.Builder.class)
    public abstract static class SolveRewardProperty extends Puzzle.Property {
        static {
            registerClass(SolveRewardProperty.class);
        }

        @JsonProperty("scores")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        abstract ImmutableMap<Score, Integer> getScores();

        public static SolveRewardProperty scoreReward(Score score, int amount) {
            return builder().addScore(score, amount).build();
        }

        public static SolveRewardProperty brainpowerReward(int amount) {
            return builder().addScore(Score.BRAINPOWER, amount).build();
        }

        public static SolveRewardProperty buzzyBucksReward(int amount) {
            return builder().addScore(Score.BUZZY_BUCKS, amount).build();
        }

        static Builder builder() {
            return new AutoValue_HuntDefinition_SolveRewardProperty.Builder();
        }

        @AutoValue.Builder
        abstract static class Builder {
            @JsonProperty("scores")
            abstract Builder setScores(ImmutableMap<Score, Integer> scores);

            abstract ImmutableMap.Builder<Score, Integer> scoresBuilder();

            Builder addScore(Score type, int amount) {
                scoresBuilder().put(type, amount);
                return this;
            }

            abstract SolveRewardProperty build();
        }
    }

    public static ZoneId CAMBRIDGE_ZONE = ZoneId.of("America/New_York");

    @AutoValue
    @JsonDeserialize(builder = AutoValue_HuntDefinition_VisibilityConstraint.Builder.class)
    protected abstract static class VisibilityConstraint {
        @JsonProperty
        abstract boolean getAutomaticallySatisfied();

        @JsonProperty
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        abstract ImmutableMap<Score, Integer> getScoreConstraints();

        @JsonProperty
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        abstract ImmutableMap<String, String> getPuzzleStatusConstraints();

        @JsonProperty
        @Nullable
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract ZonedDateTime getTimeConstraint();

        public static VisibilityConstraint automaticallySatisfied() {
            return builder().setAutomaticallySatisfied(true).build();
        }

        public static VisibilityConstraint scoreConstraint(Score type, int amount) {
            return builder().addScoreConstraint(type, amount).build();
        }

        public static VisibilityConstraint timeConstraint(ZonedDateTime timeConstraint) {
            return builder().setTimeConstraint(timeConstraint).build();
        }

        public static VisibilityConstraint timeConstraint(LocalDateTime timeConstraint) {
            return builder().setTimeConstraint(ZonedDateTime.of(timeConstraint, CAMBRIDGE_ZONE)).build();
        }

        public static VisibilityConstraint memorySolveConstraint(int amount) {
            return scoreConstraint(Score.MEMORY_SOLVES, amount);
        }

        public static VisibilityConstraint brainpowerConstraint(int amount) {
            return scoreConstraint(Score.BRAINPOWER, amount);
        }

        public static VisibilityConstraint puzzleStatusConstraint(String puzzleId, String visibility) {
            return builder().addPuzzleStatusConstraint(puzzleId, visibility).build();
        }

        static Builder builder() {
            return new AutoValue_HuntDefinition_VisibilityConstraint.Builder()
                    .setAutomaticallySatisfied(false);
        }

        @AutoValue.Builder
        abstract static class Builder {
            @JsonProperty("automaticallySatisfied")
            abstract Builder setAutomaticallySatisfied(boolean automaticallySatisfied);

            @JsonProperty("scoreConstraints")
            abstract Builder setScoreConstraints(ImmutableMap<Score, Integer> scoreConstraints);

            abstract ImmutableMap.Builder<Score, Integer> scoreConstraintsBuilder();
            public Builder addScoreConstraint(Score type, int amount) {
                scoreConstraintsBuilder().put(type, amount);
                return this;
            }

            @JsonProperty("puzzleStatusConstraints")
            abstract Builder setPuzzleStatusConstraints(Map<String, String> puzzleStatusConstraints);

            abstract ImmutableMap.Builder<String, String> puzzleStatusConstraintsBuilder();
            public Builder addPuzzleStatusConstraint(String puzzleId, String visibility) {
                puzzleStatusConstraintsBuilder().put(puzzleId, visibility);
                return this;
            }

            @JsonProperty("timeConstraint")
            abstract Builder setTimeConstraint(ZonedDateTime timeConstraint);

            abstract VisibilityConstraint build();
        }

        boolean isSatisfied(Map<Score, Integer> scores, Map<String, String> puzzleIdToStatus) {
            if (getAutomaticallySatisfied()) {
                return true;
            }

            for (Map.Entry<Score, Integer> entry : getScoreConstraints().entrySet()) {
                if (scores.getOrDefault(entry.getKey(), 0) >= entry.getValue()) {
                    return true;
                }
            }

            for (Map.Entry<String, String> entry : getPuzzleStatusConstraints().entrySet()) {
                String puzzleId = entry.getKey();
                String requiredStatus = entry.getValue();
                String currentStatus = puzzleIdToStatus.getOrDefault(puzzleId,
                        VISIBILITY_STATUS_SET.getDefaultVisibilityStatus());
                if (VISIBILITY_STATUS_ORDER.indexOf(requiredStatus) <= VISIBILITY_STATUS_ORDER.indexOf(currentStatus)) {
                    return true;
                }
            }

            return false;
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
            return new AutoValue_HuntDefinition_VisibleConstraintProperty(visibleConstraint);
        }

        @JsonProperty("visibleConstraint")
        public abstract VisibilityConstraint getVisibleConstraint();
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
            return new AutoValue_HuntDefinition_UnlockedConstraintProperty(unlockedConstraint);
        }

        @JsonProperty("unlockedConstraint")
        public abstract VisibilityConstraint getUnlockedConstraint();

        // You're allowed to tell what is required to unlock a puzzle if you can see it. (Normally properties are always hidden)
        private static final ImmutableSet<String> VISIBILITY_REQUIREMENT = ImmutableSet.of("VISIBLE", "UNLOCKED", "SOLVED");

        @Override
        public Set<String> getVisibilityRequirement() {
            return VISIBILITY_REQUIREMENT;
        }
    }

    private static final int TOTAL_ROUND_BRAINPOWER = 1250;

    private int computeBrainpower(String teamId, String solvedPuzzleId) {
        Team team = huntStatusStore.getTeam(teamId);
        Map<String, String> puzzleIdToStatus = huntStatusStore.getVisibilitiesForTeam(teamId).stream()
                .collect(Collectors.toMap(Visibility::getPuzzleId, Visibility::getStatus));

        BrainpowerGroupProperty brainpowerGroupProperty = puzzleStore
                .getPuzzle(solvedPuzzleId).getPuzzleProperty(BrainpowerGroupProperty.class);

        if (brainpowerGroupProperty == null) {
            return 0;
        }

        BrainpowerGroup brainpowerGroup = brainpowerGroupProperty.getBrainpowerGroup();

        int solvedPuzzles = 0;
        int totalPuzzles = 0;

        for (Puzzle puzzle : puzzleStore.getPuzzles().values()) {
            BrainpowerGroupProperty prop = puzzle.getPuzzleProperty(BrainpowerGroupProperty.class);
            if (prop == null) {
                continue;
            }

            if (prop.getBrainpowerGroup() != brainpowerGroup) {
                continue;
            }

            if (puzzleIdToStatus.getOrDefault(puzzle.getPuzzleId(), "INVISIBLE").equals("SOLVED")) {
                solvedPuzzles++;
            }
            totalPuzzles++;
        }

        float totalUnits = (totalPuzzles + 1) * totalPuzzles / 2;
        float unitValue = TOTAL_ROUND_BRAINPOWER / totalUnits;
        return (int)(solvedPuzzles * unitValue);
    }

    private void updateVisibility(String teamId) {
        Team team = huntStatusStore.getTeam(teamId);
        Map<Score, Integer> scores = team.getTeamProperty(ScoresProperty.class).getScores();
        Map<String, String> puzzleIdToStatus = huntStatusStore.getVisibilitiesForTeam(teamId).stream()
                .collect(Collectors.toMap(Visibility::getPuzzleId, Visibility::getStatus));

        Table<String, String, String> teamPuzzleStatusTable = HashBasedTable.create();

        for (Puzzle puzzle : puzzleStore.getPuzzles().values()) {
            VisibilityConstraint visibleConstraint = puzzle.getPuzzleProperty(VisibleConstraintProperty.class)
                    .getVisibleConstraint();
            VisibilityConstraint unlockedConstraint = puzzle.getPuzzleProperty(UnlockedConstraintProperty.class)
                    .getUnlockedConstraint();

            if (unlockedConstraint.isSatisfied(scores, puzzleIdToStatus)) {
                teamPuzzleStatusTable.put(teamId, puzzle.getPuzzleId(), "UNLOCKED");
            } else if (visibleConstraint.isSatisfied(scores, puzzleIdToStatus)) {
                teamPuzzleStatusTable.put(teamId, puzzle.getPuzzleId(), "VISIBLE");
            }
        }

        if (!teamPuzzleStatusTable.isEmpty()) {
            huntStatusStore.setVisibilityBatch(teamPuzzleStatusTable);
        }
    }

    public VisibilityStatusSet getVisibilityStatusSet() {
        return VISIBILITY_STATUS_SET;
    }

    @Override
    public List<Puzzle> getPuzzles() {
        return ImmutableList.copyOf(Puzzles.PUZZLES);
    }

    @Override
    public void addToEventProcessor() {
        eventProcessor.addEventProcessor(HuntStartEvent.class, event -> {
            boolean changed = huntStatusStore.recordHuntRunStart();
            if (changed) {
                Set<String> teamIds = huntStatusStore.getTeamIds();
                teamIds.forEach(teamId -> {
                    huntStatusStore.setTeamProperty(
                            teamId,
                            ScoresProperty.class,
                            ScoresProperty.create(ImmutableMap.of()));
                    huntStatusStore.setTeamProperty(
                            teamId,
                            IslandOpenOrderProperty.class,
                            IslandOpenOrderProperty.create(ImmutableList.of()));
                });
                eventProcessor.processBatch(teamIds.stream()
                        .map(id -> ScoreUpdateEvent.builder()
                                .setTeamId(id)
                                .build())
                        .collect(Collectors.toList()));
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

        eventProcessor.addEventProcessor(FullReleaseEvent.class, event -> {
            String puzzleId = puzzleStore.getCanonicalPuzzleId(event.getPuzzleId());
            ImmutableTable.Builder<String, String, String> b = new ImmutableTable.Builder<>();
            huntStatusStore.getTeamIds().forEach(teamId -> {
                b.put(teamId, puzzleId, "UNLOCKED");
            });
            huntStatusStore.setVisibilityBatch(b.build());
        });

        eventProcessor.addEventProcessor(FullSolveEvent.class, event -> {
            String puzzleId = puzzleStore.getCanonicalPuzzleId(event.getPuzzleId());
            ImmutableTable.Builder<String, String, String> b = new ImmutableTable.Builder<>();
            huntStatusStore.getTeamIds().forEach(teamId -> {
                b.put(teamId, puzzleId, "SOLVED");
            });
            huntStatusStore.setVisibilityBatch(b.build());
        });

        eventProcessor.addEventProcessor(VisibilityChangeEvent.class, event -> {
            Visibility visibility = event.getVisibility();
            String teamId = visibility.getTeamId();

            String puzzleId = visibility.getPuzzleId();
            if (ISLANDS.contains(puzzleId) && visibility.getStatus().equals("UNLOCKED")) {
                huntStatusStore.mutateTeamProperty(teamId, IslandOpenOrderProperty.class, prop -> {
                    ImmutableList<String> order = prop.getOpenOrder();
                    if (order.contains(puzzleId)) {
                        return prop;
                    }

                    return IslandOpenOrderProperty.create(new ImmutableList.Builder<String>()
                            .addAll(order)
                            .add(puzzleId)
                            .build());
                });
            }

            if (visibility.getStatus().equals("SOLVED")) {
                Puzzle puzzle = puzzleStore.getPuzzle(puzzleId);
                boolean scoreUpdated = false;

                int brainpowerReward = computeBrainpower(teamId, puzzleId);
                if (brainpowerReward > 0) {
                    huntStatusStore.mutateTeamProperty(
                            teamId,
                            ScoresProperty.class,
                            scores -> scores.addScores(ImmutableMap.of(Score.BRAINPOWER, brainpowerReward))
                    );
                    scoreUpdated = true;
                }

                SolveRewardProperty solveReward = puzzle.getPuzzleProperty(SolveRewardProperty.class);
                if (solveReward != null && solveReward.getScores() != null && !solveReward.getScores().isEmpty()) {
                    huntStatusStore.mutateTeamProperty(
                            teamId,
                            ScoresProperty.class,
                            scores -> scores.addScores(solveReward.getScores())
                    );
                    scoreUpdated = true;
                }

                if (scoreUpdated) {
                    eventProcessor.process(ScoreUpdateEvent.builder()
                            .setTeamId(teamId)
                            .build());
                }
            } else if (visibility.getStatus().equals("UNLOCKED")) {
                Puzzle puzzle = puzzleStore.getPuzzle(visibility.getPuzzleId());
                InteractionProperty interactionProperty = puzzle.getPuzzleProperty(InteractionProperty.class);
                if (interactionProperty != null && interactionProperty.getInteraction()) {
                    // create a secret interaction
                    interactionRequestStore.createInteractionRequest(InteractionRequest.builder()
                            .setTeamId(teamId)
                            .setPuzzleId(visibility.getPuzzleId())
                            .setRequest("Schedule interaction")
                            .setInvisible(InteractionRequest.Invisible.YES)
                            .build());
                }
            }
            updateVisibility(teamId);
        });

        eventProcessor.addEventProcessor(ScoreUpdateEvent.class, event -> {
            updateVisibility(event.getTeamId());
        });

        eventProcessor.addEventProcessor(GrantScoreEvent.class, event -> {
            huntStatusStore.mutateTeamProperty(
                    event.getTeamId(),
                    ScoresProperty.class,
                    scores -> scores.addScores(ImmutableMap.of(event.getScoreType(), event.getScoreAmount()))
            );
            eventProcessor.process(ScoreUpdateEvent.builder()
                    .setTeamId(event.getTeamId())
                    .build());
        });

        eventProcessor.addEventProcessor(GoContentDeliveredEvent.class, event -> {
            huntStatusStore.setTeamProperty(
                    event.getTeamId(),
                    GoContentDeliveredProperty.class,
                    GoContentDeliveredProperty.create(true)
            );
        });

        eventProcessor.addEventProcessor(HintCompleteEvent.class, event -> {
            String teamId = event.getHintRequest().getTeamId();
            HintRequestStatus hintRequestStatus = event.getHintRequest().getStatus();
            if (hintRequestStatus == HintRequestStatus.REJECTED) {
                int buzzyBucksAmount;
                if (event.getHintRequest().getHintType() == HintRequest.HintType.HINT) {
                    buzzyBucksAmount = 20000;
                }
                else {
                    buzzyBucksAmount = 100000;
                }
                // Refund the team their Buzzy Bucks back.
                huntStatusStore.mutateTeamProperty(
                        teamId,
                        ScoresProperty.class,
                        scores -> scores.addScores(ImmutableMap.of(Score.BUZZY_BUCKS, buzzyBucksAmount))
                );
            }
        });

        eventProcessor.addEventProcessor(TrailingTideSingleRoundEvent.class, event -> {
            String teamId = event.getTeamId();

            Map<String, Puzzle> puzzles = puzzleStore.getPuzzles();
            Set<String> toUnlock = puzzles.keySet().stream().filter(puzzleId ->
                puzzleId.startsWith(event.getRoundPrefix() + "-") &&
                        !puzzleId.endsWith("-supermeta") &&
                        !puzzleId.endsWith("-recovery") &&
                        !puzzleId.endsWith("-runaround")
            ).collect(Collectors.toSet());

            ImmutableTable.Builder<String, String, String> b = new ImmutableTable.Builder<>();
            toUnlock.forEach(puzzleId -> b.put(teamId, puzzleId, "UNLOCKED"));
            huntStatusStore.setVisibilityBatch(b.build());

            // TODO: send email
        });

        eventProcessor.addEventProcessor(TrailingTideWholeIslandEvent.class, event -> {
            Set<String> teamIds = huntStatusStore.getTeamIds();
            int islandNumber = event.getIslandNumber();
            ImmutableList.Builder<Event> b = new ImmutableList.Builder<>();
            teamIds.forEach(teamId -> {
                String roundPrefix;
                if (islandNumber == 0) {
                    roundPrefix = "emo";
                } else {
                    IslandOpenOrderProperty orderProp = huntStatusStore.getTeamProperty(teamId, IslandOpenOrderProperty.class);
                    if (orderProp.getOpenOrder().size() < islandNumber) {
                        // team has not yet opened the Nth island
                        return;
                    }
                    roundPrefix = orderProp.getOpenOrder().get(islandNumber - 1);
                }

                b.add(TrailingTideSingleRoundEvent.builder()
                        .setTeamId(teamId)
                        .setRoundPrefix(roundPrefix)
                        .build());
            });
            eventProcessor.processBatch(b.build());
        });

        eventProcessor.addEventProcessor(PeriodicTimerEvent.class, event -> {
            Optional<Run> optionalRun = huntStatusStore.getHuntRunProperties();
            if (!optionalRun.isPresent()) {
                return;
            }

            Run run = optionalRun.get();
            Instant startTimestamp = run.getStartTimestamp();
            if (startTimestamp == null) {
                return;
            }

            Instant nowTimestamp = clock.instant();
            long seconds = Duration.between(startTimestamp, nowTimestamp).getSeconds();
            Set<String> teamIds = huntStatusStore.getTeamIds();

            // Trigger time-based visibility changes
            Table<String, String, String> teamPuzzleStatusTable = HashBasedTable.create();
            puzzleStore.getPuzzles().values().forEach(puzzle -> {
                VisibilityConstraint visibleConstraint = puzzle.getPuzzleProperty(VisibleConstraintProperty.class)
                        .getVisibleConstraint();
                VisibilityConstraint unlockedConstraint = puzzle.getPuzzleProperty(UnlockedConstraintProperty.class)
                        .getUnlockedConstraint();

                if (unlockedConstraint != null && unlockedConstraint.getTimeConstraint() != null) {
                    ZonedDateTime timeConstraint = unlockedConstraint.getTimeConstraint();
                    if (timeConstraint.isBefore(nowTimestamp.atZone(timeConstraint.getZone()))) {
                        teamIds.forEach(teamId -> teamPuzzleStatusTable.put(teamId, puzzle.getPuzzleId(), "UNLOCKED"));
                    }
                } else if (visibleConstraint != null && visibleConstraint.getTimeConstraint() != null) {
                    ZonedDateTime timeConstraint = visibleConstraint.getTimeConstraint();
                    if (timeConstraint.isBefore(nowTimestamp.atZone(timeConstraint.getZone()))) {
                        teamIds.forEach(teamId -> teamPuzzleStatusTable.put(teamId, puzzle.getPuzzleId(), "VISIBLE"));
                    }
                }
            });
            if (!teamPuzzleStatusTable.isEmpty()) {
                huntStatusStore.setVisibilityBatch(teamPuzzleStatusTable);
            }

            // Drip brainpower to teams
            //
            // We want to the total amount of dripped brainpower to be 23/228096000 t^2 + 1/220 t, where t is in
            // seconds since game start. To avoid skew, we track how much BP we've already dripped, figure out how much
            // we should have dripped, and drip the difference.

            // zarvox: kill the brainpower drip for now to buy time
            int expectedBrainpower = (int)(23.0/228096000 * seconds * seconds + 1.0/220 * seconds);
            final AtomicBoolean updatedScores = new AtomicBoolean();
            /*
            teamIds.forEach(teamId -> {
                huntStatusStore.mutateTeamProperty(teamId, ScoresProperty.class, scores -> {
                    int dripped = scores.getScores().getOrDefault(Score.BRAINPOWER_DRIP, 0);
                    int missing = expectedBrainpower - dripped;
                    if (missing != 0) {
                        updatedScores.set(true);
                    }
                    return scores.addScores(ImmutableMap.of(
                            Score.BRAINPOWER, missing,
                            Score.BRAINPOWER_DRIP, missing));
                });
            });
            */

            // Drip Buzzy Bucks to teams
            //
            // The amount of B$ we want to drip is a piecewise function. Prior to 31 hours in (111600 seconds), total
            // amount of dripped B$ should be 50/279 * t. After 31 hours, total amount of dripped B$ should be
            // 55000e^(17/900000*(t-111600))-35000
            int expectedBuzzyBucks = (seconds < 111600) ?
                (int)(50.0/279 * seconds) :
                (int)(55000 * Math.exp(17.0/900000 * (seconds - 111600)) - 35000);
            teamIds.forEach(teamId -> {
                huntStatusStore.mutateTeamProperty(teamId, ScoresProperty.class, scores -> {
                    int dripped = scores.getScores().getOrDefault(Score.BUZZY_BUCKS_DRIP, 0);
                    int missing = expectedBuzzyBucks - dripped;
                    if (missing != 0) {
                        updatedScores.set(true);
                    }
                    return scores.addScores(ImmutableMap.of(
                            Score.BUZZY_BUCKS, missing,
                            Score.BUZZY_BUCKS_DRIP, missing));
                });
            });

            if (updatedScores.get()) {
                eventProcessor.processBatch(teamIds.stream()
                        .map(id -> ScoreUpdateEvent.builder()
                                .setTeamId(id)
                                .build())
                        .collect(Collectors.toList()));
            }
        });

        // TODO: figure out what to do with these
        eventProcessor.addEventProcessor(PuzzlePropertyChangeEvent.class, event -> {});
    }

    @Override
    public boolean handleHintRequest(HintRequest hintRequest) {
        AtomicBoolean deductedBuzzyBucks = new AtomicBoolean(false);
        int buzzyBucksAmount;
        if (hintRequest.getHintType() == HintRequest.HintType.HINT) {
            buzzyBucksAmount = -20000;
        }
        else {
            buzzyBucksAmount = -100000;
        }
        huntStatusStore.mutateTeamProperty(
                hintRequest.getTeamId(),
                ScoresProperty.class,
                scores -> {
                    if (scores.getScores().get(Score.BUZZY_BUCKS) + buzzyBucksAmount >= 0) {
                        deductedBuzzyBucks.set(true);

                        return scores.addScores(ImmutableMap.of(Score.BUZZY_BUCKS, buzzyBucksAmount));
                    }
                    return scores;
                }
        );
        return deductedBuzzyBucks.get();
    }
}
