package edu.mit.puzzle.cube.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
@JsonDeserialize(builder = AutoValue_Puzzles.Builder.class)
public abstract class Puzzles {
    @AutoValue.Builder
    public static abstract class Builder {
        @JsonProperty("puzzles") public abstract Builder setPuzzles(List<Puzzle> puzzles);
        public abstract Puzzles build();
    }

    public static Builder builder() {
        return new AutoValue_Puzzles.Builder();
    }

    @JsonProperty("puzzles") public abstract List<Puzzle> getPuzzles();

}
