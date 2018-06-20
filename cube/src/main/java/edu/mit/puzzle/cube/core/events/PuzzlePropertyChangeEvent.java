package edu.mit.puzzle.cube.core.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

import edu.mit.puzzle.cube.core.model.Puzzle;

@AutoValue
@JsonDeserialize(builder = AutoValue_PuzzlePropertyChangeEvent.Builder.class)
@JsonTypeName("PuzzlePropertyChange")
public abstract class PuzzlePropertyChangeEvent extends Event {
    public String getEventType() {
        return PuzzlePropertyChangeEvent.class.getSimpleName();
    }

    @AutoValue.Builder
    public static abstract class Builder {
        @JsonProperty("puzzle") public abstract Builder setPuzzle(Puzzle puzzle);
        public abstract PuzzlePropertyChangeEvent build();
    }

    public static Builder builder() {
        return new AutoValue_PuzzlePropertyChangeEvent.Builder();
    }

    @JsonProperty("puzzle") public abstract Puzzle getPuzzle();
}
