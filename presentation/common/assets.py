import errno
import json
import os

from flask import Blueprint
import jinja2

blueprint = Blueprint('assets', __name__)

def asset_url_for(path):
    return jinja2.Markup(blueprint.assets.asset_url_for(path))

def img_src_for(path):
    srcset = []
    srcset.append(asset_url_for(path) + ' 1x')
    path_parts = os.path.splitext(path)
    retina_path = '%s@2x%s' % (path_parts)
    if retina_path in blueprint.assets.manifest:
        srcset.append(asset_url_for(retina_path) + ' 2x')

    attr = 'src="%s"' % jinja2.escape(asset_url_for(path))
    if len(srcset) > 1:
      attr += ' srcset="%s"' % jinja2.escape(', '.join(srcset))
    return jinja2.Markup(attr)

@blueprint.record_once
def record(setup_state):
    blueprint.assets = GulpAssets(setup_state.app.config)
    setup_state.app.context_processor(lambda: {
        'asset_url_for': asset_url_for,
        'img_src_for': img_src_for,
        'debug_mode': setup_state.app.config['DEBUG'],
    })

class GulpAssets(object):
    def __init__(self, config):
        self.base_url = config['ASSET_BASE_URL']

        root = os.path.join(os.path.dirname(__file__), '../..')
        self.manifest = {}
        try:
            with open(os.path.join(root, 'build/rev-manifest.json')) as f:
                self.manifest = json.load(f)
        except IOError as e:
            # It's OK for the manifest file to not exist in debug/dev
            # mode
            if not config['DEBUG'] or e.errno != errno.ENOENT:
                raise

            asset_dir = os.path.join(root, 'dist')
            for root, _dirs, files in os.walk(asset_dir):
                for f in files:
                    full_path = os.path.relpath(os.path.join(root, f), asset_dir)
                    self.manifest[full_path] = full_path

    def asset_url_for(self, path):
        return self.base_url + self.manifest.get(path, path)
