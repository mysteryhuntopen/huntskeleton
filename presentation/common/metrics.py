from functools import wraps
from pyformance import global_registry

def create_reporter(app):
    if "GRAPHITE_HOST" in app.config:
        from pyformance.reporters.carbon_reporter import CarbonReporter
        reporter = CarbonReporter(
            prefix=app.config["GRAPHITE_PREFIX"],
            server=app.config["GRAPHITE_HOST"],
            reporting_interval=30)
        reporter.start()

def time(key):
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            with timer(key):
                return f(*args, **kwargs)
        return decorated_function
    return decorator

def timer(key):
    return global_registry().timer(key).time()
