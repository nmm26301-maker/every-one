"""Test exact production SQL: latest dictation per material/reference, using created then id."""
import json,re,pathlib,sqlite3
root=pathlib.Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/org/openenglish/reader/StudioStore.java').read_text()
match=re.search(r'public static final String REVIEW_SQL = ("(?:[^"\\]|\\.)*");',s);assert match
sql=json.loads(match.group(1));db=sqlite3.connect(':memory:')
db.executescript('CREATE TABLE materials(id INTEGER PRIMARY KEY,title TEXT,type TEXT,cues TEXT);CREATE TABLE practice(id INTEGER PRIMARY KEY,material INTEGER,anchor INTEGER,reference TEXT,heard TEXT,recording TEXT,created INTEGER);')
db.executemany('INSERT INTO materials VALUES(?,?,?,?)',[(1,'audio','audio','[]'),(2,'video','video','[]'),(3,'article','text','[]')])
checks=0
def check(ok,name):
 global checks
 assert ok,name
 checks+=1
def add(row):db.execute('INSERT INTO practice VALUES(?,?,?,?,?,?,?)',row)
def ids():return [r[0] for r in db.execute(sql)]
add((1,1,0,'same','wrong','',100));check(ids()==[1],'first incorrect dictation')
add((2,1,0,'same','same','',101));check(ids()==[2],'later correct suppresses older incorrect candidate')
add((3,1,0,'same','older-wrong','',99));check(ids()==[2],'created takes precedence over larger id')
add((4,1,0,'same','new-wrong','',101));check(ids()==[4],'id breaks timestamp tie')
add((5,1,0,'same','spoken','studio-recordings/a.m4a',102));check(ids()==[4],'recognized audio does not change dictation candidate')
add((6,1,0,'same','','',103));check(ids()==[4],'empty unfinished text ignored')
add((7,2,0,'same','other wrong','',104));check(ids()==[7,4],'materials kept independent')
add((8,1,8,'same','same','',105));check(ids()==[8,7],'same sentence moved to different anchor uses latest attempt')
add((9,3,0,'same','article text','',106));check(ids()==[8,7],'article not a subtitle dictation')
add((10,1,0,'another','wrong','',107));check(ids()==[10,8,7],'different reference remains independent')
db.execute('DELETE FROM practice WHERE id=8');check(ids()==[10,7,4],'history deletion reveals last remaining attempt')
print(f'PASS: {checks} exact production review-SQL checks (host SQLite)')
