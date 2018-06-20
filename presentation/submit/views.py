from submit import app

from common import cube, login_required
from common.round_puzzle_map import ISLAND_UNLOCKS, ISLAND_IDS, ISLANDS_AND_PLACEHOLDERS
from flask import abort, make_response, redirect, render_template, request, session, url_for
from requests.exceptions import HTTPError
from werkzeug.exceptions import default_exceptions

import pyformance
import collections
import re

@app.errorhandler(Exception)
def handle_exception(error):
    if app.config['DEBUG']:
        raise

    error_string = str(error)
    status_code = 500
    pyformance.global_registry().counter("present.error.500").inc()

    return make_response(
        render_template(
            "error.html",
            error=error_string),
        status_code)

@app.errorhandler(HTTPError)
def handle_requests_error(error):
    status_code = error.response.status_code
    pyformance.global_registry().counter("present.error.%d" % error.response.status_code).inc()

    if status_code != 500 and status_code in default_exceptions:
        description = default_exceptions[status_code].description
    elif app.config['DEBUG']:
        raise
    else:
        status_code = 500
        description = default_exceptions[500].description

    return make_response(
        render_template(
            'error.html',
            error=description),
        status_code)

@app.errorhandler(403)
def handle_forbidden(error):
    pyformance.global_registry().counter("submit.error.403").inc()
    return make_response(
        render_template(
            "error.html",
            error=error),
        403)

@app.errorhandler(404)
def handle_not_found(error):
    pyformance.global_registry().counter("submit.error.404").inc()
    return make_response(
        render_template(
            "error.html",
            error=error),
        404)

@app.errorhandler(500)
def handle_internal_server_error(error):
    response.status_code = 500
    pyformance.global_registry().counter("submit.error.500").inc()
    return make_response(
        render_template(
            "error.html",
            error=error),
        500)

@app.context_processor
def utility_processor():
    def puzzle_url_for(puzzle_id):
        return '/puzzle/' + puzzle_id

    def pretty_title(title):
        title = re.sub('(_+)','<span class="answer-blank">\\1</span>',title)
        title = re.sub("'", "&rsquo;",title) #Assumes there are no enclosed single quotes, i.e., all of these are apostrophes
        title = re.sub('^\\.\\.\\.', '.&nbsp;.&nbsp;.&nbsp;',title)
        title = re.sub('\\.\\.\\.', '&nbsp;.&nbsp;.&nbsp;.',title)
        return title

    return dict(puzzle_url_for=puzzle_url_for,pretty_title=pretty_title)

@app.route("/puzzle/<puzzle_id>", methods=["GET", "POST"])
@login_required.solvingteam
def puzzle(puzzle_id):
    if (app.config["SITE_MODE"] if app.config["SITE_MODE"] else 'live') not in ['live']:
        abort(403)
      
    if not cube.is_puzzle_unlocked(app, puzzle_id):
        abort(403)

    island_visibilities = cube.get_puzzle_visibilities_for_list(app, ISLAND_IDS + ISLAND_UNLOCKS)
    open_islands = [island for island in ISLAND_IDS if island in island_visibilities and island_visibilities[island]['status'] in ['UNLOCKED', 'SOLVED']]

    island_properties = cube.get_all_puzzle_properties_for_list(app, ISLAND_UNLOCKS)
    island_ids = [island_properties[island].get('puzzleProperties', {}).get('DisplayIdProperty', {}).get('displayId', island) for island in island_properties ]

    island_unlocks = [island_properties[island].get('puzzleProperties',{}).get('DisplayIdProperty',{}).get('displayId','')
                         for island in ISLAND_UNLOCKS if island_visibilities[island].get('status') == "UNLOCKED"]

    if puzzle_id in island_unlocks and island_unlocks.index(puzzle_id) < len(open_islands):
        abort(403)

    if request.method == "POST":
        if "submission" in request.form:
            cube.create_submission(app, puzzle_id, request.form["submission"])
            return redirect(url_for('puzzle', puzzle_id = puzzle_id))
        elif "hintrequest" in request.form:
            cube.create_hint_request(app, puzzle_id, request.form["hintrequest"], request.form["hinttype"])
            return redirect(url_for('puzzle', puzzle_id = puzzle_id))
        elif "interactionrequest" in request.form:
            cube.create_interaction_request(app, puzzle_id, request.form["interactionrequest"])
            return redirect(url_for('puzzle', puzzle_id = puzzle_id))
        elif "islandunlock" in request.form:
            cube.create_interaction_request(app, puzzle_id, request.form["islandunlock"])
            return redirect(url_for('puzzle', puzzle_id = puzzle_id))
        else:
            abort(400)

    submissions = cube.get_submissions(app, puzzle_id)
    puzzle = cube.get_puzzle(app, puzzle_id)
    visibility = cube.get_puzzle_visibility(app, puzzle_id)
    hints = cube.get_hints(app, puzzle_id)
    interactions = [i for i in cube.get_interactions(app, puzzle_id) if i['invisible'] != 'YES']
    team_properties = cube.get_team_properties(app)

    closed_islands = [ island for island in ISLAND_IDS if (island not in island_visibilities or island_visibilities[island]['status'] in ['INVISIBLE']) ]

    url = '';
    if (puzzle_id in island_ids):
        url = "unlock_island.html"
    else:
        url="puzzle.html"

    r = make_response(
        render_template(
            url,
            puzzle_id=puzzle_id,
            puzzle=puzzle,
            submissions=submissions,
            visibility=visibility,
            hints=hints,
            interactions=interactions,
            team_properties=team_properties,
            closed_islands=closed_islands,
            placeholders=dict(ISLANDS_AND_PLACEHOLDERS)))
    r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
    return r
