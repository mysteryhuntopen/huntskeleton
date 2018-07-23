package edu.mit.puzzle.cube.core;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import edu.mit.puzzle.cube.core.permissions.SubjectUtils;
import edu.mit.puzzle.cube.core.serverresources.*;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.resource.Finder;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.Verifier;

import java.util.List;

public class CubeRestlet extends Filter {
    public CubeRestlet(
            Context context,
            CubeResourceComponent dagger,
            MetricRegistry metricRegistry
    ) {
        super(context);

        Router router = new Router(context) {
            @Override
            public Finder createFinder(Class<? extends ServerResource> targetClass) {
                Finder finder = super.createFinder(targetClass);
                return new Finder(finder.getContext(), finder.getTargetClass()) {
                    @Override
                    public ServerResource find(Request request, Response response) {
                        ServerResource res = finder.find(request, response);
                        if (res instanceof AbstractCubeResource) {
                            dagger.injectCubeResource((AbstractCubeResource) res);
                        }
                        return res;
                    }
                };
            }
        };

        router.attach("/authorized", AuthorizedResource.class);
        router.attach("/events", EventsResource.class);
        router.attach("/hintrequests", HintRequestsResource.class);
        router.attach("/hintrequests/{id}", HintRequestResource.class);
        router.attach("/interactionrequests", InteractionRequestsResource.class);
        router.attach("/interactionrequests/{id}", InteractionRequestResource.class);
        router.attach("/puzzles", PuzzlesResource.class);
        router.attach("/puzzles/{id}", PuzzleResource.class);
        router.attach("/run", RunResource.class);
        router.attach("/submissions", SubmissionsResource.class);
        router.attach("/submissions/{id}", SubmissionResource.class);
        router.attach("/teams", TeamsResource.class);
        router.attach("/teams/{id}", TeamResource.class);
        router.attach("/users", UsersResource.class);
        router.attach("/users/{id}", UserResource.class);
        router.attach("/visibilities", VisibilitiesResource.class);
        router.attach("/visibilities/{teamId}/{puzzleId}", VisibilityResource.class);
        router.attach("/visibilitychanges", VisibilityChangesResource.class);

        // Create an authenticator for all routes.
        ChallengeAuthenticator authenticator = new ChallengeAuthenticator(
                context,
                ChallengeScheme.HTTP_BASIC,
                "Cube"
        );
        authenticator.setVerifier((Request request, Response response) -> {
            if (request.getMethod().equals(Method.OPTIONS)) {
                return Verifier.RESULT_VALID;
            }

            ChallengeResponse challengeResponse = request.getChallengeResponse();
            if (challengeResponse == null) {
                throw new AuthenticationException(
                        "Credentials are required, but none were provided.");
            }

            UsernamePasswordToken token = new UsernamePasswordToken(
                    challengeResponse.getIdentifier(),
                    challengeResponse.getSecret()
            );
            SubjectUtils.setSubject(request, token);

            return Verifier.RESULT_VALID;
        });
        authenticator.setNext(router);

        final String cubeRequestsTimerKey = MetricRegistry.name(this.getClass(), "requests");
        final Timer requestsTimer = metricRegistry.timer(cubeRequestsTimerKey);
        Filter timingMetricFilter = new Filter(context) {
            @Override
            protected int beforeHandle(Request request, Response response) {
                Timer.Context timerContext = requestsTimer.time();
                request.getAttributes().put(cubeRequestsTimerKey, timerContext);
                return CONTINUE;
            }

            @Override
            protected void afterHandle(Request request, Response response) {
                Timer.Context timerContext = (Timer.Context) request.getAttributes().get(cubeRequestsTimerKey);
                timerContext.stop();
            }
        };
        timingMetricFilter.setNext(authenticator);

        final Histogram prometheusLatencyHistogram = Histogram.build()
                .name("response_latency_seconds")
                .help("Cube response latency in seconds.")
                .labelNames("method")
                .exponentialBuckets(0.005, 1.45, 20)
                .register();
        final Counter prometheusStatusCodeCounter = Counter.build()
                .name("status_codes")
                .help("Cube response status codes.")
                .labelNames("method", "code")
                .register();
        Filter prometheusMetricsFilter = new Filter(context) {
            private static final String TIMER_ATTRIBUTE_KEY = "response_latency_seconds_timer";

            private String getMethod(Request request) {
                List<String> segments = request.getResourceRef().getSegments();
                if (segments.isEmpty()) {
                    return "";
                }
                return segments.get(0);
            }

            @Override
            protected int beforeHandle(Request request, Response response) {
                Histogram.Timer timer = prometheusLatencyHistogram.labels(getMethod(request)).startTimer();
                request.getAttributes().put(TIMER_ATTRIBUTE_KEY, timer);
                return CONTINUE;
            }

            @Override
            protected void afterHandle(Request request, Response response) {
                Histogram.Timer timer = (Histogram.Timer) request.getAttributes().get(TIMER_ATTRIBUTE_KEY);
                timer.observeDuration();
                prometheusStatusCodeCounter.labels(
                        getMethod(request),
                        Integer.toString(response.getStatus().getCode())
                ).inc();
            }
        };
        prometheusMetricsFilter.setNext(timingMetricFilter);

        setNext(prometheusMetricsFilter);
    }
}
