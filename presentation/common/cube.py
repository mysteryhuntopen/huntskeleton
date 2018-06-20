import base64
import json
import urllib2

from flask import session
import requests
from concurrent.futures import ThreadPoolExecutor
from requests_futures.sessions import FuturesSession

#This number is currently a guess and probably needs changing based on load testing
THREAD_POOL = ThreadPoolExecutor(max_workers=100)

class ResponseCheckingFutureWrapper(object):
    def __init__(self, response_future):
        self.response_future = response_future

    def result(self):
        response = self.response_future.result()
        response.raise_for_status()
        return response

def create_requests_session(username=None, password=None):
    if not username:
        username = session["username"]
    if not password:
        password = session["password"]
    s = requests.Session()
    s.auth = (username, password)
    return s

def get_url_for_path(app, path):
    return app.config["CUBE_API_SERVICE"] + path

def get_async(app, path, requests_session=None):
    if not requests_session:
        requests_session = create_requests_session()
    futures_session = FuturesSession(session=requests_session, executor=THREAD_POOL)
    url = get_url_for_path(app, path)
    return ResponseCheckingFutureWrapper(futures_session.get(url))

def get(app, path, requests_session=None):
    return get_async(app, path, requests_session=requests_session).result().json()

def post(app, path, data, requests_session=None):
    if not requests_session:
        requests_session = create_requests_session()
    url = get_url_for_path(app, path)
    headers = { "Content-Type": "application/json" }
    json_post_data = json.dumps(data)
    response = requests_session.post(url, data=json_post_data, headers=headers)
    response.raise_for_status()
    return response.json()

def post_async(app, path, data, requests_session=None):
    if not requests_session:
        requests_session = create_requests_session()
    futures_session = FuturesSession(session=requests_session, executor=THREAD_POOL)
    url = get_url_for_path(app, path)
    headers = { "Content-Type": "application/json" }
    json_post_data = json.dumps(data)
    return ResponseCheckingFutureWrapper(futures_session.post(url, data=json_post_data, headers=headers))

def authorized(app, permission):
    response = get(app, "/authorized?permission=%s" % permission)
    return response["authorized"]

def is_hunt_started_async(app):
    response = get_async(app, "/run")
    class IsHuntStartedFutureWrapper(object):
        def __init__(self, response_future):
            self.response_future = response_future
        def result(self):
            json = self.response_future.result().json()
            return json.get("startTimestamp", None) is not None
    return IsHuntStartedFutureWrapper(response)

def get_visible_puzzle_ids(app):
    response = get(app, "/visibilities?teamId=%s" % session["username"])
    return [v["puzzleId"] for v in response["visibilities"]]

def get_puzzle_visibilities(app, team_id=None):
    if not team_id:
        team_id = session["username"]
    response = get(app, "/visibilities?teamId=%s" % team_id)
    return sorted(response["visibilities"], key=lambda v: v["puzzleId"])

def get_puzzle_visibilities_async(app, team_id=None):
    if not team_id:
        team_id = session["username"]
    return get_async(app, "/visibilities?teamId=%s" % team_id)

def get_puzzle_visibilities_for_list(app, puzzle_ids, team_id=None):
    if not team_id:
        team_id = session["username"]
    response = get(app, "/visibilities?teamId=%s&puzzleId=%s" % (team_id, ','.join(puzzle_ids)))
    return { v["puzzleId"]: v for v in response["visibilities"] }

def get_puzzle_visibilities_for_list_async(app, puzzle_ids, team_id=None):
    if not team_id:
        team_id = session["username"]
    return get_async(app, "/visibilities?teamId=%s&puzzleId=%s" % (team_id, ','.join(puzzle_ids)))

def get_puzzle_visibility(app, puzzle_id):
    return get(app, "/visibilities/%s/%s" % (session["username"], puzzle_id))

def get_puzzle_visibility_async(app, puzzle_id):
    return get_async(app, "/visibilities/%s/%s" % (session["username"], puzzle_id))

def update_puzzle_visibility(app, team_id, puzzle_id, status):
    post(app, "/visibilities/%s/%s" % (team_id, puzzle_id), {
        "teamId": team_id,
        "puzzleId": puzzle_id,
        "status": status,
    })

def update_puzzle_visibility_async(app, team_id, puzzle_id, status):
    return post_async(app, "/visibilities/%s/%s" % (team_id, puzzle_id), {
        "teamId": team_id,
        "puzzleId": puzzle_id,
        "status": status,
    })

def get_team_visibility_changes_async(app, team_id=None):
    if not team_id:
        team_id = session["username"]
    response = get_async(app, "/visibilitychanges?teamId=%s" % team_id)
    class VisibilityChangesFutureWrapper(object):
        def __init__(self, response_future):
            self.response_future = response_future
        def result(self):
            json = self.response_future.result().json()
            return json["visibilityChanges"]
    return VisibilityChangesFutureWrapper(response)

def is_puzzle_unlocked(app, puzzle_id):
    return get_puzzle_visibility(app, puzzle_id)["status"] in ["UNLOCKED", "SOLVED"]

def get_all_puzzle_properties(app):
    response = get(app, "/puzzles?teamId=%s&siteMode=%s" % (session["username"], app.config["SITE_MODE"]))
    return response

def get_all_puzzle_properties_async(app):
    return get_async(app, "/puzzles?teamId=%s&siteMode=%s" % (session["username"], app.config["SITE_MODE"]))

def get_all_puzzle_properties_for_list(app, puzzle_ids):
    response = get(app, "/puzzles?teamId=%s&puzzleId=%s&siteMode=%s" % (session["username"], ','.join(puzzle_ids), app.config["SITE_MODE"]))
    return {puzzle.get('puzzleId'): puzzle for puzzle in response.get('puzzles',[])}

def get_all_puzzle_properties_for_list_async(app, puzzle_ids):
    return get_async(app, "/puzzles?teamId=%s&puzzleId=%s&siteMode=%s" % (session["username"], ','.join(puzzle_ids), app.config["SITE_MODE"]))

def get_puzzles(app):
    response = get(app, "/puzzles?siteMode=%s" % app.config["SITE_MODE"])
    return response["puzzles"]

def get_puzzle(app, puzzle_id):
    response = get(app, "/puzzles/%s?siteMode=%s" % (puzzle_id, app.config["SITE_MODE"]))
    return response

def get_puzzle_async(app, puzzle_id):
    return get_async(app, "/puzzles/%s?siteMode=%s" % (puzzle_id, app.config["SITE_MODE"]))

def get_team_properties(app, team_id=None):
    if not team_id:
        team_id = session["username"]
    response = get(app, "/teams/%s" % team_id)
    return response

def get_team_properties_async(app):
    return get_async(app, "/teams/%s" % session["username"])

def get_submissions(app, puzzle_id, team_id=None):
    if not team_id:
        team_id = session["username"]
    response = get(app, "/submissions?teamId=%s&puzzleId=%s" % (team_id, puzzle_id))
    return response["submissions"]

def get_team_submissions_async(app, team_id=None):
    if not team_id:
        team_id = session["username"]
    response = get_async(app, "/submissions?teamId=%s" % team_id)
    class SubmissionsFutureWrapper(object):
        def __init__(self, response_future):
            self.response_future = response_future
        def result(self):
            json = self.response_future.result().json()
            return json["submissions"]
    return SubmissionsFutureWrapper(response)

def get_all_pending_submissions(app):
    response = get(app, "/submissions?status=SUBMITTED,ASSIGNED")
    return response["submissions"]

def get_submission(app, submission_id):
    return get(app, "/submissions/%d" % submission_id)

def create_submission(app, puzzle_id, submission):
    post(app, "/submissions", {
        "teamId": session["username"],
        "puzzleId": puzzle_id,
        "submission": submission,
    })

def update_submission_status(app, submission_id, status):
    post(app, "/submissions/%d" % submission_id, {
        "status": status,
    })

def get_hints(app, puzzle_id):
    response = get(app, "/hintrequests?teamId=%s&puzzleId=%s" % (session["username"], puzzle_id))
    return response["hintRequests"]

def get_all_pending_hint_requests(app):
    response = get(app, "/hintrequests")
    return response["hintRequests"]

def get_hint_request(app, hint_request_id):
    return get(app, "/hintrequests/%d" % hint_request_id)

def create_hint_request(app, puzzle_id, request, hint_type):
    post(app, "/hintrequests", {
        "teamId": session["username"],
        "puzzleId": puzzle_id,
        "request": request,
        "hintType": hint_type,
    })

def update_hint_request(app, hint_request_id, status, response):
    post(app, "/hintrequests/%d" % hint_request_id, {
        "status": status,
        "response": response,
    })

def get_interactions(app, puzzle_id):
    response = get(app, "/interactionrequests?teamId=%s&puzzleId=%s" % (session["username"], puzzle_id))
    return response["interactionRequests"]

def get_all_pending_interaction_requests(app):
    response = get(app, "/interactionrequests")
    return response["interactionRequests"]

def get_interaction_request(app, interaction_request_id):
    return get(app, "/interactionrequests/%d" % interaction_request_id)

def create_interaction_request(app, puzzle_id, request, invisible='NO'):
    post(app, "/interactionrequests", {
        "teamId": session["username"],
        "puzzleId": puzzle_id,
        "request": request,
        'invisible': invisible,
    })

def update_interaction_request(app, interaction_request_id, status, response):
    post(app, "/interactionrequests/%d" % interaction_request_id, {
        "status": status,
        "response": response,
    })

def get_teams(app):
    response = get(app, "/teams")
    return response["teams"]

def get_team(app, team_id):
    return get(app, "/teams/%s" % team_id)

def update_team(app, team_id, team):
    post(app, "/teams/%s" % team_id, team)

def create_team(app, team):
    post(app, "/teams", team)

def get_users(app):
    response = get(app, "/users")
    return response["users"]

def get_user(app, username):
    return get(app, "/users/%s" % username)

def update_user(app, username, user):
    post(app, "/users/%s" % username, user)

def create_user(app, user):
    post(app, "/users", user)

def create_event(app, event):
    post(app, "/events", event)

def create_event_async(app, event):
    return post_async(app, "/events", event)
