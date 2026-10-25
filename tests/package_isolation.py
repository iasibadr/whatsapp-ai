from pathlib import Path
import re,xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[1]
manifest=E.parse(root/'AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
assert "namespace 'live.pharmaradio.whatsappai'" in (root/'app/build.gradle').read_text()
assert any(x.get(a+'name')=='android.permission.CALL_PHONE' for x in manifest.findall('uses-permission'))
assert not any(x.get(a+'name')=='.CallBridgeService' for x in manifest.findall('application/service'))
for provider in manifest.findall('application/provider'):
    assert provider.get(a+'authorities').startswith('live.pharmaradio.whatsappai.')
source=(root/'java/live/pharmaradio/whatsappai/ShellAudioBridge.java').read_text(encoding='utf-8')
process=re.search(r'PROCESS = "([^"]+)"',source).group(1)
assert process!='whatsapp-relay-lab','Process collides with preserved lab'
assert process!='voice-autonome','Process collides with preserved SIM app'
print('PASS package, permissions, provider and process isolation')
