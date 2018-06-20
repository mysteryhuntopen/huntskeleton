EMOTION_IDS = ['joy', 'sadness', 'fear', 'disgust', 'anger']
ISLANDS_AND_PLACEHOLDERS = [
    ('pokemon', 'shiny'),
    ('games', 'byzantine'),
    ('scifi', 'dark'),
    ('hacking', 'secret'),
]
ISLAND_IDS = [i[0] for i in ISLANDS_AND_PLACEHOLDERS]

ISLAND_UNLOCKS = ['island-open-{}'.format(i) for i in range(1, 4+1)]

EMOTION_RUNAROUND = 'brainstorm'
EMOTION_METAS = EMOTION_IDS
EMOTION_PUZZLES = ['emo-' + str(i) for i in range(1, 34+1)]
EMOTION_ALL_PUZZLES = [EMOTION_RUNAROUND] + EMOTION_METAS + EMOTION_PUZZLES

GAMES_SUPERMETA = 'games-supermeta'
GAMES_SUBMETAS = ['games-antemeta']
GAMES_PUZZLES = ['games-' + str(i) for i in range(1, 18+1)]
GAMES_ALL_PUZZLES = [GAMES_SUPERMETA] + GAMES_SUBMETAS + GAMES_PUZZLES

POKEMON_SUPERMETA = 'pokemon-supermeta'
POKEMON_SUBMETAS = ['pokemon-meta-' + str(i) for i in range(1,3+1)]
POKEMON_PUZZLES =  ['pokemon-' + type + '-' + str(i) for i in range(1,12+1) for type in ['unevolved','evolved']]
POKEMON_ALL_PUZZLES = [POKEMON_SUPERMETA] + POKEMON_SUBMETAS + POKEMON_PUZZLES

SCIFI_SUPERMETA = 'scifi-supermeta'
SCIFI_SUBMETAS = ['scifi-meta-' + str(i) for i in range(1, 6+1)]
SCIFI_PUZZLES = ['scifi-' + str(i) for i in range(1, 18+1)]
SCIFI_ALL_PUZZLES = [SCIFI_SUPERMETA] + SCIFI_SUBMETAS + SCIFI_PUZZLES

HACKING_PHASES = [('scout',8), ('build',8), ('deploy',6)]
HACKING_SUPERMETA = 'hacking-supermeta'
HACKING_SUBMETAS = ['hacking-' + phase + '-meta' for phase, cnt in HACKING_PHASES]
HACKING_RUNAROUNDS = ['hacking-' + phase + '-runaround' for phase, cnt in HACKING_PHASES]
HACKING_PUZZLES_BY_PHASE = {
    p: ['hacking-%s-%s' % (p, i) for i in range(1,cnt+1)] for (p, cnt) in HACKING_PHASES}
HACKING_PUZZLES = [p for lst in HACKING_PUZZLES_BY_PHASE.values() for p in lst]
HACKING_ALL_PUZZLES = [HACKING_SUPERMETA] + HACKING_RUNAROUNDS + HACKING_SUBMETAS + HACKING_PUZZLES

EVENTS = ['event-' + str(i) for i in range(1,5+1)]

EMOTION_INTERACTIONS = ['encounter-' + emotion for emotion in (EMOTION_IDS + ['buzzy'])]
ISLAND_RECOVERY_INTERACTIONS = [island + '-recovery' for island in ISLAND_IDS]
FINALES = ['emotional-finale', 'grand-finale']

# Emotional finale should be after the emotion interactions; grand finale should be at the end
INTERACTIONS_AND_FINALES = EMOTION_INTERACTIONS + [FINALES[0]] + ISLAND_RECOVERY_INTERACTIONS + [FINALES[1]]
INTERACTIONS = EMOTION_INTERACTIONS + ISLAND_RECOVERY_INTERACTIONS

ALL_METAS = (EMOTION_METAS +
                 [GAMES_SUPERMETA] + GAMES_SUBMETAS +
                 [POKEMON_SUPERMETA] + POKEMON_SUBMETAS +
                 [SCIFI_SUPERMETA] + SCIFI_SUBMETAS +
                 [HACKING_SUPERMETA] + HACKING_SUBMETAS)

ROUND_PUZZLE_MAP = {
  'memories': EMOTION_ALL_PUZZLES,
  'games': GAMES_ALL_PUZZLES,
  'pokemon': POKEMON_ALL_PUZZLES,
  'scifi': SCIFI_ALL_PUZZLES,
  'hacking': HACKING_ALL_PUZZLES,
  'events': EVENTS,
  'interactions': INTERACTIONS,
  'finales': FINALES,
  'unlocks': ISLAND_UNLOCKS,
}

ISLAND_SUPERMETAS = [island + '-supermeta' for island in ISLAND_IDS]

OBJECTIVE_PUZZLES = ( FINALES + ISLAND_UNLOCKS + ISLAND_RECOVERY_INTERACTIONS + ISLAND_SUPERMETAS +
                      GAMES_SUBMETAS + POKEMON_SUBMETAS + SCIFI_SUBMETAS +
                      HACKING_SUBMETAS + HACKING_RUNAROUNDS +
                      [EMOTION_RUNAROUND] + EMOTION_INTERACTIONS + EMOTION_METAS + EVENTS)
