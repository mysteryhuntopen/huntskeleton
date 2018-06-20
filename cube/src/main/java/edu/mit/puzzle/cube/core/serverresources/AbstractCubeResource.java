package edu.mit.puzzle.cube.core.serverresources;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import edu.mit.puzzle.cube.core.events.Event;
import edu.mit.puzzle.cube.core.events.EventProcessor;
import edu.mit.puzzle.cube.core.model.HintRequestStore;
import edu.mit.puzzle.cube.core.model.HuntStatusStore;
import edu.mit.puzzle.cube.core.model.InteractionRequestStore;
import edu.mit.puzzle.cube.core.model.PuzzleStore;
import edu.mit.puzzle.cube.core.model.SubmissionStore;
import edu.mit.puzzle.cube.core.model.UserStore;
import edu.mit.puzzle.cube.core.permissions.SubjectUtils;

import org.apache.shiro.subject.Subject;
import org.restlet.representation.Representation;
import org.restlet.resource.ServerResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

public abstract class AbstractCubeResource extends ServerResource {
    @Inject EventProcessor<Event> eventProcessor;
    @Inject HintRequestStore hintRequestStore;
    @Inject HuntStatusStore huntStatusStore;
    @Inject InteractionRequestStore interactionRequestStore;
    @Inject PuzzleStore puzzleStore;
    @Inject SubmissionStore submissionStore;
    @Inject UserStore userStore;

    @Inject MetricRegistry metricRegistry;

    private static Map<Class<? extends AbstractCubeResource>, Timer> requestsTimers = new ConcurrentHashMap<>();

    private Timer getLatencyTimer() {
        Class<? extends AbstractCubeResource> klass = this.getClass();
        if (requestsTimers.get(klass) == null) {
            requestsTimers.put(klass, metricRegistry.timer(MetricRegistry.name(this.getClass(), "requests")));
        }
        return requestsTimers.get(klass);
    }

    @Override
    protected Representation doNegotiatedHandle() {
        Timer.Context timerContext = getLatencyTimer().time();
        try {
            return super.doNegotiatedHandle();
        } finally {
            timerContext.stop();
        }
    }

    protected Subject getSubject() {
        return SubjectUtils.getSubject(getRequest());
    }
}
