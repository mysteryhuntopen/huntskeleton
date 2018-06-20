package edu.mit.puzzle.cube.core.permissions;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.restlet.Request;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public class SubjectUtils {
    private static final String SUBJECT_KEY = "ShiroSubject";

    public static final void setSubject(Request request, UsernamePasswordToken token) {
        Subject subject = new Subject.Builder().buildSubject();
        subject.login(token);
        request.getAttributes().put(SUBJECT_KEY, subject);
    }

    public static final Subject getSubject(Request request) {
        Subject subject = (Subject) request.getAttributes().get(SUBJECT_KEY);
        if (subject == null) {
            throw new ResourceException(
                    Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Handling request without a security subject");
        }
        return subject;
    }
}
