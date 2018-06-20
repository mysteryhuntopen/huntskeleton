package edu.mit.puzzle.cube.huntimpl.insideout2018;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.mit.puzzle.cube.core.model.Answer;
import edu.mit.puzzle.cube.core.model.Puzzle;

import java.time.ZonedDateTime;

class PuzzleBuilder {
    private String id;
    private String displayId;
    private String displayName;
    private HuntDefinition.InteractionProperty interactionProperty;
    private HuntDefinition.BrainpowerGroupProperty brainpowerGroup;
    private HuntDefinition.FeedersProperty feeders;
    private HuntDefinition.EmotionsProperty emotions;
    private HuntDefinition.SadnessProperty sadness;
    private HuntDefinition.AngerProperty anger;
    private HuntDefinition.SymbolProperty symbol;
    private HuntDefinition.RadioProperty radio;
    private HuntDefinition.ScifiDoorColorProperty scifiDoorColor;
    private HuntDefinition.ScifiDoorBoxesProperty scifiDoorBoxes;
    private HuntDefinition.TileProperty tile;
    private HuntDefinition.RoadsProperty roads;
    private HuntDefinition.ChitProperty chit;
    private HuntDefinition.VisibilityConstraint visibleConstraint;
    private HuntDefinition.VisibilityConstraint unlockedConstraint;
    private Puzzle.AnswersProperty answers;
    private HuntDefinition.SolveRewardProperty solveReward;

    private static final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");
    private static final HuntDefinition.VisibilityConstraint NO_CONSTRAINT = HuntDefinition.VisibilityConstraint.builder().build();

    private PuzzleBuilder() {
        this.visibleConstraint = NO_CONSTRAINT;
        this.unlockedConstraint = NO_CONSTRAINT;
    }

    static PuzzleBuilder builder() {
        return new PuzzleBuilder();
    }

    Puzzle build() {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(displayName);

        if (displayId == null) {
            displayId = id;
        }

        Puzzle.DisplayIdProperty displayIdProperty = Puzzle.DisplayIdProperty.create(
                displayId, DISPLAY_PROPERTY_ACCESS_STATUSES);
        Puzzle.DisplayNameProperty displayNameProperty = Puzzle.DisplayNameProperty.create(
                displayName, DISPLAY_PROPERTY_ACCESS_STATUSES);

        Puzzle.Builder builder = Puzzle.builder()
                .setPuzzleId(id)
                .addPuzzleProperty(Puzzle.DisplayIdProperty.class, displayIdProperty)
                .addPuzzleProperty(Puzzle.DisplayNameProperty.class, displayNameProperty)
                .addPuzzleProperty(HuntDefinition.VisibleConstraintProperty.class,
                        HuntDefinition.VisibleConstraintProperty.create(this.visibleConstraint))
                .addPuzzleProperty(HuntDefinition.UnlockedConstraintProperty.class,
                        HuntDefinition.UnlockedConstraintProperty.create(this.unlockedConstraint));

        if (this.answers != null) {
            builder = builder.addPuzzleProperty(Puzzle.AnswersProperty.class, answers);
        }
        if (this.interactionProperty != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.InteractionProperty.class, this.interactionProperty);
        }
        if (this.brainpowerGroup != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.BrainpowerGroupProperty.class, this.brainpowerGroup);
        }
        if (this.feeders != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.FeedersProperty.class, this.feeders);
        }
        if (this.solveReward != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.SolveRewardProperty.class, solveReward);
        }
        if (this.emotions != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.EmotionsProperty.class, this.emotions);
        }
        if (this.sadness != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.SadnessProperty.class, this.sadness);
        }
        if (this.anger != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.AngerProperty.class, this.anger);
        }
        if (this.symbol != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.SymbolProperty.class, this.symbol);
        }
        if (this.radio != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.RadioProperty.class, this.radio);
        }
        if (this.scifiDoorColor != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.ScifiDoorColorProperty.class, this.scifiDoorColor);
        }
        if (this.scifiDoorBoxes != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.ScifiDoorBoxesProperty.class, this.scifiDoorBoxes);
        }
        if (this.tile != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.TileProperty.class, this.tile);
        }
        if (this.roads != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.RoadsProperty.class, this.roads);
        }
        if (this.chit != null) {
            builder = builder.addPuzzleProperty(HuntDefinition.ChitProperty.class, this.chit);
        }

        return builder.build();
    }

    PuzzleBuilder setId(String id) {
        this.id = id;
        return this;
    }

    PuzzleBuilder setDisplayId(String displayId) {
        this.displayId = displayId;
        return this;
    }

    PuzzleBuilder setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    PuzzleBuilder setEmotions(ImmutableSet<HuntDefinition.Emotion> emotions) {
        this.emotions = HuntDefinition.EmotionsProperty.create(emotions);
        return this;
    }
    
    PuzzleBuilder setEmotion(HuntDefinition.Emotion emotion) {
        return setEmotions(ImmutableSet.of(emotion));
    }

    PuzzleBuilder setSadness(int sadness) {
        this.sadness = HuntDefinition.SadnessProperty.create(sadness);
        return this;
    }

    PuzzleBuilder setAnger(int anger) {
        this.anger = HuntDefinition.AngerProperty.create(anger);
        return this;
    }
    
    PuzzleBuilder setSymbol(String symbol) {
        this.symbol = HuntDefinition.SymbolProperty.create(symbol);
        return this;
    }
    
    PuzzleBuilder setRadio(String radio) {
        this.radio = HuntDefinition.RadioProperty.create(radio);
        return this;
    }
    
    PuzzleBuilder setScifiDoorColor(String color) {
    	this.scifiDoorColor = HuntDefinition.ScifiDoorColorProperty.create(color);
    	return this;
    }
    
    PuzzleBuilder setScifiDoorBoxes(int boxes) {
    	this.scifiDoorBoxes = HuntDefinition.ScifiDoorBoxesProperty.create(boxes);
    	return this;
    }
    
    PuzzleBuilder setTile(String tile) {
        this.tile = HuntDefinition.TileProperty.create(tile);
        return this;
    }
    
    PuzzleBuilder setRoads(ImmutableSet<HuntDefinition.Road> roads) {
        this.roads = HuntDefinition.RoadsProperty.create(roads);
        return this;
    }
    
    PuzzleBuilder setChit(int chit) {
        this.chit = HuntDefinition.ChitProperty.create(chit);
        return this;
    }

    PuzzleBuilder setVisibleConstraint(HuntDefinition.VisibilityConstraint visibilityConstraint) {
        this.visibleConstraint = visibilityConstraint;
        return this;
    }

    PuzzleBuilder setUnlockedConstraint(HuntDefinition.VisibilityConstraint visibilityConstraint) {
        this.unlockedConstraint = visibilityConstraint;
        return this;
    }

    PuzzleBuilder setInteraction() {
        this.interactionProperty = HuntDefinition.InteractionProperty.create(true);
        return this;
    }

    PuzzleBuilder setBrainpowerGroup(HuntDefinition.BrainpowerGroup brainpowerGroup) {
        this.brainpowerGroup = HuntDefinition.BrainpowerGroupProperty.create(brainpowerGroup);
        return this;
    }

    PuzzleBuilder setFeeders(ImmutableSet<String> feeders) {
        this.feeders = HuntDefinition.FeedersProperty.create(feeders);
        return this;
    }

    PuzzleBuilder setSolveReward(HuntDefinition.SolveRewardProperty solveReward) {
        this.solveReward = solveReward;
        return this;
    }

    PuzzleBuilder setAnswer(String answer) {
        this.answers = Puzzle.AnswersProperty.create(ImmutableList.of(Answer.create(answer)));
        return this;
    }

    PuzzleBuilder setRot13Answer(String rot13Answer) {
        int[] newCodePoints = rot13Answer.codePoints().map(c -> {
            if ((c >= 'A' && c <= 'M') || (c >= 'a' && c <= 'm')) {
                return c + 13;
            } else if ((c >= 'N' && c <= 'Z') || (c >= 'n' && c <= 'z')) {
                return c - 13;
            } else {
                return c;
            }
        }).toArray();
        return setAnswer(new String(newCodePoints, 0, newCodePoints.length));
    }
}
