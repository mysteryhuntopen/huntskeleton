package edu.mit.puzzle.cube.core;

import edu.mit.puzzle.cube.core.events.CompositeEventProcessor;
import edu.mit.puzzle.cube.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public abstract class HuntDefinition {
    @Inject public CompositeEventProcessor eventProcessor;
    @Inject public HintRequestStore hintRequestStore;
    @Inject public HuntStatusStore huntStatusStore;
    @Inject public InteractionRequestStore interactionRequestStore;
    @Inject public PuzzleStore puzzleStore;

    public abstract VisibilityStatusSet getVisibilityStatusSet();

    public abstract List<Puzzle> getPuzzles();

    /**
     * @return A newly constructed CompositeEventProcessor appropriate to the HuntDefinition.
     * Hunt definitions may override this in order to implement processBatch more efficiently
     * that the default implementation in EventProcessor.
     */
    public CompositeEventProcessor generateCompositeEventProcessor() {
        return new CompositeEventProcessor();
    }

    public abstract void addToEventProcessor();

    /**
     * @return true if this team may currently request a hint for this puzzle. Hunt definitions may
     * override this to implement behaviors such as:<p><ul>
     * <li>charging currency for this hint (amount may vary based on puzzle id and hunt state)
     * <li>only allowing hints for a subset of puzzles
     * </ul>
     */
    public boolean handleHintRequest(HintRequest hintRequest) {
        return true;
    }

    public static HuntDefinition forClassName(String className) {
        final Logger LOGGER = LoggerFactory.getLogger(HuntDefinition.class);
        HuntDefinition huntDefinition = null;
        try {
            @SuppressWarnings("unchecked")
            Class<HuntDefinition> huntDefinitionClass = (Class<HuntDefinition>) Class.forName(className);
            huntDefinition = huntDefinitionClass.newInstance();
            LOGGER.info("Using hunt definition {}", className);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Hunt definition class not found", e);
            System.exit(1);
        } catch (InstantiationException | IllegalAccessException e) {
            LOGGER.error("Failed to instantiate hunt definition", e);
            System.exit(1);
        }
        return huntDefinition;
    }
}
