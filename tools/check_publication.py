"""Read-only checks of publication content: README links, staged paths and common secret formats."""
from pathlib import Path
import json
import re
import subprocess
import urllib.parse

P=Path(__file__).resolve().parents[1]
readme=(P/'README.md').read_text(encoding='utf-8')
missing=[]
for link in re.findall(r'\]\(([^)]+)\)',readme):
    if re.match(r'^(?:https?://|#)',link):continue
    target=urllib.parse.unquote(link.split('#')[0])
    if target and not (P/target).exists():missing.append(target)
assert not missing,missing
names=subprocess.check_output(['git','diff','--cached','--name-only','--diff-filter=ACMR','-z'],cwd=P).decode('utf-8').split('\0')
names=[n for n in names if n]
blocked=[n for n in names if n.startswith(('run/','build/','.gradle/','validation/','source_models/','deliverables/'))
         or n.endswith(('.jar','.pyc','.blend1')) or '__pycache__/' in n]
assert not blocked,blocked
patterns=[rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{50,}',
          rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',rb'AKIA[0-9A-Z]{16}']
suspects=[]
for name in names:
    path=P/name
    if path.is_file() and path.stat().st_size<2_000_000:
        raw=path.read_bytes()
        if any(re.search(pattern,raw) for pattern in patterns):suspects.append(name)
assert not suspects,suspects
checks=json.loads((P/'docs/release-checks-1.3.3.json').read_text(encoding='utf-8'))
assert checks['gametests_each_sbw_version']==60 and checks['ammo_definitions_unchanged']==29
assert "version = '1.3.3'" in (P/'build.gradle').read_text()
print(json.dumps({'readme_links_valid':True,'staged_files':len(names),'unwanted_staged_files':blocked,
    'secret_pattern_matches':suspects,'version':'1.3.3'},ensure_ascii=False))
