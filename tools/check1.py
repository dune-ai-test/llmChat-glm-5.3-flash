import re
import sys

path = sys.argv[1]
src = open(path, encoding='utf-8').read()
DQ = chr(34)
BS = chr(92)
print('triple quotes:', src.count(DQ * 3))
print('escaped quotes:', src.count(BS + DQ))
# strip line comments (done during char scan; no pre-strip)
no_comments = src
# strip strings char-by-char, keeping ${...} contents counted as code, and // comments
out = []
i = 0
n = len(no_comments)
while i < n:
    ch = no_comments[i]
    if ch == '/' and i + 1 < n and no_comments[i + 1] == '/':
        nl = no_comments.find('\n', i)
        i = n if nl < 0 else nl
        continue
    if ch == DQ:
        if no_comments[i:i + 3] == DQ * 3:
            # skip multiline string until closing triple quote
            end = no_comments.find(DQ * 3, i + 3)
            i = (end + 3) if end >= 0 else n
            out.append('""')
            continue
        i += 1
        buf = []
        while i < n and no_comments[i] != DQ and no_comments[i] != '\n':
            if no_comments[i] == BS:
                i += 2
                continue
            if no_comments[i] == '$' and i + 1 < n and no_comments[i + 1] == '{':
                # template: copy its inner content (balanced braces) as code
                depth = 1
                i += 2
                inner = []
                while i < n and depth > 0:
                    if no_comments[i] == '{':
                        depth += 1
                    elif no_comments[i] == '}':
                        depth -= 1
                        if depth == 0:
                            break
                    inner.append(no_comments[i])
                    i += 1
                i += 1
                out.append(''.join(inner))
                continue
            i += 1
        if i < n and no_comments[i] == DQ:
            i += 1
        out.append('""')
        continue
    out.append(ch)
    i += 1
s = ''.join(out)
print('braces', s.count('{') - s.count('}'))
print('parens', s.count('(') - s.count(')'))
print('brackets', s.count('[') - s.count(']'))
