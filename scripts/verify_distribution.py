#!/usr/bin/env python3
"""Audit the actual downloadable APK, including the startup regression boundary."""
import os
from pathlib import Path
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

apk = Path(sys.argv[1]).resolve()
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
analyzer = sdk / 'cmdline-tools/latest/bin/apkanalyzer'
manifest = ET.fromstring(subprocess.check_output([str(analyzer), 'manifest', 'print', str(apk)], text=True))
ns = '{http://schemas.android.com/apk/res/android}'
package = manifest.attrib['package']
assert package == 'br.com.pocooptimizer.test', package
permissions = {p.attrib[ns+'name'] for p in manifest.findall('uses-permission')}
assert permissions <= {package+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}, permissions
assert not any('fixtures' in str(p.attrib) for p in manifest.findall('./application/provider')), 'Test provider leaked into distributed APK'
assert not manifest.findall('instrumentation'), 'Test runner leaked into APK'
assert any(a.attrib[ns+'name'].endswith('MainActivity') and a.attrib.get(ns+'exported') == 'true'
           for a in manifest.findall('./application/activity')), 'Missing launchable Activity'
with zipfile.ZipFile(apk) as z:
    for entry in z.namelist():
        if entry.endswith('.dex'):
            assert b'FixtureDocumentsProvider' not in z.read(entry), 'Test fixture bytecode leaked into APK'
        if entry.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) and entry.endswith('.so'):
            data = z.read(entry)
            assert data[:5] == b'\x7fELF\x02', entry
            endian = '<' if data[5] == 1 else '>'
            offset = struct.unpack_from(endian+'Q', data, 32)[0]
            entry_size, count = struct.unpack_from(endian+'HH', data, 54)
            for i in range(count):
                header = struct.unpack_from(endian+'IIQQQQQQ', data, offset+i*entry_size)
                if header[0] == 1:
                    assert header[7] >= 16384, f'{entry}: LOAD alignment {header[7]} < 16 KiB'
            print('ELF alignment >= 16 KiB:', entry)
subprocess.run([str(sdk/'build-tools/35.0.0/zipalign'), '-c', '-P', '16', '4', str(apk)], check=True)
subprocess.run([str(sdk/'build-tools/35.0.0/apksigner'), 'verify', '--verbose', str(apk)], check=True)
print('PASS: distribution contains no test provider/runner or unnecessary permissions; signature and 16 KiB alignment verified.')
