#!/usr/bin/env python3
"""Exercise the exact distribution APK via its real Android UI, outside instrumentation."""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

apk, output = Path(sys.argv[1]).resolve(), Path(sys.argv[2])
output.mkdir(parents=True, exist_ok=True)
pkg = 'br.com.pocooptimizer.test'
activity = pkg+'/br.com.pocooptimizer.MainActivity'

def adb(*args, timeout=40):
    return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT, timeout=timeout)

def alive():
    try:
        return bool(adb('shell', 'pidof', pkg).strip())
    except subprocess.CalledProcessError:
        return False

def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/poco-window.xml')
    xml = adb('shell', 'cat', '/sdcard/poco-window.xml')
    (output/'window.xml').write_text(xml)
    return ET.fromstring(xml)

def matches(node, text):
    return node.attrib.get('text') == text or node.attrib.get('content-desc') == text

def wait_for(predicate, description, timeout=45):
    end = time.monotonic()+timeout
    while time.monotonic() < end:
        if not alive():
            raise AssertionError('App process died while waiting for '+description)
        root = ui()
        found = next((n for n in root.iter('node') if predicate(n)), None)
        if found is not None:
            return found
        time.sleep(1)
    raise AssertionError('UI did not show '+description)

def tap(text):
    node = wait_for(lambda n: matches(n,text), text)
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def screenshot(name):
    with (output/(name+'.png')).open('wb') as f:
        subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)

try:
    adb('install','-r',str(apk),timeout=120)
    adb('logcat','-c')
    for attempt in range(1,4):
        adb('shell','am','force-stop',pkg)
        launch = adb('shell','am','start','-W','-f','0x10008000','-a','android.intent.action.MAIN',
                     '-c','android.intent.category.LAUNCHER','-n',activity)
        (output/f'cold-start-{attempt}.txt').write_text(launch)
        wait_for(lambda n: matches(n,'POCO OPTIMIZER'),'dashboard')
        time.sleep(10)
        assert alive(), 'Process died after cold start'
        print(f'PASS cold start {attempt}: dashboard visible and process survived',flush=True)
    screenshot('distribution-dashboard')
    # Scroll the LazyColumn if the primary button is below a small emulator viewport.
    for _ in range(5):
        if any(matches(n,'ANALISAR DISPOSITIVO') for n in ui().iter('node')):
            break
        size = adb('shell','wm','size')
        w,h = map(int,re.search(r'(\d+)x(\d+)',size).groups())
        adb('shell','input','swipe',str(w//2),str(h*3//4),str(w//2),str(h//3),'350')
    tap('ANALISAR DISPOSITIVO')
    wait_for(lambda n: n.attrib.get('text','').startswith('Última análise concluída:')
             and 'Não disponível' not in n.attrib.get('text',''), 'completed real analysis',timeout=60)
    print('PASS real scan without storage permissions',flush=True)
    for label, heading in [('Limpeza','Limpeza inteligente'),('Segurança','Central de segurança'),
                            ('Desemp.','Desempenho'),('Ajustes','Preferências da análise')]:
        tap(label)
        wait_for(lambda n: matches(n,heading),heading)
        print('PASS navigation: '+label,flush=True)
    screenshot('distribution-settings')
    adb('shell','input','keyevent','KEYCODE_HOME')
    adb('shell','am','start','-W','-n',activity)
    wait_for(lambda n: n.attrib.get('package')==pkg,'foreground after resume')
    assert alive(), 'Process died after background/resume'
    print('PASS background/resume',flush=True)
finally:
    for name,args in [('logcat',['logcat','-d','-v','threadtime']),('crash',['logcat','-b','crash','-d','-v','threadtime'])]:
        try: (output/(name+'.txt')).write_text(adb(*args))
        except Exception as e: (output/(name+'-capture-error.txt')).write_text(str(e))
