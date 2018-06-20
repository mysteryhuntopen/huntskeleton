package edu.mit.puzzle.cube.core;

import edu.mit.puzzle.cube.core.serverresources.AbstractCubeResource;

import dagger.Subcomponent;

@Subcomponent
@RequestScoped
public interface CubeResourceComponent {
    @Subcomponent.Builder
    interface Builder {
        CubeResourceComponent build();
    }

    void injectCubeResource(AbstractCubeResource cubeResource);
}
