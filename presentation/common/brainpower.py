def calculate_brainpower_thresholds(island_properties):
    brainpower_thresholds = [
        island_properties.
        get('island-open-{}'.format(i), {}).
        get('puzzleProperties', {}).
        get('UnlockedConstraintProperty', {}).
        get('unlockedConstraint', {}).
        get('scoreConstraints', {}).
        get('BRAINPOWER', 0)
        for i in range(1, 4+1)]
    return brainpower_thresholds
