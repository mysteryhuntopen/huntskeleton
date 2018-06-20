package edu.mit.puzzle.cube.core;

import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import edu.mit.puzzle.cube.core.db.ConnectionFactory;
import edu.mit.puzzle.cube.core.db.CubeJdbcRealm;
import edu.mit.puzzle.cube.core.events.PeriodicTimerEvent;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.engine.converter.ConverterHelper;
import org.restlet.ext.jackson.JacksonConverter;
import org.restlet.service.CorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CubeApplication extends Application {
    private static Logger LOGGER = LoggerFactory.getLogger(CubeApplication.class);

    private final CubeComponent dagger;

    private final Service timingEventService;

    public CubeApplication(CubeConfig config) {
        CorsService corsService = new CorsService();
        corsService.setAllowedOrigins(config.getCorsAllowedOrigins());
        corsService.setAllowedCredentials(true);
        corsService.setAllowingAllRequestedHeaders(true);
        getServices().add(corsService);
        
        setupJacksonConverter();

        setStatusService(new CubeStatusService(corsService));

        dagger = DaggerCubeComponent.builder()
                .cubeConfigModule(new CubeConfigModule(config))
                .build();

        setupAuthentication(dagger.getConnectionFactory());

        HuntDefinition huntDefinition = dagger.getHuntDefinition();
        dagger.injectHuntDefinition(huntDefinition);
        huntDefinition.addToEventProcessor();

        timingEventService = new AbstractScheduledService() {
            @Override
            protected void runOneIteration() throws Exception {
                try {
                    dagger.getCompositeEventProcessor().process(PeriodicTimerEvent.builder().build());
                } catch (Exception e) {
                    LOGGER.error("Failure while processing periodic timer event", e);
                }
            }

            @Override
            protected Scheduler scheduler() {
                return Scheduler.newFixedRateSchedule(0, 10, TimeUnit.MINUTES);
            }
        };
        timingEventService.startAsync();

        dagger.getMetricRegistry().registerAll(new GarbageCollectorMetricSet());
        dagger.getMetricRegistry().registerAll(new MemoryUsageGaugeSet());
        dagger.getMetricRegistry().registerAll(new ThreadStatesGaugeSet());
    }

    private void setupJacksonConverter() {
        List<ConverterHelper> converters = Engine.getInstance().getRegisteredConverters();
        for (ConverterHelper converter : converters) {
            if (converter instanceof JacksonConverter) {
                converters.remove(converter);
                break;
            }
        }

        converters.add(new CubeJacksonConverter());
    }

    private void setupAuthentication(ConnectionFactory connectionFactory) {
        CubeJdbcRealm realm = new CubeJdbcRealm();
        realm.setDataSource(connectionFactory.getDataSource());

        DefaultSecurityManager securityManager = new DefaultSecurityManager(realm);

        // Disable Shiro session storage.
        final DefaultSessionStorageEvaluator sessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        sessionStorageEvaluator.setSessionStorageEnabled(false);
        final DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        subjectDAO.setSessionStorageEvaluator(sessionStorageEvaluator);
        securityManager.setSubjectDAO(subjectDAO);

        SecurityUtils.setSecurityManager(securityManager);
    }

    @Override
    public synchronized Restlet createInboundRoot() {
        return new CubeRestlet(
                getContext(),
                dagger.getCubeResourceComponentBuilder().build(),
                dagger.getMetricRegistry()
        );
    }

    public static void main (String[] args) throws Exception {
        CubeConfig config = CubeConfig.readFromConfigJson();

        // Create a new Component.
        Component component = new Component();

        // Add a new HTTP server.
        component.getServers().add(Protocol.HTTP, config.getPort());

        // Attach this application.
        component.getDefaultHost().attach("", new CubeApplication(config));

        // Start the component.
        component.start();
    }

}
