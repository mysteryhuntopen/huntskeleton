package edu.mit.puzzle.cube.core;

import com.codahale.metrics.graphite.Graphite;
import dagger.Module;
import dagger.Provides;
import edu.mit.puzzle.cube.core.environments.DevelopmentEnvironment;
import edu.mit.puzzle.cube.core.environments.ServiceEnvironment;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;

@Module
public class CubeRestletTestModule {
    private final HuntDefinition huntDefinition;

    CubeRestletTestModule(HuntDefinition huntDefinition) {
        this.huntDefinition = huntDefinition;
    }

    @Provides
    @Singleton
    HuntDefinition provideHuntDefinition() {
        return huntDefinition;
    }

    @Provides
    @Singleton
    ServiceEnvironment provideServiceEnvironment(HuntDefinition huntDefinition) {
        return new DevelopmentEnvironment(huntDefinition);
    }

    @Provides
    Optional<Graphite> provideGraphite() {
        return Optional.empty();
    }

    @Provides
    @Named("graphitePrefix")
    Optional<String> provideGraphitePrefix() {
        return Optional.empty();
    }
}
