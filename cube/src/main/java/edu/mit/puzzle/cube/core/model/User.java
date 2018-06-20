package edu.mit.puzzle.cube.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

@AutoValue
@JsonDeserialize(builder = AutoValue_User.Builder.class)
public abstract class User {
    private static final Pattern USERNAME_VALIDATOR = Pattern.compile(
            "^[a-zA-Z0-9_\\-]+$");

    @AutoValue.Builder
    public static abstract class Builder {
        @JsonProperty("username") public abstract Builder setUsername(String username);

        @Nullable
        @JsonProperty("password") public abstract Builder setPassword(@Nullable String password);

        @Nullable
        @JsonProperty("roles") public abstract Builder setRoles(@Nullable List<String> roles);

        @Nullable
        @JsonProperty("teamId") public abstract Builder setTeamId(@Nullable String teamId);

        public abstract User build();
    }

    public static Builder builder() {
        return new AutoValue_User.Builder();
    }

    public abstract Builder toBuilder();

    @JsonProperty("username") public abstract String getUsername();

    @Nullable
    @JsonProperty("password")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract String getPassword();

    @Nullable
    @JsonProperty("roles")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract List<String> getRoles();

    @Nullable
    @JsonProperty("teamId")
    public abstract String getTeamId();

    public void validate() {
        if (getUsername() != null) {
            if (!USERNAME_VALIDATOR.matcher(getUsername()).matches()) {
                throw new ResourceException(
                        Status.CLIENT_ERROR_BAD_REQUEST.getCode(),
                        String.format("Invalid username: %s", getUsername()));
            }
        }
    }
}
