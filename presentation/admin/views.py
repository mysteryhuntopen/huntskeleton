from admin import app
from admin.interactions import PUZZLES_WITH_INSTRUCTIONS, ALL_PUZZLES_WITH_INTERACTIONS

from common import cube, login_required
from common import round_puzzle_map
from common.brainpower import calculate_brainpower_thresholds
from flask import abort, flash, redirect, render_template, request, session, url_for
from requests.exceptions import RequestException

import time

@app.context_processor
def utility_processor():
    def is_authorized(permission):
        return cube.authorized(app, permission)

    def current_timestamp():
        return int(time.time() * 1000)

    return {
        "is_authorized": is_authorized,
        "current_timestamp": current_timestamp,
    }

def get_puzzles():
    puzzles = cube.get_puzzles(app)
    def sortkey(puzzle):
        if "DisplayNameProperty" in puzzle["puzzleProperties"]:
            return puzzle["puzzleProperties"]["DisplayNameProperty"]["displayName"]
        else:
            return puzzle["puzzleId"]
    puzzles.sort(key=sortkey)
    return puzzles

def get_puzzle_id_to_puzzle():
    puzzles = cube.get_puzzles(app)
    return {puzzle['puzzleId']: puzzle for puzzle in puzzles}

@app.errorhandler(RequestException)
def handle_request_exception(error):
    return render_template(
        "error.html",
        error=error)

@app.route("/productionassets")
@login_required.writingteam
def productionassets():
    return render_template("production_assets.html")

@app.route("/")
@login_required.writingteam
def index():
    return render_template("index.html")

@app.route("/callqueue")
@login_required.writingteam
def callqueue():
    pending_submissions = cube.get_all_pending_submissions(app)
    puzzle_id_to_puzzle = get_puzzle_id_to_puzzle()

    return render_template(
        "callqueue.html",
        pending_submissions=pending_submissions,
        puzzle_id_to_puzzle=puzzle_id_to_puzzle)

@app.route("/interactionqueue")
@login_required.writingteam
def interactionqueue():
    pending_interaction_requests = cube.get_all_pending_interaction_requests(app)
    pending_hint_requests = cube.get_all_pending_hint_requests(app)
    puzzle_id_to_puzzle = get_puzzle_id_to_puzzle()

    pending_interaction_requests.sort(key=lambda r: r.get("timestamp", 0), reverse=True)

    return render_template(
        "interactionqueue.html",
        pending_hint_requests=pending_hint_requests,
        pending_interaction_requests=pending_interaction_requests,
        puzzle_id_to_puzzle=puzzle_id_to_puzzle)

@app.route("/submission/<int:submission_id>", methods=["GET", "POST"])
@login_required.writingteam
def submission(submission_id):
    if request.method == "POST":
        if "status" in request.form:
            try:
                cube.update_submission_status(app, submission_id, request.form["status"])
            except RequestException, e:
                if e.response is None:
                    raise e
                flash("Failed to update submission: %s" % e.response.json())
                return redirect(url_for("callqueue"))

            if request.form["status"] in ["SUBMITTED", "CORRECT", "INCORRECT"]:
                return redirect(url_for("callqueue"))
        else:
            abort(400)

    time.sleep(0.2)

    submission = cube.get_submission(app, submission_id)
    past_submissions = cube.get_submissions(app, submission['puzzleId'], submission['teamId'])
    past_submissions = [s for s in past_submissions if s['submissionId'] != submission_id]
    puzzle = cube.get_puzzle(app, submission['puzzleId'])
    team = cube.get_team_properties(app, team_id=submission['teamId'])

    return render_template(
        "submission.html",
        submission=submission,
        past_submissions=past_submissions,
        puzzle=puzzle,
        team=team,
        puzzles_with_instructions=PUZZLES_WITH_INSTRUCTIONS)

@app.route("/hintrequest/<int:hint_request_id>", methods=["GET", "POST"])
@login_required.writingteam
def hintrequest(hint_request_id):
    if request.method == "POST":
        if "status" in request.form:
            response = ""
            if "response" in request.form:
                response = request.form["response"]

            try:
                cube.update_hint_request(app, hint_request_id, request.form["status"], response)
            except RequestException, e:
                if e.response is None:
                    raise e
                flash("Failed to update hint request: %s" % e.response.json())
                return redirect(url_for("callqueue"))

            if request.form["status"] == "REQUESTED":
                return redirect(url_for("callqueue"))
        else:
            abort(400)

    hint_request = cube.get_hint_request(app, hint_request_id)
    puzzle = cube.get_puzzle(app, hint_request['puzzleId'])
    team = cube.get_team_properties(app, team_id=hint_request['teamId'])

    return render_template(
        "hintrequest.html",
        hint_request=hint_request,
        puzzle=puzzle,
        team=team)

@app.route("/interactionrequest/<int:interaction_request_id>", methods=["GET", "POST"])
@login_required.writingteam
def interactionrequest(interaction_request_id):
    if request.method == "POST":
        if "status" in request.form:
            response = ""
            if "response" in request.form:
                response = request.form["response"]

            try:
                cube.update_interaction_request(app, interaction_request_id, request.form["status"], response)
            except RequestException, e:
                if e.response is None:
                    raise e
                flash("Failed to update interaction request: %s" % e.response.json())
                return redirect(url_for("interactionqueue"))

            if request.form["status"] == "REQUESTED":
                return redirect(url_for("interactionqueue"))
        else:
            abort(400)

    interaction_request = cube.get_interaction_request(app, interaction_request_id)
    puzzle = cube.get_puzzle(app, interaction_request['puzzleId'])
    team = cube.get_team_properties(app, team_id=interaction_request['teamId'])

    return render_template(
        "interactionrequest.html",
        interaction_request=interaction_request,
        puzzle=puzzle,
        team=team,
        puzzles_with_instructions=PUZZLES_WITH_INSTRUCTIONS,
        island_discoveries=ISLAND_DISCOVERIES,
        all_puzzles_with_interactions=ALL_PUZZLES_WITH_INTERACTIONS)

@app.route("/teams", methods=["GET", "POST"])
@login_required.writingteam
def teams():
    if request.method == "POST":
        cube.create_team(app, {
            "teamId": request.form["teamId"],
            "teamName": request.form["teamName"],
            "password": request.form["password"],
            "email": request.form["email"],
            "headquarters": request.form["headquarters"],
            "primaryPhone": request.form["primaryPhone"],
            "secondaryPhone": request.form["secondaryPhone"],
        })

    teams = cube.get_teams(app)
    teams.sort(key=lambda t: t.get('teamId',''))

    return render_template(
        "teams.html",
        teams=teams)

@app.route("/team/<team_id>", methods=["GET", "POST"])
@login_required.writingteam
def team(team_id):
    if request.method == "POST":
        if request.form["action"] == "ChangeContactInfo":
            if ("email" not in request.form or
                "primaryPhone" not in request.form or
                "secondaryPhone" not in request.form):
                abort(400)

            update = {
                "teamId": team_id,
                "teamName": request.form["teamName"],
                "email": request.form["email"],
                "headquarters": request.form["headquarters"],
                "primaryPhone": request.form["primaryPhone"],
                "secondaryPhone": request.form["secondaryPhone"],
            }

            if request.form["password"] and (len(request.form["password"]) > 0):
                update["password"] = request.form["password"]

            cube.update_team(app, team_id, update)
        elif request.form["action"] == "SetPuzzleStatus":
            if request.form["actionType"] == "Unlock":
                status = "UNLOCKED"
            elif request.form["actionType"] == "Solve":
                status = "SOLVED"
            else:
                abort(400)
            cube.update_puzzle_visibility(
                app,
                team_id,
                request.form["puzzleId"],
                status)
        elif request.form["action"] == "GrantScore":
            cube.create_event(app, {
                "eventType": "GrantScore",
                "teamId": team_id,
                "scoreType": request.form['scoreType'],
                "scoreAmount": request.form["scoreAmount"],
            })
        elif request.form['action'] == 'TrailingTideRelease':
            cube.create_event(app, {
                'eventType': 'TrailingTideSingleRound',
                'teamId': team_id,
                'roundPrefix': request.form['roundPrefix'],
            })
        elif request.form["action"] == "GoContentDelivered":
            cube.create_event(app, {
                "eventType": "GoContentDelivered",
                "teamId": team_id,
            })
        else:
            abort(400)

    team = cube.get_team(app, team_id)
    puzzles = get_puzzle_id_to_puzzle()

    island_properties = cube.get_all_puzzle_properties_for_list(app, round_puzzle_map.ISLAND_UNLOCKS)
    brainpower_thresholds = calculate_brainpower_thresholds(island_properties)

    island_visibilities = cube.get_puzzle_visibilities_for_list(app, round_puzzle_map.ISLAND_IDS + round_puzzle_map.ISLAND_UNLOCKS, team_id)
    open_islands = [island for island in round_puzzle_map.ISLAND_IDS if island in island_visibilities and island_visibilities[island]['status'] in ['UNLOCKED', 'SOLVED']]

    completeable_puzzle_visibilities = cube.get_puzzle_visibilities_for_list(
        app,
        (round_puzzle_map.EMOTION_INTERACTIONS +
         round_puzzle_map.ISLAND_IDS +
         round_puzzle_map.ISLAND_RECOVERY_INTERACTIONS +
         round_puzzle_map.EVENTS +
         ['pokemon-unevolved-10'] +
         [round_puzzle_map.EMOTION_RUNAROUND] +
         round_puzzle_map.FINALES),
        team_id)

    all_visibilities_future = cube.get_puzzle_visibilities_async(app, team["teamId"])
    all_visibilities = all_visibilities_future.result().json()["visibilities"]
    visibilities_map = { v["puzzleId"]: v for v in all_visibilities }
    scores = team.get('teamProperties',{}).get('ScoresProperty',{}).get('scores',{})

    return render_template(
        "team.html",
        team=team,
        puzzles=puzzles,
        completeable_puzzle_visibilities=completeable_puzzle_visibilities,
        all_visibilities=visibilities_map,
        scores=scores,
        brainpower_thresholds=brainpower_thresholds,
        open_islands=open_islands,
        round_puzzle_map=round_puzzle_map,
        is_hunt_started=cube.is_hunt_started_async(app).result())

def build_roles_list(form):
    roles = []
    for key, value in form.iteritems():
        if key.startswith("role_") and value:
            roles.append(key[5:])
    return roles

@app.route("/users", methods=["GET", "POST"])
@login_required.writingteam
def users():
    if request.method == "POST":
        cube.create_user(app, {
            "username": request.form["username"],
            "password": request.form["password"],
            "roles": build_roles_list(request.form),
        })

    users = cube.get_users(app)

    return render_template(
        "users.html",
        users=users)

@app.route("/user/<username>", methods=["GET", "POST"])
@login_required.writingteam
def user(username):
    if request.method == "POST":
        update = {
            "username": username,
        }

        if cube.authorized(app, "userroles:update:%s" % username):
            user = cube.get_user(app, username)
            roles = build_roles_list(request.form)
            if user["roles"] != roles:
                update["roles"] = roles

        logout = False
        if request.form["password"]:
            update["password"] = request.form["password"]
            if username == session["username"]:
                logout = True

        cube.update_user(app, username, update)

        if logout:
            return redirect(url_for("login.logout"))
        return redirect(url_for("users"))

    user = cube.get_user(app, username)

    return render_template(
        "user.html",
        user=user)

@app.route("/admintools", methods=["GET", "POST"])
@login_required.writingteam
def admintools():
    if request.method == "POST":
        if request.form["action"] == "HuntStart":
            cube.create_event(app, {
                "eventType": "HuntStart",
            })
        elif request.form["action"] == "FullRelease":
            cube.create_event(app, {
                "eventType": "FullRelease",
                "puzzleId": request.form["puzzleId"],
            })
        elif request.form["action"] == "FullSolve":
            cube.create_event(app, {
                "eventType": "FullSolve",
                "puzzleId": request.form["puzzleId"],
            })
        elif request.form['action'] == 'TrailingTideRelease':
            cube.create_event(app, {
                'eventType': 'TrailingTideWholeIsland',
                'islandNumber': int(request.form['islandNumber']),
            })
        else:
            abort(400)

    return render_template(
        "admintools.html",
        puzzles=get_puzzles(),
        is_hunt_started=cube.is_hunt_started_async(app).result())

@app.route("/bigboard")
@login_required.writingteam
def bigboard():
    sortBy = request.args.get("sortBy", "metas")

    teams = cube.get_teams(app)

    team_visibility_futures = {}
    for team in teams:
        team_visibility_futures[team["teamId"]] = cube.get_puzzle_visibilities_async(app, team["teamId"])

    team_visibilities = {}
    for team_id, future in team_visibility_futures.iteritems():
        visibilities = future.result().json()["visibilities"]
        team_visibilities[team_id] = { v["puzzleId"]: v for v in visibilities }

    team_scores = { team['teamId']: team.get('teamProperties',{}).get('ScoresProperty',{}).get('scores',{}) \
                   for team in teams }

    team_metas_solved = get_puzzles_solved_by_team(teams, team_visibilities, [])

    team_supermetas_solved = get_puzzles_solved_by_team(teams, team_visibilities, [])

    if sortBy == "metas":
        teams.sort(key=lambda team: (team_metas_solved[team["teamId"]], team_scores[team["teamId"]].get('BRAINPOWER', 0)), reverse=True)
    elif sortBy == "brainpower":
        teams.sort(key=lambda team: (team_scores[team["teamId"]].get('BRAINPOWER', 0), team_metas_solved[team["teamId"]]), reverse=True)
    elif sortBy == "supermetas":
        teams.sort(key=lambda team: (team_supermetas_solved[team["teamId"]], team_metas_solved[team["teamId"]], team_scores[team["teamId"]].get('BRAINPOWER', 0)), reverse=True)

    return render_template(
        "bigboard.html",
        sortBy=sortBy,
        teams=teams,
        team_scores=team_scores,
        team_visibilities=team_visibilities,
        puzzles=get_puzzle_id_to_puzzle(),
        round_puzzle_map=round_puzzle_map)

@app.route("/bulk_team_action", methods=["GET", "POST"])
@login_required.writingteam
def bulk_team_action():
    if request.method == "POST":
        responses = []
        for team_id in request.form.getlist("team_ids"):
            if request.form.has_key("brainpower"):
                responses.append(cube.create_event_async(app, {
                    "eventType": "GrantScore",
                    "teamId": team_id,
                    "scoreType": "BRAINPOWER",
                    "scoreAmount": request.form["brainpower"],
                }))
            elif request.form.has_key("buzzyBucks"):
                responses.append(cube.create_event_async(app, {
                    "eventType": "GrantScore",
                    "teamId": team_id,
                    "scoreType": "BUZZY_BUCKS",
                    "scoreAmount": request.form["buzzyBucks"],
                }))
            elif request.form.has_key("solvePuzzle"):
                responses.append(cube.update_puzzle_visibility_async(
                    app,
                    team_id,
                    request.form["solvePuzzle"],
                    "SOLVED"))
            else:
                abort(400)
        for response in responses:
            response.result()

    teams = cube.get_teams(app)

    team_ids = [team["teamId"] for team in teams]
    team_ids.sort()

    team_names = {team["teamId"]: team.get("teamName","") for team in teams}

    team_scores = { team["teamId"]: team.get("teamProperties",{}).get("ScoresProperty",{}).get("scores",{}) for team in teams }

    team_visibility_futures = {}
    for team_id in team_ids:
        team_visibility_futures[team_id] = cube.get_puzzle_visibilities_for_list_async(
            app,
            round_puzzle_map.EVENTS,
            team_id)

    team_visibilities = {}
    for team_id, future in team_visibility_futures.iteritems():
        visibilities = future.result().json()["visibilities"]
        team_visibilities[team_id] = { v["puzzleId"]: v for v in visibilities }

    return render_template(
        "bulk_team_action.html",
        team_ids=team_ids,
        team_names=team_names,
        team_scores=team_scores,
        team_visibilities=team_visibilities,
        round_puzzle_map=round_puzzle_map)

def get_puzzles_solved_by_team(teams, team_visibilities, puzzle_list):
    return { team["teamId"]: sum(v["status"] == "SOLVED"
                                for v in team_visibilities[team["teamId"]].values()
                                if v["puzzleId"] in puzzle_list)
            for team in teams }
