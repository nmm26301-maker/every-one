"""Execute native review table, joins and defer/reference queries verbatim in host SQLite."""
import pathlib,re,json,sqlite3
source=(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/java/org/openenglish/reader/StudioStore.java').read_text()
def sql(name):
 expression=re.search(r'public static final String '+name+r' = (.+);',source).group(1)
 pieces=re.findall(r'"(?:[^"\\]|\\.)*"|REVIEW_SQL',expression)
 return ''.join(sql('REVIEW_SQL') if p=='REVIEW_SQL' else json.loads(p) for p in pieces)
assert source.index('db.rawQuery(PREVIOUS_REVIEW_SQL')<source.index('long id = db.insertOrThrow("practice"')
TABLE=sql('REVIEW_PLAN_TABLE');QUERY=sql('REVIEW_SCHEDULE_SQL');DEFER=sql('DEFER_REVIEW_SQL');PREVIOUS=sql('PREVIOUS_REVIEW_SQL');REF=sql('PLAN_REFERENCE_SQL')
def create():
 db=sqlite3.connect(':memory:');db.row_factory=sqlite3.Row
 db.executescript("CREATE TABLE materials(id INTEGER PRIMARY KEY,title TEXT,type TEXT,cues TEXT);CREATE TABLE practice(id INTEGER PRIMARY KEY,material INTEGER,anchor INTEGER,reference TEXT,heard TEXT,recording TEXT,created INTEGER);")
 db.execute(TABLE);db.executemany('INSERT INTO materials VALUES(?,?,?,?)',[(1,'audio','audio','[]'),(2,'video','video','[]'),(3,'text','text','[]')]);db.commit();return db
db=create();checks=0
def check(ok,name):
 global checks
 assert ok,name;checks+=1
def add(i,m=1,r='same',h='wrong',file='',time=None,anchor=0):db.execute('INSERT INTO practice VALUES(?,?,?,?,?,?,?)',(i,m,anchor,r,h,file,i if time is None else time))
def rows():return [dict(r) for r in db.execute(QUERY)]
def defer(i,due):db.execute(DEFER,(due,i));return db.execute('SELECT changes()').fetchone()[0]
def ref(i,m,r):return db.execute(REF,(i,m,r)).fetchone()
def invalid(command,args,name):
 try:db.execute(command,args)
 except sqlite3.IntegrityError:check(True,name)
 else:raise AssertionError(name)
add(1);check(rows()[0]['planned_due'] is None,'legacy wrong starts without persisted plan')
check(defer(1,1000)==1,'snooze creates one plan for newest attempt')
check(rows()[0]['planned_due']==1000 and rows()[0]['planned_interval']==0,'wrong snooze stores due but no mastery interval')
check(defer(1,0)==1 and rows()[0]['planned_due']==0,'restore to due replaces same plan')
check(db.execute('SELECT count(*) FROM review_plan').fetchone()[0]==1,'one row per material/reference')
check(ref(1,1,'same')['heard']=='wrong','backup reference lookup valid')
check(ref(1,2,'same') is None,'backup mismatched material rejected')
check(ref(1,1,'edited') is None,'backup mismatched original rejected')
check(ref(999,1,'same') is None,'backup missing attempt rejected')
check(db.execute(PREVIOUS,(1,'same')).fetchone()['interval']==0,'read current plan before next write')
add(2,h='same');check(rows()[0]['id']==2 and rows()[0]['planned_due'] is None,'new writing does not inherit stale-plan join')
check(db.execute(PREVIOUS,(1,'same')).fetchone() is None,'stale plan not used to advance interval')
check(defer(1,2000)==0,'outdated card cannot defer newer attempt')
db.execute('INSERT OR REPLACE INTO review_plan VALUES(?,?,?,?,?)',(1,'same',2,2000,1));check(rows()[0]['planned_interval']==1,'new correct plan visible')
check(db.execute(PREVIOUS,(1,'same')).fetchone()['due']==2000,'due progression can read newest correct plan')
add(3,file='studio-recordings/a.m4a',h='spoken');check(rows()[0]['id']==2,'recorded ASR leaves writing plan unchanged')
check(defer(3,3000)==0 and ref(3,1,'same') is None,'recorded ASR cannot become dictation plan')
add(4,h='');check(rows()[0]['id']==2,'unfinished empty writing leaves plan')
check(defer(4,4000)==0 and ref(4,1,'same') is None,'empty writing cannot become plan')
check(defer(2,0)==1 and rows()[0]['planned_interval']==1,'manual restore preserves correct interval')
add(5,m=2);check(defer(5,5000)==1,'same original independent in second material')
check(db.execute('SELECT count(*) FROM review_plan').fetchone()[0]==2,'two material plans coexist')
add(6,m=3);check(defer(6,6000)==0,'article cannot be a scheduled subtitle card')
add(7,r='other',time=20);check(defer(7,900)==1,'different original independent')
check([r['id'] for r in rows()]==[2,7,5],'cards ordered by earliest due')
add(8,r='other',time=19);check([r['id'] for r in rows()]==[2,7,5],'newer id with older time not latest')
add(9,r='other',time=20,anchor=9);check([r['id'] for r in rows()]==[2,9,5],'same timestamp newest id wins despite anchor move')
check(defer(7,10000)==0,'stale same-time card cannot override new record')
invalid('INSERT OR REPLACE INTO review_plan VALUES(?,?,?,?,?)',(1,'same',2,-1,1),'negative due SQL constraint')
invalid('INSERT OR REPLACE INTO review_plan VALUES(?,?,?,?,?)',(1,'same',2,0,2),'invalid mastery interval SQL constraint')
invalid('INSERT INTO review_plan VALUES(?,?,?,?,?)',(1,'same',2,0,1),'duplicate plan SQL constraint')
check(rows()[0]['planned_due']==0,'failed replacements preserve prior valid row')
db.commit()
try:
 db.execute('BEGIN');db.execute('DELETE FROM review_plan');db.execute('DELETE FROM practice');
 db.execute('INSERT INTO review_plan VALUES(?,?,?,?,?)',(1,'same',2,-1,1))
except sqlite3.IntegrityError:db.rollback()
check(db.execute('SELECT count(*) FROM practice').fetchone()[0]==9,'invalid restore transaction retains learning rows')
check(db.execute('SELECT count(*) FROM review_plan').fetchone()[0]==3,'invalid restore transaction retains schedule')
# Actual JSON values and exact reference validation survive a host database round trip.
backup=json.loads(json.dumps({t:[dict(r) for r in db.execute('SELECT * FROM '+t)] for t in ['practice','review_plan']}))
other=create();other.executemany('INSERT INTO practice VALUES(?,?,?,?,?,?,?)',[tuple(r.values()) for r in backup['practice']])
for p in backup['review_plan']:
 assert other.execute(REF,(p['attempt'],p['material'],p['reference'])).fetchone()
 other.execute('INSERT INTO review_plan VALUES(?,?,?,?,?)',tuple(p.values()))
check([dict(r) for r in other.execute(QUERY)]==rows(),'JSON schedule values round trip in host SQLite')
other.execute('DELETE FROM review_plan');check(all(r['planned_attempt'] is None for r in other.execute(QUERY)),'old JSON without optional schedule has no inherited mappings')
db.execute('DELETE FROM review_plan WHERE attempt=?',(2,));db.execute('DELETE FROM practice WHERE id=?',(2,))
check(rows()[0]['id']==1 and rows()[0]['planned_due'] is None,'deleting latest reveals prior wrong as due')
db.execute('DELETE FROM review_plan WHERE material=?',(1,));db.execute('DELETE FROM practice WHERE material=?',(1,));db.execute('DELETE FROM materials WHERE id=?',(1,))
check([r['id'] for r in rows()]==[5],'material deletion leaves only other material review')
check(db.execute('SELECT count(*) FROM review_plan WHERE material=1').fetchone()[0]==0,'material deletion clears plans')
add(10,m=2,r='');check(defer(10,10000)==0,'empty original cannot create unrestorable schedule')
check(db.execute('SELECT count(*) FROM review_plan').fetchone()[0]==1,'empty-original action preserves valid schedule')
print(f'PASS: {checks} exact production schedule, deferral, reference and host SQLite transaction checks')
