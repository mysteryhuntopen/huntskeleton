from present import app

from common import cube, login_required, metrics
from common.round_puzzle_map import EMOTION_IDS, ISLAND_IDS, OBJECTIVE_PUZZLES, ISLAND_RECOVERY_INTERACTIONS, POKEMON_SUBMETAS, SCIFI_SUBMETAS, HACKING_SUBMETAS, HACKING_RUNAROUNDS, EVENTS, FINALES, ROUND_PUZZLE_MAP, ISLANDS_AND_PLACEHOLDERS, ISLAND_UNLOCKS, EMOTION_INTERACTIONS, INTERACTIONS_AND_FINALES
from common.brainpower import calculate_brainpower_thresholds
from flask import abort, make_response, redirect, render_template, request, send_from_directory, session, url_for, jsonify
from werkzeug.exceptions import default_exceptions
import jinja2
import pyformance
from requests.exceptions import HTTPError
from datetime import datetime
from pytz import timezone
import jwt

import collections
import os
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
    pyformance.global_registry().counter("present.error.403").inc()
    return make_response(
        render_template(
            "error.html",
            error=error),
        403)

@app.errorhandler(404)
def handle_not_found(error):
    pyformance.global_registry().counter("present.error.404").inc()
    return make_response(
        render_template(
            "error.html",
            error=error),
        404)

@app.errorhandler(500)
def handle_internal_server_error(error):
    pyformance.global_registry().counter("present.error.500").inc()
    return make_response(
        render_template(
            "error.html",
            error=error),
        500)

@app.context_processor
def utility_processor():
    def submit_url_for(puzzle_id):
        return app.config["SUBMIT_URL"] % puzzle_id

    def pretty_title(title):
      title = re.sub('(_+)','<span class="answer-blank">\\1</span>',title)
      title = re.sub("'", "&rsquo;",title) #Assumes there are no enclosed single quotes, i.e., all of these are apostrophes
      title = title.replace('...', '&hellip;')
      return title

    def site_mode():
      return app.config["SITE_MODE"] if app.config["SITE_MODE"] else 'live'

    def pretty_truncate(value, length):
      if len(value) <= length:
        return value
      return value[0:length] + '...'

    return dict(
        submit_url_for=submit_url_for,
        pretty_title=pretty_title,
        site_mode=site_mode,
        pretty_truncate=pretty_truncate)

def make_core_display_data(team_properties_async):
    try:
        team_properties = team_properties_async.result().json()
    except HTTPError as e:
        return get_full_path_core_display_data()

    island_visibilities_async = cube.get_puzzle_visibilities_for_list_async(app, ISLAND_IDS + ISLAND_UNLOCKS)
    island_properties_async = cube.get_all_puzzle_properties_for_list_async(app, ISLAND_UNLOCKS)

    island_visibilities = { v["puzzleId"]: v for v in island_visibilities_async.result().json().get("visibilities",[]) }
    island_properties = {puzzle.get('puzzleId'): puzzle for puzzle in island_properties_async.result().json().get('puzzles',[])}
    open_islands = [island for island in ISLAND_IDS if island in island_visibilities and island_visibilities[island]['status'] in ['UNLOCKED', 'SOLVED']]
    island_unlocks = [island_properties[island].get('puzzleProperties',{}).get('DisplayIdProperty',{}).get('displayId','')
                     for island in ISLAND_UNLOCKS if island in island_visibilities and island_visibilities[island].get('status') == "UNLOCKED"]

    core_display_data = { }
    core_display_data['brainpower'] = team_properties.get('teamProperties',{}).get('ScoresProperty',{}).get('scores',{}).get('BRAINPOWER',0)
    core_display_data['buzzyBucks'] = team_properties.get('teamProperties',{}).get('ScoresProperty',{}).get('scores',{}).get('BUZZY_BUCKS',0)
    core_display_data['teamName'] = team_properties.get('teamName',session['username'])
    core_display_data['email'] = team_properties.get('email','')
    core_display_data['primaryPhone'] = team_properties.get('primaryPhone','')
    core_display_data['secondaryPhone'] = team_properties.get('secondaryPhone','')
    # Puzzle pokemon-unevolved-10, a.k.a. 'Go!', needs the puzzle page to look different before and after
    # a physical prop is handed off to solvers. This property tracks whether this prop has been handed off.
    core_display_data['goContentDelivered'] = team_properties.get('teamProperties',{}).get('GoContentDeliveredProperty',{}).get('goContentDelivered')
    # We want to look at this in enough places that we want to just get it all the time.
    core_display_data['openIslands'] = open_islands
    core_display_data['brainpowerThresholds'] = calculate_brainpower_thresholds(island_properties)
    core_display_data['islandUnlocks'] = island_unlocks

    return core_display_data

def get_full_path_core_display_data():
    core_display_data = { }
    core_display_data['brainpower'] = 0;
    core_display_data['buzzyBucks'] = 0;
    core_display_data['teamName'] = session['username']
    core_display_data['email'] = ''
    core_display_data['primaryPhone'] = ''
    core_display_data['secondaryPhone'] = ''
    core_display_data['goContentDelivered'] = 'DELIVERED'
    core_display_data['openIslands'] = ISLAND_IDS
    return core_display_data

def generate_puzzle_token_json(puzzle):
    canonical_puzzle_id = puzzle.get('puzzleId')

    secret = app.config["PUZZLE_JWT_SECRET"]
    if not secret:
        print 'Error: No PUZZLE_JWT_SECRET'
        abort(500)

    return jsonify({
        "username": session['username'],
        "jwt": jwt.encode({
            "username": session['username'],
            "puzzle": canonical_puzzle_id
        }, secret, app.config["PUZZLE_JWT_ALGO"])
    })

@app.route("/")
@login_required.solvingteam
@metrics.time("present.index")
def index():
    core_team_properties_async = cube.get_team_properties_async(app)
    is_hunt_started_async = cube.is_hunt_started_async(app)

    core_display_data = make_core_display_data(core_team_properties_async)

    if not is_hunt_started_async.result():
        return prehunt_index(core_display_data=core_display_data)

    round_puzzle_ids = ROUND_PUZZLE_MAP.get('memories') + ISLAND_IDS + FINALES + ISLAND_RECOVERY_INTERACTIONS

    puzzle_visibilities_async = cube.get_puzzle_visibilities_for_list_async(app, round_puzzle_ids)
    puzzle_properties_async = cube.get_all_puzzle_properties_for_list_async(app, round_puzzle_ids)

    puzzle_visibilities = { v["puzzleId"]: v for v in puzzle_visibilities_async.result().json().get("visibilities",[]) }
    puzzle_properties = {puzzle.get('puzzleId'): puzzle for puzzle in puzzle_properties_async.result().json().get('puzzles',[])}

    with metrics.timer("present.index_render"):
        r = make_response(
            render_template(
                "index.html",
                core_display_data=core_display_data,
                islands=ISLAND_IDS,
                is_hunt_started=True,
                puzzle_properties=puzzle_properties,
                puzzle_visibilities=puzzle_visibilities))
        r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
        return r

def prehunt_index(core_display_data):
    return render_template(
        "prehunt_index.html",
        is_hunt_started=False,
        core_display_data=core_display_data)

@app.route("/memory/<island_id>")
@login_required.solvingteam
@metrics.time("present.memory")
def memory(island_id):
    gate = 'grand-finale' if island_id == 'mysteryhunt' else island_id + '-recovery'
    memory_visibility_async = cube.get_puzzle_visibility_async(app, gate)
    core_team_properties_async = cube.get_team_properties_async(app)

    memory_visibility = memory_visibility_async.result().json()
    if memory_visibility['status'] != 'SOLVED':
        abort(403)

    core_display_data = make_core_display_data(core_team_properties_async)

    with metrics.timer("present.memory_render"):
        return render_template(
            "core_memory.html",
            core_display_data=core_display_data,
            is_hunt_started=True,
            island_id=island_id)

@app.route("/island/<island_id>")
@login_required.solvingteam
@metrics.time("present.island")
def island(island_id):
    island_puzzle_ids = ROUND_PUZZLE_MAP.get(island_id,[]) + ISLAND_IDS

    island_visibility_async = cube.get_puzzle_visibility_async(app, island_id)
    core_team_properties_async = cube.get_team_properties_async(app)
    puzzle_visibilities_async = cube.get_puzzle_visibilities_for_list_async(app, island_puzzle_ids)
    puzzle_properties_async = cube.get_all_puzzle_properties_for_list_async(app, island_puzzle_ids)

    island_visibility = island_visibility_async.result().json()
    if island_visibility['status'] not in ['UNLOCKED','SOLVED']:
        abort(403)

    core_display_data = make_core_display_data(core_team_properties_async)
    puzzle_visibilities = { v["puzzleId"]: v for v in puzzle_visibilities_async.result().json().get("visibilities",[]) }
    puzzle_properties = {puzzle.get('puzzleId'): puzzle for puzzle in puzzle_properties_async.result().json().get('puzzles',[])}

    with metrics.timer("present.island_render"):
        r = make_response(
            render_template(
                "islands/%s.html" % island_id,
                core_display_data=core_display_data,
                is_hunt_started=True,
                island_id=island_id,
                puzzle_properties=puzzle_properties,
                puzzle_visibilities=puzzle_visibilities))
        r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
        return r

@app.route("/puzzle/<puzzle_id>")
@login_required.solvingteam
@metrics.time("present.puzzle")
def puzzle(puzzle_id):
    puzzle_visibility_async = cube.get_puzzle_visibility_async(app, puzzle_id)
    core_team_properties_async = cube.get_team_properties_async(app)
    puzzle_async = cube.get_puzzle_async(app, puzzle_id)
    island_visibilities_async = cube.get_puzzle_visibilities_for_list_async(app, ISLAND_UNLOCKS + ISLAND_IDS)
    island_properties_async = cube.get_all_puzzle_properties_for_list_async(app, ISLAND_UNLOCKS)

    puzzle_visibility = puzzle_visibility_async.result().json()
    if puzzle_visibility['status'] not in ['UNLOCKED','SOLVED'] and app.config["SITE_MODE"] != 'solution':
        abort(403)

    interactions_and_finales_async = cube.get_all_puzzle_properties_for_list_async(app, INTERACTIONS_AND_FINALES)
    interactions_finales = interactions_and_finales_async.result().json().get("puzzles")
    interactions_finales = { v["puzzleId"]: v for v in interactions_finales }
    interactions_finales = [ interactions_finales[interaction_finale].get('puzzleProperties',{}).get('DisplayIdProperty',{}).get('displayId',interaction_finale)
                            for interaction_finale in interactions_finales ]
    if puzzle_id in interactions_finales and app.config["SITE_MODE"] != 'solution':
        abort(403)

    island_visibilities = {r['puzzleId']: r for r in island_visibilities_async.result().json()['visibilities']}
    island_visibilities = collections.defaultdict(lambda: {'status': 'INVISIBLE'}, island_visibilities)
    island_properties = {island.get('puzzleId'): island for island in island_properties_async.result().json().get('puzzles',[])}
    island_unlocks = [island_properties[island].get('puzzleProperties',{}).get('DisplayIdProperty',{}).get('displayId','')
                         for island in ISLAND_UNLOCKS if island_visibilities[island].get('status') == "UNLOCKED"]

    core_display_data = make_core_display_data(core_team_properties_async)
    puzzle = puzzle_async.result().json()

    island_open_order = core_team_properties_async.result().json().get('teamProperties',{}).get('IslandOpenOrderProperty',{}).get('openOrder',[])

    # This pretends to be generic, but is actually just used for the Pokemon submetas
    feeders_solved = []
    feeder_properties = {}
    feeders = puzzle.get('puzzleProperties', {}).get('FeedersProperty', {}).get('feeders', [])
    if feeders:
        feeder_properties_async = cube.get_all_puzzle_properties_for_list_async(app, feeders)
        feeder_visibility_async = cube.get_puzzle_visibilities_for_list_async(app, feeders)
        feeder_properties = {v['puzzleId']: v for v in feeder_properties_async.result().json().get('puzzles', []) }
        feeders_solved = [v['puzzleId'] for v in feeder_visibility_async.result().json().get('visibilities', []) if v['status'] == 'SOLVED']
        feeders_solved.sort(key=lambda puzzleId: feeder_properties[puzzleId].get('SymbolProperty', {}).get('symbol', ''))

    canonical_puzzle_id = puzzle.get('puzzleId')
    puzzle_round_id = [r_id for r_id, round_puzzle_ids in ROUND_PUZZLE_MAP.iteritems() if canonical_puzzle_id in round_puzzle_ids]
    puzzle_round_id = puzzle_round_id[0] if len(puzzle_round_id) > 0 else None
    emotions = puzzle.get('puzzleProperties',{}).get('EmotionsProperty',{}).get('emotions',[])

    pages_without_solutions_async = cube.get_all_puzzle_properties_for_list_async(app, EMOTION_INTERACTIONS + ISLAND_RECOVERY_INTERACTIONS + FINALES + EVENTS)
    pages_without_solutions = [ v.get('puzzleProperties', {}).get('DisplayIdProperty', {}).get('displayId', '') for v in pages_without_solutions_async.result().json().get("puzzles",[]) ]

    with metrics.timer("present.puzzle_render"):
        r = make_response(
            render_template(
                "puzzles/%s.html" % puzzle_id,
                core_display_data=core_display_data,
                is_hunt_started=True,
                puzzle_id=puzzle_id,
                puzzle_round_id=puzzle_round_id,
                emotions=emotions,
                puzzle=puzzle,
                interactions_finales=interactions_finales,
                puzzle_visibility=puzzle_visibility,
                island_open_order=island_open_order,
                placeholders=ISLANDS_AND_PLACEHOLDERS,
                island_unlocks=island_unlocks,
                pages_without_solutions=pages_without_solutions,
                feeders_solved=feeders_solved,
                feeder_count=len(feeders),
                feeder_properties=feeder_properties))
        r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
        return r

@app.route("/puzzle/<puzzle_id>/token")
@login_required.solvingteam
@metrics.time("present.puzzletoken")
def puzzletoken(puzzle_id):
    puzzle_visibility_async = cube.get_puzzle_visibility_async(app, puzzle_id)
    puzzle_async = cube.get_puzzle_async(app, puzzle_id)

    puzzle_visibility = puzzle_visibility_async.result().json()
    if puzzle_visibility['status'] not in ['UNLOCKED','SOLVED']:
        abort(403)

    puzzle = puzzle_async.result().json()

    with metrics.timer("present.puzzletoken_render"):
        return generate_puzzle_token_json(puzzle)

@app.route("/solution/<puzzle_id>")
@login_required.solvingteam
@metrics.time("present.solution")
def puzzle_solution(puzzle_id):
    if app.config["SITE_MODE"] != 'solution':
        abort(403)

    puzzle_visibility_async = cube.get_puzzle_visibility_async(app, puzzle_id)
    core_team_properties_async = cube.get_team_properties_async(app)
    puzzle_async = cube.get_puzzle_async(app, puzzle_id)

    puzzle_visibility = puzzle_visibility_async.result().json()
    if puzzle_visibility['status'] not in ['UNLOCKED','SOLVED']:
        abort(403)

    core_display_data = make_core_display_data(core_team_properties_async)
    puzzle = puzzle_async.result().json()
    canonical_puzzle_id = puzzle.get('puzzleId')
    puzzle_round_id = [r_id for r_id, round_puzzle_ids in ROUND_PUZZLE_MAP.iteritems() if canonical_puzzle_id in round_puzzle_ids]
    puzzle_round_id = puzzle_round_id[0] if len(puzzle_round_id) > 0 else None
    emotions = puzzle.get('puzzleProperties',{}).get('EmotionsProperty',{}).get('emotions',[])

    with metrics.timer("present.puzzle_render"):
        return render_template(
            "solutions/%s.html" % puzzle_id,
            core_display_data=core_display_data,
            is_hunt_started=True,
            puzzle_id=puzzle_id,
            puzzle_round_id=puzzle_round_id,
            emotions=emotions,
            puzzle=puzzle,
            puzzle_visibility=puzzle_visibility,
            solution=True)

@app.route("/puzzle")
@login_required.solvingteam
@metrics.time("present.puzzle_list")
def puzzle_list():
    if not cube.is_hunt_started_async(app).result():
        abort(403);

    core_team_properties_async = cube.get_team_properties_async(app)
    all_visibilities_async = cube.get_puzzle_visibilities_async(app)
    all_puzzles_async = cube.get_all_puzzle_properties_async(app)
    interactions_and_finales_async = cube.get_all_puzzle_properties_for_list_async(app, INTERACTIONS_AND_FINALES)

    core_display_data = make_core_display_data(core_team_properties_async)
    all_visibilities = { v["puzzleId"]: v for v in all_visibilities_async.result().json()["visibilities"] }
    all_puzzles = { v["puzzleId"]: v for v in all_puzzles_async.result().json()["puzzles"] }
    interactions_finales = interactions_and_finales_async.result().json().get("puzzles")
    interactions_finales = { v["puzzleId"]: v for v in interactions_finales }
    events_unlocked = any( all_visibilities.get(puzzle_id, {}).get('status', '') in ['UNLOCKED', 'SOLVED']
                           for puzzle_id in EVENTS )

    with metrics.timer("present.puzzle_list_render"):
        r = make_response(
            render_template(
                "puzzle_list.html",
                core_display_data=core_display_data,
                is_hunt_started=True,
                all_visibilities=all_visibilities,
                all_puzzles=all_puzzles,
                round_puzzle_map=ROUND_PUZZLE_MAP,
                events_unlocked=events_unlocked,
                interactions_finales=interactions_finales,
                interactions_finales_ordering=INTERACTIONS_AND_FINALES,))
        r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
        return r

@app.route("/objectives")
@login_required.solvingteam
def objectives():
    is_hunt_started_async = cube.is_hunt_started_async(app)
    objective_visibilities_async = cube.get_puzzle_visibilities_for_list_async(app, OBJECTIVE_PUZZLES + ISLAND_IDS)
    puzzle_properties_async = cube.get_all_puzzle_properties_for_list_async(app, OBJECTIVE_PUZZLES + ISLAND_IDS + ISLAND_UNLOCKS + EVENTS)
    core_team_properties_async = cube.get_team_properties_async(app)

    core_display_data = make_core_display_data(core_team_properties_async)

    if is_hunt_started_async.result():
        is_hunt_started = True
        objective_visibilities = {r['puzzleId']: r for r in objective_visibilities_async.result().json()['visibilities']}
        puzzle_properties = {puzzle.get('puzzleId'): puzzle for puzzle in puzzle_properties_async.result().json().get('puzzles',[])}
    else:
        is_hunt_started = False
        objective_visibilities = {}
        puzzle_properties = {}

    objective_visibilities = collections.defaultdict(lambda: {'status': 'INVISIBLE'}, objective_visibilities)
    statuses = { p: objective_visibilities[p]['status'] for p in OBJECTIVE_PUZZLES }
    answers = { p: ', '.join(objective_visibilities[p].get('solvedAnswers',[])) for p in OBJECTIVE_PUZZLES }

    puzzle_properties = {puzzle.get('puzzleId'): puzzle for puzzle in puzzle_properties_async.result().json().get('puzzles',[])}
    island_properties = {puzzle: puzzle_properties[puzzle] for puzzle in puzzle_properties if puzzle in ISLAND_UNLOCKS}
    names = { p: puzzle_properties.get(p,{}).get('puzzleProperties', {}).get('DisplayNameProperty', {}).get('displayName', '') for p in OBJECTIVE_PUZZLES }
    display_ids = { p: puzzle_properties.get(p,{}).get('puzzleProperties', {}).get('DisplayIdProperty', {}).get('displayId', '') for p in OBJECTIVE_PUZZLES }

    brainpower = core_display_data.get('brainpower',0)
    buzzy_bucks = core_display_data.get('buzzyBucks',0)

    def count_solved(puzzles):
      return sum(1 for p in puzzles if statuses[p] == 'SOLVED')

    # Much less messy to calculate these here than inside templates
    counts = {
      'events': {
        'total': len(EVENTS),
        'started': sum(1 for p in EVENTS if statuses[p] in ['UNLOCKED', 'SOLVED']),
        'solved': count_solved(EVENTS),
      },
      'emotions': {
        'total': len(EMOTION_IDS),
        'solved': count_solved(EMOTION_IDS),
        'encountered': count_solved('encounter-' + e for e in EMOTION_IDS),
      },
      'islands': {
        'total': len(ISLAND_IDS),
        'opened': len(core_display_data['openIslands']),
        'solved': count_solved(i + '-supermeta' for i in ISLAND_IDS),
        'recovered': count_solved(i + '-recovery' for i in ISLAND_IDS),
      },
      'pokemon_submetas': {
        'total': len(POKEMON_SUBMETAS),
        'solved': count_solved(POKEMON_SUBMETAS),
      },
      'scifi_submetas': {
        'total': len(SCIFI_SUBMETAS),
        'solved': count_solved(SCIFI_SUBMETAS),
      },
      'hacking_submetas': {
        'total': len(HACKING_SUBMETAS),
        'open': sum(1 for p in HACKING_SUBMETAS if statuses[p] in ['UNLOCKED', 'SOLVED']),
        'solved': count_solved(HACKING_SUBMETAS),
        'mapped': count_solved(HACKING_RUNAROUNDS),
      },
    }

    r = make_response(
        render_template(
            "mission_objectives.html",
            core_display_data=core_display_data,
            emotions=EMOTION_IDS,
            islands=ISLAND_IDS,
            placeholders=dict(ISLANDS_AND_PLACEHOLDERS),
            names=names,
            display_ids=display_ids,
            is_hunt_started=is_hunt_started,
            island_unlocks=ISLAND_UNLOCKS,
            island_properties=island_properties,
            brainpower=brainpower,
            buzzy_bucks=buzzy_bucks,
            counts=counts,
            statuses=statuses,
            answers=answers))
    r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
    return r

@app.route("/safety")
@login_required.anybody
def safety():
    is_hunt_started_async = cube.is_hunt_started_async(app)
    core_team_properties_async = cube.get_team_properties_async(app)
    core_display_data = make_core_display_data(core_team_properties_async)

    return render_template("safety.html",
        core_display_data=core_display_data,
        is_hunt_started=is_hunt_started_async.result())

@app.route("/sponsors")
@login_required.anybody
def sponsors():
    is_hunt_started_async = cube.is_hunt_started_async(app)
    core_team_properties_async = cube.get_team_properties_async(app)
    core_display_data = make_core_display_data(core_team_properties_async)

    return render_template("sponsors.html",
        core_display_data=core_display_data,
        is_hunt_started=is_hunt_started_async.result())

@app.route("/faq")
@login_required.anybody
def faq():
    core_team_properties_async = cube.get_team_properties_async(app)
    is_hunt_started_async = cube.is_hunt_started_async(app)

    core_display_data = make_core_display_data(core_team_properties_async)

    return render_template(
        "faq.html",
        core_display_data=core_display_data,
        is_hunt_started=is_hunt_started_async.result())

@app.route("/credits")
@login_required.anybody
def credits():
    if app.config["SITE_MODE"] != 'solution':
        abort(403)
    core_team_properties_async = cube.get_team_properties_async(app)
    is_hunt_started_async = cube.is_hunt_started_async(app)

    core_display_data = make_core_display_data(core_team_properties_async)

    return render_template(
        "credits.html",
        core_display_data=core_display_data,
        is_hunt_started=is_hunt_started_async.result())

@app.route("/errata")
@login_required.anybody
def errata():
    core_team_properties_async = cube.get_team_properties_async(app)
    is_hunt_started_async = cube.is_hunt_started_async(app)

    core_display_data = make_core_display_data(core_team_properties_async)

    return render_template(
        "errata.html",
        core_display_data=core_display_data,
        is_hunt_started=is_hunt_started_async.result())

@app.route("/change_contact_info", methods=["POST"])
@login_required.solvingteam
def change_contact_info():
    team = cube.get_team_properties(app)
    cube.update_team(app, session["username"], {
        "teamId": session["username"],
        "teamName": team.get("teamName", ""),
        "headquarters": team.get("headquarters", ""),
        "email": request.form["email"],
        "primaryPhone": request.form["primaryPhone"],
        "secondaryPhone": request.form["secondaryPhone"],
    })
    if request.referrer:
        return redirect(request.referrer)
    return redirect(url_for("index"))

@app.route("/activity_log")
@login_required.solvingteam
@metrics.time("present.activity_log")
def activity_log():
    is_hunt_started_async = cube.is_hunt_started_async(app)
    core_team_properties_async = cube.get_team_properties_async(app)
    team_visibility_changes_async = cube.get_team_visibility_changes_async(app)
    team_submissions_async = cube.get_team_submissions_async(app)
    all_puzzles_async = cube.get_all_puzzle_properties_async(app)
    interactions_and_finales_async = cube.get_all_puzzle_properties_for_list_async(app, EMOTION_INTERACTIONS + ISLAND_RECOVERY_INTERACTIONS + FINALES)

    is_hunt_started = is_hunt_started_async.result()
    core_display_data = make_core_display_data(core_team_properties_async)

    visibility_changes = [vc for vc in team_visibility_changes_async.result()
                          if vc["status"] in ['UNLOCKED', 'SOLVED']]
    activity_entries = visibility_changes + team_submissions_async.result()
    activity_entries.sort(key=lambda entry: entry["timestamp"], reverse=True)

    all_puzzles = { v["puzzleId"]: v for v in all_puzzles_async.result().json()["puzzles"] }
    interactions_and_finales = [ v["puzzleId"] for v in interactions_and_finales_async.result().json().get("puzzles",[]) ]

    r = make_response(
        render_template(
            "activity_log.html",
            core_display_data=core_display_data,
            is_hunt_started=is_hunt_started,
            activity_entries=activity_entries,
            all_puzzles=all_puzzles,
            interactions_and_finales=interactions_and_finales,
            island_ids=ISLAND_IDS))
    r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
    return r

@app.route("/full/puzzle_raw")
@login_required.writingteam
def full_puzzle_index():
    files = os.listdir(os.path.join(app.root_path, 'templates/puzzles'))
    puzzle_ids = [file.split('.')[0] for file in files if file.endswith('.html') and not file.startswith('.')]
    puzzle_ids = sorted([puzzle_id for puzzle_id in puzzle_ids if puzzle_id not in ['puzzle_layout','sample_draft']])
    core_display_data = get_full_path_core_display_data()

    return render_template("full_puzzle_index.html",
        core_display_data=core_display_data,
        puzzle_ids=puzzle_ids,
        is_hunt_started=True)

@app.route('/full/puzzle')
@login_required.writingteam
def full_puzzle_list():
    core_display_data = get_full_path_core_display_data()
    all_puzzles_async = cube.get_all_puzzle_properties_async(app)

    all_puzzles = {v['puzzleId']: v for v in all_puzzles_async.result().json()['puzzles']}
    # default to UNLOCKED instead of SOLVED here to avoid showing answers
    all_visibilities = {p: {'status': request.args.get('visibility', 'UNLOCKED')} for p in all_puzzles}

    r = make_response(
        render_template(
            "puzzle_list.html",
            core_display_data=core_display_data,
            is_hunt_started=True,
            all_visibilities=all_visibilities,
            all_puzzles=all_puzzles,
            round_puzzle_map=ROUND_PUZZLE_MAP,
            events_unlocked=True,
            interactions_finales=all_puzzles,
            interactions_finales_ordering=INTERACTIONS_AND_FINALES,))
    r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
    return r

@app.route("/full/puzzle/<puzzle_id>")
@login_required.writingteam
def full_puzzle(puzzle_id):
    puzzle = {
        'puzzleId': puzzle_id,
        'puzzleProperties': {'DisplayNameProperty': {'displayName': puzzle_id}},
    }
    puzzle_round_id = 'floaters'
    emotions = []
    feeders = set()
    feeder_properties = {}

    try:
        puzzle = cube.get_puzzle(app, puzzle_id)

        canonical_puzzle_id = puzzle.get('puzzleId')
        puzzle_round_id = [r_id for r_id, round_puzzle_ids in ROUND_PUZZLE_MAP.iteritems() if canonical_puzzle_id in round_puzzle_ids]
        puzzle_round_id = puzzle_round_id[0] if len(puzzle_round_id) > 0 else None
        emotions = puzzle.get('puzzleProperties',{}).get('EmotionsProperty',{}).get('emotions',[])

        feeders = puzzle.get('puzzleProperties', {}).get('FeedersProperty', {}).get('feeders', [])
        if feeders:
            feeder_properties_async = cube.get_all_puzzle_properties_for_list_async(app, feeders)
            feeder_properties = {v['puzzleId']: v for v in feeder_properties_async.result().json().get('puzzles', []) }
            feeders.sort(key=lambda puzzleId: feeder_properties[puzzleId].get('puzzleProperties', {}).get('SymbolProperty', {}).get('symbol', ''))

    except HTTPError as e:
        if e.response.status_code != 404:
            raise

    core_display_data = get_full_path_core_display_data()

    pages_without_solutions_async = cube.get_all_puzzle_properties_for_list_async(app, EMOTION_INTERACTIONS + ISLAND_RECOVERY_INTERACTIONS + FINALES + EVENTS)
    pages_without_solutions = [ v.get('puzzleProperties', {}).get('DisplayIdProperty', {}).get('displayId', '') for v in pages_without_solutions_async.result().json().get("puzzles",[]) ]

    return render_template(
        "puzzles/%s.html" % puzzle_id,
        core_display_data=core_display_data,
        is_hunt_started=True,
        puzzle_id=puzzle_id,
        puzzle_round_id=puzzle_round_id,
        emotions=emotions,
        puzzle=puzzle,
        puzzle_visibility={'status': 'UNLOCKED'},
        feeders_solved=feeders,
        feeder_count=len(feeders),
        feeder_properties=feeder_properties,
        pages_without_solutions=pages_without_solutions)


@app.route("/full/puzzle/<puzzle_id>/token")
@login_required.writingteam
def full_puzzletoken(puzzle_id):
    puzzle_async = cube.get_puzzle_async(app, puzzle_id)
    return generate_puzzle_token_json(puzzle_async.result().json())


@app.route("/full/solution/<puzzle_id>")
@login_required.writingteam
def full_solution(puzzle_id):
    try:
        puzzle = cube.get_puzzle(app, puzzle_id)
        canonical_puzzle_id = puzzle.get('puzzleId')
        puzzle_round_id = [r_id for r_id, round_puzzle_ids in ROUND_PUZZLE_MAP.iteritems() if canonical_puzzle_id in round_puzzle_ids]
        puzzle_round_id = puzzle_round_id[0] if len(puzzle_round_id) > 0 else None
        emotions = puzzle.get('puzzleProperties',{}).get('EmotionsProperty',{}).get('emotions',[])
    except HTTPError as e:
        if e.response.status_code != 404:
            raise

        puzzle = {
            'puzzleId': puzzle_id,
            'puzzleProperties': {
                'DisplayNameProperty': {'displayName': puzzle_id},
                'AnswersProperty': {'answers': [{'canonicalAnswer': 'FLOATER ANSWER'}]},
            },
        }
        puzzle_round_id = 'floaters'
        emotions = []

    core_display_data = get_full_path_core_display_data()

    return render_template(
        "solutions/%s.html" % puzzle_id,
        core_display_data=core_display_data,
        is_hunt_started=True,
        puzzle_id=puzzle_id,
        puzzle_round_id=puzzle_round_id,
        emotions=emotions,
        puzzle=puzzle,
        puzzle_visibility={'status': 'SOLVED'},
        solution=True)

@app.route("/full/island/<island_id>")
@login_required.writingteam
def full_island(island_id):
    puzzle_properties = cube.get_puzzles(app)
    puzzle_properties = {puzzle.get('puzzleId'): puzzle for puzzle in puzzle_properties}

    core_display_data = get_full_path_core_display_data()
    visibility = request.args.get('visibility', 'SOLVED')
    puzzle_visibilities = {puzzle_id: {'status': visibility} for puzzle_id in puzzle_properties.keys()}

    opts = dict(
        core_display_data=core_display_data,
        is_hunt_started=True,
        island_id=island_id,
        puzzle_properties=puzzle_properties,
        puzzle_visibilities=puzzle_visibilities,
        )

    if island_id == 'index':
        template = 'index.html'
        opts['islands'] = ISLAND_IDS
    else:
        template = "islands/%s.html" % island_id

    return render_template(template, **opts)

@app.route("/full/memory/<island_id>")
@login_required.writingteam
def full_memory(island_id):
    core_display_data = get_full_path_core_display_data()

    return render_template(
        "core_memory.html",
        core_display_data=core_display_data,
        is_hunt_started=True,
        island_id=island_id)

@app.route('/full/objectives')
@login_required.writingteam
def full_objectives():
    core_display_data = get_full_path_core_display_data()
    is_hunt_started = True

    puzzle_properties_async = cube.get_all_puzzle_properties_for_list_async(app, OBJECTIVE_PUZZLES + ISLAND_IDS + ISLAND_UNLOCKS + EVENTS)
    puzzle_properties = {puzzle.get('puzzleId'): puzzle for puzzle in puzzle_properties_async.result().json().get('puzzles',[])}
    island_properties = {puzzle: puzzle_properties[puzzle] for puzzle in puzzle_properties if puzzle in ISLAND_UNLOCKS}
    names = { p: puzzle_properties.get(p,{}).get('puzzleProperties', {}).get('DisplayNameProperty', {}).get('displayName', '') for p in OBJECTIVE_PUZZLES }
    display_ids = { p: puzzle_properties.get(p,{}).get('puzzleProperties', {}).get('DisplayIdProperty', {}).get('displayId', '') for p in OBJECTIVE_PUZZLES }

    statuses = collections.defaultdict(lambda: request.args.get('visibility', 'SOLVED'))
    answers = {p: ', '.join([a['canonicalAnswer'] for a in puzzle_properties.get(p,{}).get('puzzleProperties',{}).get('AnswersProperty',{}).get('answers',[])]) for p in OBJECTIVE_PUZZLES}

    counts = {
      'events': {
        'total': len(EVENTS),
        'started': len(EVENTS),
        'solved': len(EVENTS),
      },
      'emotions': {
        'total': len(EMOTION_IDS),
        'solved': len(EMOTION_IDS),
        'encountered': len(EMOTION_IDS),
      },
      'islands': {
        'total': len(ISLAND_IDS),
        'opened': len(ISLAND_IDS),
        'solved': len(ISLAND_IDS),
        'recovered': len(ISLAND_IDS),
      },
      'pokemon_submetas': {
        'total': len(POKEMON_SUBMETAS),
        'solved': len(POKEMON_SUBMETAS),
      },
      'scifi_submetas': {
        'total': len(SCIFI_SUBMETAS),
        'solved': len(SCIFI_SUBMETAS),
      },
      'hacking_submetas': {
        'total': len(HACKING_SUBMETAS),
        'open': len(HACKING_SUBMETAS),
        'solved': len(HACKING_SUBMETAS),
        'mapped': len(HACKING_RUNAROUNDS),
      },
    }

    r = make_response(
        render_template(
            "mission_objectives.html",
            core_display_data=core_display_data,
            emotions=EMOTION_IDS,
            islands=ISLAND_IDS,
            placeholders=dict(ISLANDS_AND_PLACEHOLDERS),
            names=names,
            display_ids=display_ids,
            is_hunt_started=is_hunt_started,
            island_unlocks=ISLAND_UNLOCKS,
            island_properties=island_properties,
            brainpower=0,
            buzzy_bucks=0,
            counts=counts,
            statuses=statuses,
            answers=answers))
    r.headers.set('Cache-Control', 'private, max-age=0, no-cache, no-store')
    return r
