package edu.mit.puzzle.cube.core;

import com.codahale.metrics.MetricRegistry;
import dagger.Component;
import edu.mit.puzzle.cube.core.db.ConnectionFactory;
import edu.mit.puzzle.cube.core.environments.ServiceEnvironment;
import edu.mit.puzzle.cube.core.events.CompositeEventProcessor;

import javax.inject.Singleton;

@Component(modules = {CubeModule.class, CubeConfigModule.class})
@Singleton
public interface CubeComponent {
    HuntDefinition getHuntDefinition();
    ServiceEnvironment getServiceEnvironment();
    ConnectionFactory getConnectionFactory();
    CompositeEventProcessor getCompositeEventProcessor();
    MetricRegistry getMetricRegistry();

    void injectHuntDefinition(HuntDefinition huntDefinition);

    CubeResourceComponent.Builder getCubeResourceComponentBuilder();
}
