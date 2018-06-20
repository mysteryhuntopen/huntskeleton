package edu.mit.puzzle.cube.core.serverresources;

import edu.mit.puzzle.cube.core.model.Run;

import org.restlet.resource.Get;

import java.util.Optional;

public class RunResource extends AbstractCubeResource {
    @Get
    public Run handleGet() {
        Optional<Run> run = huntStatusStore.getHuntRunProperties();
        if (run.isPresent()) {
            return run.get();
        }
        return Run.builder().build();
    }
}
