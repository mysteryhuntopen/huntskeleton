from present import app

from datetime import datetime
import pytz

@app.template_filter('datetime')
def format_datetime(value):
    fmt = '%Y-%m-%d %H:%M:%S'
    utc = pytz.utc
    eastern = pytz.timezone('US/Eastern')
    utc_dt = utc.localize(datetime.utcfromtimestamp(value / 1000))
    eastern_dt = utc_dt.astimezone(eastern)
    return eastern_dt.strftime(fmt)
