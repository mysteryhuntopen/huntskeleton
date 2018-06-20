from flask import Blueprint, redirect, render_template, request, session, url_for
from requests.exceptions import RequestException

import cube

blueprint = Blueprint("login", __name__, template_folder="templates", static_folder="loginstatic")

@blueprint.record
def record(setup_state):
    blueprint.config = setup_state.app.config

def clear_session():
    session.pop("username", None)
    session.pop("password", None)
    session.pop("usertype", None)
    session.pop("after_login_url", None)

@blueprint.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        username = str(request.form["username"].strip().lower()).translate(None, " &#+")
        session["username"] = username
        session["password"] = request.form["password"]
        try:
            all_teams_permission = cube.authorized(blueprint, "teams:read:*")
            if all_teams_permission:
                session["usertype"] = "writingteam"
            else:
                session["usertype"] = "solvingteam"
        except RequestException, e:
            clear_session()
            if e.response is None:
                return render_template(
                    "login.html",
                    error="Login failed - backend not available. %s" % e)
            if e.response.status_code == 401 or e.response.status_code == 403:
                return render_template(
                    "login.html",
                    error="Invalid login for user '%s'." % request.form["username"])
            raise e
        if "after_login_url" in session:
            after_login_url = session["after_login_url"]
            session.pop("after_login_url", None)
            return redirect(after_login_url)
        return redirect(url_for("index"))
    return render_template("login.html")

@blueprint.route("/logout")
def logout():
    clear_session()
    return redirect(url_for("index"))

@blueprint.route("/wrongusertype")
def wrongusertype():
    return render_template("wrongusertype.html")
