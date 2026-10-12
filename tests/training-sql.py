"""Run the exact production SQL constants in a host SQLite regression fixture."""
import pathlib,re,json,sqlite3
root=pathlib.Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/org/openenglish/reader/StudioStore.java').read_text()
def constant(name):
    match=re.search(r'public static final String '+name+r' = ("(?:[^"\\]|\\.)*");',s)
    assert match,name
    return json.loads(match.group(1))
where=constant('PRACTICE_FILTER');day=constant('DAY_SENTENCES');attempts=constant('DAY_ATTEMPTS')
db=sqlite3.connect(':memory:');db.executescript('CREATE TABLE materials(id INTEGER PRIMARY KEY,title TEXT);CREATE TABLE practice(id INTEGER PRIMARY KEY,material INTEGER,anchor INTEGER,reference TEXT,heard TEXT,recording TEXT,created INTEGER);')
db.executemany('INSERT INTO materials VALUES(?,?)',[(1,'100% habits'),(2,'Under_score'),(3,'! literal')])
rows=[(1,1,0,'Take your time','Take time','',100),(2,1,0,'Take your time','','studio-recordings/a.m4a',101),(3,1,1,'Hello world','hello world','',102),(4,2,0,'Hello world','Hello world','',103),(5,1,0,'Edited subtitle','Edited subtitle','',104),(6,3,0,'Literal %_!','Literal %_!','',200)]
db.executemany('INSERT INTO practice VALUES(?,?,?,?,?,?,?)',rows)
checks=0
def check(ok,name):
    global checks
    assert ok,name
    checks+=1
def params(q='',kind='全部'):
    pattern='%'+q.strip().replace('!','!!').replace('%','!%').replace('_','!_')+'%'
    return (kind,kind,kind,pattern,pattern,pattern)
def records(q='',kind='全部',limit=30,offset=0):
    return db.execute('SELECT p.id'+where+' ORDER BY p.created DESC,p.id DESC LIMIT ? OFFSET ?',params(q,kind)+(limit,offset)).fetchall()
check(len(records())==6,'all records')
check(records(kind='录音')==[(2,)],'recording filter')
check(len(records(kind='听写'))==5,'dictation filter')
check(len(records('%'))==5,'literal percent title and reference')
check(records('_')==[(6,),(4,)],'literal underscore')
check(records('!')==[(6,)],'literal escape character')
check(records("' OR 1=1 --")==[],'bound search parameters')
check(records(limit=2,offset=2)==[(4,),(3,)],'ordered pages')
check(db.execute(day,(100,200)).fetchone()[0]==4,'distinct material/cue/reference, repeats excluded')
check(db.execute(attempts,(100,200)).fetchone()[0]==5,'all attempts, next midnight excluded')
check(db.execute(day,(200,201)).fetchone()[0]==1,'next day included')
db.execute('DELETE FROM practice WHERE id=?',(1,));check(db.execute(day,(100,200)).fetchone()[0]==4,'one repeat deletion keeps daily sentence')
db.execute('DELETE FROM practice WHERE id=?',(2,));check(db.execute(day,(100,200)).fetchone()[0]==3,'last repeat deletion adjusts goal')
print(f'PASS: {checks} production-SQL filtering, pagination and daily-count checks (host SQLite)')
