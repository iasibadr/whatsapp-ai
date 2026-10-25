"""Conservative publication check. Prints locations, never matched secrets."""
import argparse, pathlib, re, subprocess, zipfile, hashlib

def scan(name, data):
    rules = {
        'credential': rb'(?:sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})',
        'private key': rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
        'private path': rb'/home/[a-z][a-z0-9_-]*/|C:\\Users\\[^\\]+\\',
        'private network': rb'(?:192\.168\.[0-9]+\.[0-9]+|tail[a-z0-9]+\.ts\.net)',
    }
    # Exact, nonfunctional fixtures exercising key detection, never real credentials.
    data=data.replace(b'sk-synthetic-not-a-real-api-key-731000000000',b'TEST_FIXTURE')
    if name.endswith('AgentSharePolicyTest.java'):
        data=data.replace(b'-----BEGIN RSA PRIVATE KEY-----',b'TEST_FIXTURE')
    # Byte-identical Conscrypt 2.5.3 upstream binaries contain their own build paths.
    if name.endswith("libconscrypt_jni.so") and hashlib.sha256(data).hexdigest() in {'009ee440620105adefff81c5af4fafd23aebefff5b8d03d7180212c02983b532', 'a005cac24028f662269e969b13bca38fa7f759e1479eecbd4e48dba9cfdc889b', 'fd5a080ced130e2c2e7982cb9c82b5601fc84df61f41129dd0d3ecdbf2c96155', '9ee2eccb596cbbf5316572c11efa5ce4c2612ceba540c215b360e2750a665d87'}:
        data=data.replace(b"/home/prb/",b"UPSTREAM_BUILD/")
    bad=[]
    for label, pattern in rules.items():
        if re.search(pattern,data):bad.append((name,label))
    return bad

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apk',type=pathlib.Path);args=parser.parse_args()
    bad=[];count=0
    if args.apk:
        with zipfile.ZipFile(args.apk) as archive:
            for entry in archive.infolist():
                if entry.is_dir():continue
                count+=1
                if entry.filename.lower().endswith(('.pcm','.wav','.mp3','.p12','.jks','.keystore','.env')) or any(s in entry.filename for s in ['shared_prefs/','databases/']):bad.append((entry.filename,'private asset type'))
                data=archive.read(entry)
                bad+=scan(entry.filename,data)
                # Also scan UTF-16 strings as used in compiled Android resources.
                bad+=scan(entry.filename,data.decode('utf-16-le',errors='ignore').encode('utf-8'))
    else:
        root=pathlib.Path(__file__).resolve().parents[1]
        files=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().split('\0')
        for name in filter(None,files):
            if name=='scripts/privacy_scan.py':continue
            p=root/name;count+=1
            if p.suffix.lower() in ['.jks','.keystore','.p12','.pem','.env','.log']:bad.append((name,'private file type'))
            if p.suffix.lower()!='.apk':bad+=scan(name,p.read_bytes())
    for name,label in bad:print(label+': '+name)
    print(('FAIL' if bad else 'PASS')+f' publication pattern check: {count} files/entries (not a security certification)')
    raise SystemExit(bool(bad))
if __name__=='__main__':main()
