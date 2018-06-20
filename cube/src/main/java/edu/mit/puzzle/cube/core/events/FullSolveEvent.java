package edu.mit.puzzle.cube.core.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

@AutoValue
@JsonDeserialize(builder = AutoValue_FullSolveEvent.Builder.class)
@JsonTypeName("FullSolve")
public abstract class FullSolveEvent extends Event {
    public String getEventType() {
        return FullSolveEvent.class.getSimpleName();
    }

    @AutoValue.Builder
    public static abstract class Builder {
        @JsonProperty("puzzleId") public abstract Builder setPuzzleId(String puzzleId);
        public abstract FullSolveEvent build();
    }

    public static Builder builder() {
        return new AutoValue_FullSolveEvent.Builder();
    }

    @JsonProperty("puzzleId") public abstract String getPuzzleId();
}
