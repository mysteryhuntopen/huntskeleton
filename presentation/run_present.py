import os

from common import metrics
from present import app

from common import login
app.register_blueprint(login.blueprint)
from common import gcp
app.register_blueprint(gcp.blueprint)
from common import assets
app.register_blueprint(assets.blueprint)

metrics.create_reporter(app)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, extra_files=[
        os.path.join(app.config.root_path, '../default_config.cfg'),
        os.path.join(app.config.root_path, '../config.cfg'),
    ])
