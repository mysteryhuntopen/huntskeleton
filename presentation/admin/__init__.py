from flask import Flask
from werkzeug.contrib.fixers import ProxyFix

app = Flask(__name__)

app.config.from_pyfile('../default_config.cfg')
app.config.from_pyfile('../config.cfg', silent=True)
if app.debug:
    app.config['SEND_FILE_MAX_AGE_DEFAULT'] = 0

# In production, we run behind GCP Load Balancers
if not app.debug:
    app.wsgi_app = ProxyFix(app.wsgi_app)

import admin.views
import admin.utils
