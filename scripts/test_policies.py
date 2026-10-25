"""Run pure-Java policy tests without a phone or API call."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/'java/live/pharmaradio/whatsappai'
names=['CallLaunchPolicy','CallStatePolicy','ToolResponsePolicy','LaunchAttempt','TelecomCallPolicy','WhatsAppAudioPolicy']
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(['javac','-proc:none','-encoding','UTF-8','-d',tmp]+[str(source/(n+'.java')) for n in names]+[str(p) for p in sorted((root/'tests').glob('*Test.java'))],check=True)
    for p in sorted((root/'tests').glob('*Test.java')):
        subprocess.run(['java','-cp',tmp,'live.pharmaradio.whatsappai.'+p.stem],check=True)
subprocess.run(['python3',str(root/'tests/package_isolation.py')],check=True)
