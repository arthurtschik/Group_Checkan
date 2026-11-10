"""Minimal encoders for Android binary XML (AndroidManifest / res xml) and resources.arsc."""
import struct

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# Value types
T_REF, T_STRING, T_DEC, T_HEX, T_BOOL = 0x01, 0x03, 0x10, 0x11, 0x12


def _pad4(b):
    return b + b"\0" * ((4 - len(b) % 4) % 4)


def string_pool(strings):
    """UTF-16 string pool chunk."""
    offsets, data = [], b""
    for s in strings:
        offsets.append(len(data))
        enc = s.encode("utf-16-le")
        n = len(enc) // 2
        if n > 0x7FFF:
            data += struct.pack("<HH", 0x8000 | (n >> 16), n & 0xFFFF)
        else:
            data += struct.pack("<H", n)
        data += enc + b"\0\0"
    data = _pad4(data)
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    body = b"".join(struct.pack("<I", o) for o in offsets) + data
    size = header_size + len(body)
    return struct.pack("<HHIIIIII", 0x0001, header_size, size, len(strings), 0, 0, strings_start, 0) + body


class Node:
    def __init__(self, tag, attrs=None, children=None):
        self.tag = tag
        self.attrs = attrs or []  # list of (ns_android:bool, name, res_id or None, type, value)
        self.children = children or []


def a(name, typ, value, res_id=None):
    """android:-namespaced attribute if res_id given, plain attribute otherwise."""
    return (res_id is not None, name, res_id, typ, value)


def encode_xml(root):
    # collect attribute names with resource ids first (resource map), then other strings
    res_names, res_ids, others = [], [], []

    def walk(n):
        for (is_ns, name, rid, typ, val) in n.attrs:
            if rid is not None:
                if name not in res_names:
                    res_names.append(name)
                    res_ids.append(rid)
            elif name not in others:
                others.append(name)
            if typ == T_STRING and val not in others:
                others.append(val)
        if n.tag not in others:
            others.append(n.tag)
        for c in n.children:
            walk(c)

    walk(root)
    for s in ("android", ANDROID_NS):
        if s not in others:
            others.append(s)
    strings = res_names + [s for s in others if s not in res_names]
    idx = {s: i for i, s in enumerate(strings)}

    chunks = [string_pool(strings)]
    rm = b"".join(struct.pack("<I", r) for r in res_ids)
    chunks.append(struct.pack("<HHI", 0x0180, 8, 8 + len(rm)) + rm)

    NONE = 0xFFFFFFFF
    ns_body = struct.pack("<II", idx["android"], idx[ANDROID_NS])
    chunks.append(struct.pack("<HHIII", 0x0100, 16, 24, 1, NONE) + ns_body)

    def emit(n):
        attrs = sorted(n.attrs, key=lambda x: (x[2] if x[2] is not None else 0xFFFFFFFF, x[1]))
        ab = b""
        for (is_ns, name, rid, typ, val) in attrs:
            ns = idx[ANDROID_NS] if is_ns else NONE
            if typ == T_STRING:
                raw, data = idx[val], idx[val]
            elif typ == T_BOOL:
                raw, data = NONE, 0xFFFFFFFF if val else 0
            else:
                raw, data = NONE, val & 0xFFFFFFFF
            ab += struct.pack("<IIIHBBI", ns, idx[name], raw, 8, 0, typ, data)
        ext = struct.pack("<IIHHHHHH", NONE, idx[n.tag], 20, 20, len(attrs), 0, 0, 0)
        body = ext + ab
        chunks.append(struct.pack("<HHIII", 0x0102, 16, 16 + len(body), 1, NONE) + body)
        for c in n.children:
            emit(c)
        chunks.append(struct.pack("<HHIIIII", 0x0103, 16, 24, 1, NONE, NONE, idx[n.tag]))

    emit(root)
    chunks.append(struct.pack("<HHIII", 0x0101, 16, 24, 1, NONE) + ns_body)
    body = b"".join(chunks)
    return struct.pack("<HHI", 0x0003, 8, 8 + len(body)) + body


def config(density=0, sdk=0):
    c = bytearray(64)
    struct.pack_into("<I", c, 0, 64)
    struct.pack_into("<H", c, 14, density)
    struct.pack_into("<H", c, 24, sdk)
    return bytes(c)


def encode_arsc(package_id, package_name, types):
    """types: list of (type_name, [entry_names], spec_flags, [(config_bytes, {entry_index: file_path})])"""
    values = []
    for _, _, _, cfgs in types:
        for _, m in cfgs:
            for p in m.values():
                if p not in values:
                    values.append(p)
    gpool = string_pool(values)
    type_pool = string_pool([t[0] for t in types])
    keys = []
    for t in types:
        for k in t[1]:
            if k not in keys:
                keys.append(k)
    key_pool = string_pool(keys)

    tchunks = b""
    for ti, (tname, entries, spec_flags, cfgs) in enumerate(types, start=1):
        flags = b"".join(struct.pack("<I", spec_flags) for _ in entries)
        tchunks += struct.pack("<HHIBBHI", 0x0202, 16, 16 + len(flags), ti, 0, 0, len(entries)) + flags
        for cfg, m in cfgs:
            header_size = 20 + len(cfg)
            offs, ent = b"", b""
            for ei, ename in enumerate(entries):
                if ei in m:
                    offs += struct.pack("<I", len(ent))
                    ent += struct.pack("<HHI", 8, 0, keys.index(ename))
                    ent += struct.pack("<HBBI", 8, 0, T_STRING, values.index(m[ei]))
                else:
                    offs += struct.pack("<I", 0xFFFFFFFF)
            entries_start = header_size + len(offs)
            size = entries_start + len(ent)
            tchunks += struct.pack("<HHIBBHII", 0x0201, header_size, size, ti, 0, 0, len(entries), entries_start) + cfg + offs + ent

    name = package_name.encode("utf-16-le")[:254].ljust(256, b"\0")
    pkg_header = 288
    type_off = pkg_header
    key_off = type_off + len(type_pool)
    pkg_body = type_pool + key_pool + tchunks
    pkg = struct.pack("<HHII", 0x0200, pkg_header, pkg_header + len(pkg_body), package_id) + name + \
        struct.pack("<IIIII", type_off, len(types), key_off, len(keys), 0) + pkg_body
    body = gpool + pkg
    return struct.pack("<HHII", 0x0002, 12, 12 + len(body), 1) + body
