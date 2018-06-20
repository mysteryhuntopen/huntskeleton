from flask import Blueprint, current_app, request, redirect

blueprint = Blueprint('gcp', __name__)

@blueprint.route('/healthz')
def health():
    return 'ok'

# HTTPS redirect
@blueprint.before_app_request
def before_request():
    if not current_app.debug and not request.is_secure and not request.endpoint == 'gcp.health':
        url = request.url.replace('http://', 'https://', 1)
        code = 301
        return redirect(url, code=code)
