#!/usr/bin/env python3
import sys, os, json, zipfile, zlib, pickle, io, struct, pickletools

class UniversalStub:
    def __init__(self, *args, **kwargs):
        self.__dict__ = {}
    def __setstate__(self, state):
        if isinstance(state, dict):
            self.__dict__.update(state)
        elif isinstance(state, tuple) and len(state) >= 2 and isinstance(state[1], dict):
            self.__dict__.update(state[1])
    def __repr__(self):
        return f'<Stub {getattr(self, "__dict__", {})}>'

class RevertableDict(dict):
    def __setstate__(self, state):
        if isinstance(state, tuple) and len(state) >= 2 and isinstance(state[1], dict):
            self.update(state[1])
        elif isinstance(state, dict):
            self.update(state)

class RevertableList(list):
    def __setstate__(self, state):
        if isinstance(state, tuple) and len(state) >= 2 and isinstance(state[1], list):
            self.extend(state[1])
        elif isinstance(state, list):
            self.extend(state)

class RevertableSet(set):
    def __setstate__(self, state):
        if isinstance(state, tuple) and len(state) >= 1 and isinstance(state[0], dict):
            self.update(state[0].keys())
        elif isinstance(state, dict):
            self.update(state.keys())
        elif isinstance(state, (list, set, tuple)):
            self.update(state)

class Persistent:
    def __init__(self, *args, **kwargs):
        self.__dict__ = {}
    def __setstate__(self, state):
        if isinstance(state, dict):
            self.__dict__.update(state)
        elif isinstance(state, tuple) and len(state) >= 2 and isinstance(state[1], dict):
            self.__dict__.update(state[1])

class Preferences:
    def __init__(self, *args, **kwargs):
        self.__dict__ = {}
    def __setstate__(self, state):
        if isinstance(state, dict):
            self.__dict__.update(state)
        elif isinstance(state, tuple) and len(state) >= 2 and isinstance(state[1], dict):
            self.__dict__.update(state[1])

class RenpyUnpickler(pickle.Unpickler):
    def find_class(self, module, name):
        if name == 'Persistent': return Persistent
        if name == 'Preferences': return Preferences
        if name == 'RevertableDict': return RevertableDict
        if name == 'RevertableList': return RevertableList
        if name == 'RevertableSet': return RevertableSet
        if name == 'set': return set
        return UniversalStub

def serialize_value_proto2(val):
    if isinstance(val, bool):
        return b'\x88' if val else b'\x89'
    elif isinstance(val, int):
        if 0 <= val <= 255:
            return b'K' + bytes([val])
        elif 0 <= val <= 65535:
            return b'M' + struct.pack('<H', val)
        elif -2147483648 <= val <= 2147483647:
            return b'J' + struct.pack('<i', val)
        else:
            s = str(val).encode('ascii') + b'L'
            return b'p' + bytes([len(s)]) + s
    elif isinstance(val, float):
        return b'G' + struct.pack('>d', val)
    elif isinstance(val, str):
        b = val.encode('utf-8')
        return b'X' + struct.pack('<I', len(b)) + b
    elif val is None:
        return b'N'
    return None

def patch_pickle_bytes(raw_bytes, modifications):
    decomp = bytearray(raw_bytes)
    ops = list(pickletools.genops(decomp))
    patches = []

    for i in range(len(ops) - 1):
        op, arg, pos = ops[i]
        if op.name in ('SHORT_BINSTRING', 'BINUNICODE', 'BINSTRING'):
            key_name = arg
            if key_name in modifications:
                v_idx = i + 1
                if ops[v_idx][0].name in ('BINPUT', 'LONG_BINPUT'):
                    v_idx += 1
                if v_idx < len(ops):
                    v_op, v_val, v_pos = ops[v_idx]
                    v_end_pos = ops[v_idx+1][2] if v_idx + 1 < len(ops) else len(decomp)
                    new_val = modifications[key_name]
                    encoded = serialize_value_proto2(new_val)
                    if encoded is not None:
                        patches.append((v_pos, v_end_pos, encoded))

    patches.sort(key=lambda p: p[0], reverse=True)
    for start, end, new_bytes in patches:
        decomp[start:end] = new_bytes
    return bytes(decomp)

def read_file(filepath):
    is_zip = zipfile.is_zipfile(filepath)
    variables = []
    
    if is_zip:
        with zipfile.ZipFile(filepath, 'r') as z:
            if 'log' in z.namelist():
                raw = z.read('log')
                u = RenpyUnpickler(io.BytesIO(raw), encoding='latin1')
                loaded = u.load()
                roots = loaded[0] if isinstance(loaded, (tuple, list)) else loaded
                for k, v in roots.items():
                    if isinstance(k, str) and k.startswith('store.') and not k.startswith('store._'):
                        name = k[6:]
                        if isinstance(v, (int, float, bool, str, type(None))):
                            variables.append({
                                'raw_key': k,
                                'display_name': name,
                                'type': type(v).__name__,
                                'value': v
                            })
    else:
        with open(filepath, 'rb') as f:
            decomp = zlib.decompress(f.read())
        u = RenpyUnpickler(io.BytesIO(decomp), encoding='latin1')
        loaded = u.load()
        d = getattr(loaded, '__dict__', loaded if isinstance(loaded, dict) else {})
        for k, v in d.items():
            if isinstance(k, str) and isinstance(v, (int, float, bool, str, type(None))):
                variables.append({
                    'raw_key': k,
                    'display_name': k,
                    'type': type(v).__name__,
                    'value': v
                })

    variables.sort(key=lambda x: x['display_name'].lower())
    return {
        'status': 'ok',
        'is_save': is_zip,
        'variables': variables
    }

def save_file(filepath, modifications_json_path):
    with open(modifications_json_path, 'r', encoding='utf-8') as f:
        modifications = json.load(f)

    # Backup original by renaming to .bak
    bak_path = filepath + '.bak'
    if os.path.exists(bak_path):
        os.remove(bak_path)
    os.rename(filepath, bak_path)

    is_zip = zipfile.is_zipfile(bak_path)

    if is_zip:
        # Patch log entry and rewrite zip
        with zipfile.ZipFile(bak_path, 'r') as zin:
            with zipfile.ZipFile(filepath, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    content = zin.read(item.filename)
                    if item.filename == 'log':
                        content = patch_pickle_bytes(content, modifications)
                    zout.writestr(item, content)
    else:
        with open(bak_path, 'rb') as f:
            raw_decomp = zlib.decompress(f.read())
        patched = patch_pickle_bytes(raw_decomp, modifications)
        compressed = zlib.compress(patched, 9)
        with open(filepath, 'wb') as f:
            f.write(compressed)

    return {'status': 'ok', 'backup': bak_path}

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print(json.dumps({'status': 'error', 'message': 'Invalid arguments'}))
        sys.exit(1)
    
    action = sys.argv[1]
    filepath = sys.argv[2]
    
    try:
        if action == 'read':
            print(json.dumps(read_file(filepath)))
        elif action == 'save':
            mod_path = sys.argv[3]
            print(json.dumps(save_file(filepath, mod_path)))
    except Exception as e:
        print(json.dumps({'status': 'error', 'message': str(e)}))
        sys.exit(1)
