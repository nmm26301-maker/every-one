#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/reading-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -classpath "$JSON_JAR" -d build/reading-tests app/src/main/java/org/openenglish/reader/ReadingProgress.java tests/ReadingProgressTest.java
java -cp "build/reading-tests:$JSON_JAR" ReadingProgressTest
python3 - <<'PY'
import json,re
from pathlib import Path
s=json.loads(Path('app/src/main/assets/reading.json').read_text())['stories']
assert len(s)==18 and len({a['id'] for a in s})==18
words=[]
for a in s:
 assert a['level'] in ('A2','A2–B1') and len(a['paragraphs'])==3 and len(a['words'])==5
 for p in a['paragraphs']: assert len(p['en'])>60 and len(p['zh'])>20
 for w in a['words']:
  assert re.fullmatch('[a-z]+',w['en']) and w['zh'],w
  words.append(w['en'])
assert len(words)==90
print('Catalog verified: 18 stories, 54 bilingual paragraphs, 90 vocabulary cards.')
PY

