from functools import wraps
from flask import redirect, request, session, url_for

def maybe_redirect(usertype):
    if "username" not in session:
        session["after_login_url"] = request.url
        return redirect(url_for("login.login"))
    if "usertype" not in session or session["usertype"] != usertype:
        return redirect(url_for("login.wrongusertype"))
    return None

def anybody(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if 'username' not in session:
            session['after_login_url'] = request.url
            return redirect(url_for('login.login'))
        return f(*args, **kwargs)
    return decorated_function

def solvingteam(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        redirect_result = maybe_redirect("solvingteam")
        if redirect_result:
            return redirect_result
        return f(*args, **kwargs)
    return decorated_function

def writingteam(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        redirect_result = maybe_redirect("writingteam")
        if redirect_result:
            return redirect_result
        return f(*args, **kwargs)
    return decorated_function
