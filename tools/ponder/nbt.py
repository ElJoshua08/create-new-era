"""Minimal gzipped NBT reader/writer, enough for structure templates.

Values map to Python as: compound -> dict, list -> NbtList, int array -> IntArray,
and numbers carry their tag through small wrapper types (Byte, Short, Int, Long, Float, Double).
"""
import gzip
import struct


class Byte(int): pass
class Short(int): pass
class Int(int): pass
class Long(int): pass
class Float(float): pass
class Double(float): pass
class IntArray(list): pass


class NbtList(list):
    def __init__(self, tag, items=()):
        super().__init__(items)
        self.tag = tag


TAGS = {1: Byte, 2: Short, 3: Int, 4: Long, 5: Float, 6: Double, 8: str, 9: NbtList, 10: dict, 11: IntArray}
IDS = {v: k for k, v in TAGS.items()}
FMT = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}


def tag_of(value):
    if isinstance(value, bool):
        return 1
    for cls, tid in IDS.items():
        if type(value) is cls:
            return tid
    if isinstance(value, int):
        return 3
    if isinstance(value, float):
        return 6
    raise TypeError(type(value))


class _Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, n):
        out = self.data[self.pos:self.pos + n]
        self.pos += n
        return out

    def num(self, tid):
        return TAGS[tid](struct.unpack(FMT[tid], self.take(struct.calcsize(FMT[tid])))[0])

    def string(self):
        n = struct.unpack(">H", self.take(2))[0]
        return self.take(n).decode("utf-8")

    def payload(self, tid):
        if tid in FMT:
            return self.num(tid)
        if tid == 8:
            return self.string()
        if tid == 9:
            inner = self.take(1)[0]
            n = struct.unpack(">i", self.take(4))[0]
            return NbtList(inner, [self.payload(inner) for _ in range(n)])
        if tid == 10:
            out = {}
            while (t := self.take(1)[0]) != 0:
                name = self.string()
                out[name] = self.payload(t)
            return out
        if tid == 11:
            n = struct.unpack(">i", self.take(4))[0]
            return IntArray(struct.unpack(f">{n}i", self.take(4 * n)))
        raise ValueError(f"unsupported tag {tid}")


def read(path):
    r = _Reader(gzip.decompress(open(path, "rb").read()))
    tid = r.take(1)[0]
    r.string()
    return r.payload(tid)


def _write_payload(out, value, tid):
    if tid in FMT:
        out += struct.pack(FMT[tid], value)
    elif tid == 8:
        b = value.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif tid == 9:
        inner = value.tag if isinstance(value, NbtList) else (tag_of(value[0]) if value else 1)
        out += bytes([inner]) + struct.pack(">i", len(value))
        for v in value:
            _write_payload(out, v, inner)
    elif tid == 10:
        for k, v in value.items():
            t = tag_of(v)
            out += bytes([t])
            _write_payload(out, k, 8)
            _write_payload(out, v, t)
        out += b"\x00"
    elif tid == 11:
        out += struct.pack(">i", len(value)) + struct.pack(f">{len(value)}i", *value)


def write(path, root):
    out = bytearray([10])
    _write_payload(out, "", 8)
    _write_payload(out, root, 10)
    with open(path, "wb") as f:
        f.write(gzip.compress(bytes(out), mtime=0))
