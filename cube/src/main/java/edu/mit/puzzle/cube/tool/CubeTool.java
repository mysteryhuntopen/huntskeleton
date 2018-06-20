package edu.mit.puzzle.cube.tool;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.mit.puzzle.cube.core.CubeConfig;
import edu.mit.puzzle.cube.core.HuntDefinition;
import edu.mit.puzzle.cube.core.db.CubeDatabaseSchema;
import edu.mit.puzzle.cube.core.environments.ProductionEnvironment;
import edu.mit.puzzle.cube.core.environments.ServiceEnvironment;
import edu.mit.puzzle.cube.core.events.CompositeEventProcessor;
import edu.mit.puzzle.cube.core.model.Answer;
import edu.mit.puzzle.cube.core.model.Puzzle;
import edu.mit.puzzle.cube.core.model.Puzzle.DisplayIdProperty;
import edu.mit.puzzle.cube.core.model.PuzzleStore;
import edu.mit.puzzle.cube.core.model.User;
import edu.mit.puzzle.cube.core.model.UserStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class CubeTool {
    private final JCommander jCommander;
    private final CubeConfig cubeConfig;
    private final ServiceEnvironment environment;

    private CubeTool() {
        jCommander = new JCommander(this);
        jCommander.setProgramName(CubeTool.class.getSimpleName());

        cubeConfig = CubeConfig.readFromConfigJson();
        environment = new ProductionEnvironment(cubeConfig);
    }

    public static class NonEmptyStringValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            if (value.trim().isEmpty()) {
                throw new ParameterException("Parameter " + name + " must not be empty");
            }
        }
    }

    private interface Command {
        public void run();
    }

    @Parameters(
            commandNames = {"initdb"},
            commandDescription = "Initialize an empty database for use with Cube"
    )
    private class CommandInitDb implements Command {
        @Override
        public void run() {
            HuntDefinition huntDefinition = HuntDefinition.forClassName(
                    cubeConfig.getHuntDefinitionClassName()
            );
            CubeDatabaseSchema cubeDatabaseSchema = new CubeDatabaseSchema(
                    cubeConfig.getDatabaseConfig().getDriverClassName(),
                    huntDefinition.getVisibilityStatusSet()
            );
            PuzzleStore puzzleStore = new PuzzleStore(environment.getConnectionFactory(), null);
            try (
                    Connection connection = environment.getConnectionFactory().getConnection()
            ) {
                cubeDatabaseSchema.execute(connection);
                puzzleStore.initializePuzzles(huntDefinition.getPuzzles());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Parameters(
            commandNames = {"resethunt"},
            commandDescription = "Delete all run progress data from the database and reinit puzzles"
    )
    private class CommandResetHunt implements Command {
        @Override
        public void run() {
            HuntDefinition huntDefinition = HuntDefinition.forClassName(
                    cubeConfig.getHuntDefinitionClassName()
            );
            PuzzleStore puzzleStore = new PuzzleStore(environment.getConnectionFactory(), null);
            try (
                    Connection connection = environment.getConnectionFactory().getConnection();
                    PreparedStatement updateRun = connection.prepareStatement(
                            "UPDATE run SET startTimestamp = NULL");
                    PreparedStatement deleteTeamProperties = connection.prepareStatement(
                            "DELETE FROM team_properties");
                    PreparedStatement deleteSubmissions = connection.prepareStatement(
                            "DELETE FROM submissions");
                    PreparedStatement deleteVisibilities = connection.prepareStatement(
                            "DELETE FROM visibilities");
                    PreparedStatement deleteVisibilityHistory = connection.prepareStatement(
                            "DELETE FROM visibility_history");
                    PreparedStatement deletePuzzleProperties = connection.prepareStatement(
                            "DELETE FROM puzzle_properties");
                    PreparedStatement deletePuzzles = connection.prepareStatement(
                            "DELETE FROM puzzles");
            ) {
                updateRun.executeUpdate();
                deleteTeamProperties.executeUpdate();
                deleteSubmissions.executeUpdate();
                deleteVisibilities.executeUpdate();
                deleteVisibilityHistory.executeUpdate();
                deletePuzzleProperties.executeUpdate();
                deletePuzzles.executeUpdate();
                puzzleStore.initializePuzzles(huntDefinition.getPuzzles());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Parameters(
            commandNames = {"adduser"},
            commandDescription = "Create a new user"
    )
    private class CommandAddUser implements Command {
        @Parameter(
                names = {"-u", "--username"},
                description = "The username of the user to create",
                required = true,
                validateWith = NonEmptyStringValidator.class
        )
        String username;

        @Parameter(
                names = {"-p", "--password"},
                description = "The user's password",
                required = true,
                validateWith = NonEmptyStringValidator.class
        )
        String password;

        @Parameter(
                names = {"-r", "--role"},
                description = "The user's role [writingteam, admin]"
        )
        List<String> roles;

        @Override
        public void run() {
            UserStore userStore = new UserStore(environment.getConnectionFactory());
            User user = User.builder()
                    .setUsername(username.trim())
                    .setPassword(password.trim())
                    .setRoles(roles)
                    .build();
            userStore.addUser(user);
        }
    }

    @Parameters(
            commandNames = {"updatepuzzle"},
            commandDescription = "Update the display ID and display name for a puzzle"
    )
    private class CommandUpdatePuzzle implements Command {
        private final ImmutableSet<String> DISPLAY_PROPERTY_ACCESS_STATUSES = ImmutableSet.of("UNLOCKED", "SOLVED");

        @Parameter(
                names = {"--puzzleId"},
                description = "The canonical ID of the puzzle to update",
                required = true,
                validateWith = NonEmptyStringValidator.class
        )
        String puzzleId;

        @Parameter(
                names = {"--displayId"},
                description = "The new display ID for the puzzle",
                required = true,
                validateWith = NonEmptyStringValidator.class
        )
        String displayId;

        @Parameter(
                names = {"--displayName"},
                description = "The new display name for the puzzle",
                required = true,
                validateWith = NonEmptyStringValidator.class
        )
        String displayName;

        @Override
        public void run() {
            // Load HuntDefinition to force all puzzle property classes to be loaded.
            HuntDefinition.forClassName(
                    cubeConfig.getHuntDefinitionClassName()
            );

            PuzzleStore puzzleStore = new PuzzleStore(
                    environment.getConnectionFactory(),
                    new CompositeEventProcessor()
            );

            boolean changed = puzzleStore.setPuzzleProperty(
                    puzzleId,
                    Puzzle.DisplayIdProperty.class,
                    Puzzle.DisplayIdProperty.create(displayId, DISPLAY_PROPERTY_ACCESS_STATUSES)
            );
            if (!changed) {
                throw new RuntimeException("Failed to update display ID property for puzzle " + puzzleId);
            }

            changed = puzzleStore.setPuzzleProperty(
                    puzzleId,
                    Puzzle.DisplayNameProperty.class,
                    Puzzle.DisplayNameProperty.create(displayName, DISPLAY_PROPERTY_ACCESS_STATUSES)
            );
            if (!changed) {
                throw new RuntimeException("Failed to update display name property for puzzle " + puzzleId);
            }
        }
    }

    @Parameters(
            commandNames = {"generateanswerjson"},
            commandDescription = "Generate a JSON file containing hashes of all puzzle answers"
    )
    private class CommandGenerateAnswerJSON implements Command {
        @Override
        public void run() {
            // Load HuntDefinition to force all puzzle property classes to be loaded.
            HuntDefinition.forClassName(
                    cubeConfig.getHuntDefinitionClassName()
            );

            PuzzleStore puzzleStore = new PuzzleStore(
                    environment.getConnectionFactory(),
                    new CompositeEventProcessor()
            );

            Map<String, Puzzle> puzzles = puzzleStore.getPuzzles();
            ObjectNode puzzlesJson = JsonNodeFactory.instance.objectNode();
            for (Puzzle puzzle : puzzles.values()) {
                List<Answer> answers = puzzle.getAnswers();
                if (answers.size() == 0) {
                    continue;
                }
                String answer = answers.get(0).getCanonicalAnswer();
                String strippedAnswer = answer.toUpperCase().replaceAll("[^A-Z0-9]", "");
                DisplayIdProperty displayIdProperty = puzzle.getPuzzleProperty(DisplayIdProperty.class);
                String puzzleId;
                if (displayIdProperty != null) {
                    puzzleId = displayIdProperty.getDisplayId();
                } else {
                    puzzleId = puzzle.getPuzzleId();
                }
                puzzlesJson.put(puzzleId, strippedAnswer.hashCode());
            }

            ObjectMapper objectMapper = new ObjectMapper();
            try {
                System.out.print(objectMapper.writeValueAsString(puzzlesJson));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    private void run(String[] args) {
        Map<String, Command> commands = ImmutableMap.of(
                "initdb", new CommandInitDb(),
                "resethunt", new CommandResetHunt(),
                "adduser", new CommandAddUser(),
                "updatepuzzle", new CommandUpdatePuzzle(),
                "generateanswerjson", new CommandGenerateAnswerJSON()
        );
        for (Command command : commands.values()) {
            jCommander.addCommand(command);
        }
        try {
            jCommander.parse(args);
            String parsedCommand = jCommander.getParsedCommand();
            if (parsedCommand == null) {
                throw new RuntimeException("No command was specified");
            }
            if (commands.get(parsedCommand) == null) {
                throw new RuntimeException("Unrecognized command " + parsedCommand);
            }
            commands.get(parsedCommand).run();
        } catch (Exception e) {
            jCommander.usage();
            throw e;
        }
    }

    public static void main(String[] args) {
        new CubeTool().run(args);
    }
}
