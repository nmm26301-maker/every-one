"""Build the common ECDICT subset from an original ecdict.csv; no network required."""
import csv, sqlite3, pathlib, re, hashlib, json, sys
source = pathlib.Path(sys.argv[1] if len(sys.argv)>1 else '../dictionary-data/ecdict.csv')
data = source.read_bytes(); sha = hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
forced = set('a small habit can make big difference coffee hello learn read book world morning thank sentence english practice ticket you i cat travel hotel'.split())
rows = []
for row in csv.DictReader(data.decode('utf-8-sig').splitlines()):
    word = row['word'].lower()
    if not re.fullmatch("[a-z][a-z'-]*", word) or not row['translation']: continue
    ranks = [int(row[k]) for k in ('bnc','frq') if row[k].isdigit() and int(row[k])>0]
    rows.append((0 if word in forced else min(ranks) if ranks else 999999,word,row))
rows.sort(key=lambda x:(x[0],x[1])); selected={}
for rank,word,row in rows:
    if word not in selected:selected[word]=row
    if len(selected)==20000:break
path = pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/lexicon.db';path.unlink(missing_ok=True)
with sqlite3.connect(path) as db:
    db.executescript('CREATE TABLE lexicon(word TEXT PRIMARY KEY,phonetic TEXT,translation TEXT,definition TEXT);CREATE TABLE forms(surface TEXT PRIMARY KEY,lemma TEXT);')
    for word,row in selected.items():
        db.execute('INSERT INTO lexicon VALUES(?,?,?,?)',(word,row['phonetic'],row['translation'].replace('\\n','\n'),row['definition'].replace('\\n','\n')))
        for form in row['exchange'].split('/'):
            if ':' in form:
                for f in form.split(':',1)[1].split(','):
                    if re.fullmatch("[a-z][a-z'-]*",f) and f!=word:db.execute('INSERT OR IGNORE INTO forms VALUES(?,?)',(f,word))
    count = db.execute('SELECT count(*) FROM forms').fetchone()[0]
meta={'source':'https://github.com/skywind3000/ECDICT','license':'MIT','csv_git_blob_sha1':sha,'entries':len(selected),'forms':count}
path.with_name('lexicon-source.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2));print(meta)
