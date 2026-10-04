"""Check standalone APK contents and native-library 16KB load-segment alignment."""
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    names = apk.namelist()
    assert 'AndroidManifest.xml' in names and 'classes.dex' in names
    native = [name for name in names if name.startswith('lib/') and name.endswith('.so')]
    abis = {name.split('/')[1] for name in native}
    assert {'arm64-v8a', 'armeabi-v7a', 'x86_64'} <= abis, abis
    for name in native:
        if name.split('/')[1] not in {'arm64-v8a', 'x86_64'}:
            continue
        data = apk.read(name)
        assert data[:4] == b'\x7fELF'
        order = '<' if data[5] == 1 else '>'
        assert data[4] == 2
        phoff = struct.unpack_from(order + 'Q', data, 32)[0]
        phsize, count = struct.unpack_from(order + 'HH', data, 54)
        for i in range(count):
            offset = phoff + i * phsize
            kind = struct.unpack_from(order + 'I', data, offset)[0]
            if kind == 1:
                alignment = struct.unpack_from(order + 'Q', data, offset + 48)[0]
                assert alignment >= 16384, (name, alignment)
    print('Standalone universal APK; native 64-bit libraries support 16KB alignment.')
