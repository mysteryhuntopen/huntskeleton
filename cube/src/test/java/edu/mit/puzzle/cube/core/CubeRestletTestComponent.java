package edu.mit.puzzle.cube.core;

import dagger.Component;
import edu.mit.puzzle.cube.core.db.ConnectionFactory;
import edu.mit.puzzle.cube.core.environments.ServiceEnvironment;
import edu.mit.puzzle.cube.core.events.CompositeEventProcessor;

import javax.inject.Singleton;

@Component(modules = {CubeModule.class, CubeRestletTestModule.class})
@Singleton
public interface CubeRestletTestComponent {
    HuntDefinition getHuntDefinition();
    ServiceEnvironment getServiceEnvironment();
    ConnectionFactory getConnectionFactory();
    CompositeEventProcessor getCompositeEventProcessor();

    void injectHuntDefinition(HuntDefinition huntDefinition);

    CubeResourceComponent.Builder getCubeResourceComponentBuilder();
}
