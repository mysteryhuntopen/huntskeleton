package edu.mit.puzzle.cube.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
@JsonDeserialize(builder = AutoValue_InteractionRequests.Builder.class)
public abstract class InteractionRequests {
	@AutoValue.Builder
	public static abstract class Builder {
		@JsonProperty("interactionRequests") public abstract Builder setInteractionRequests(List<InteractionRequest> interactionRequests);
		public abstract InteractionRequests build();
	}

	public static Builder builder() {
		return new AutoValue_InteractionRequests.Builder();
	}

	@JsonProperty("interactionRequests") public abstract List<InteractionRequest> getInteractionRequests();
}
