import re
import sys

DQ = chr(34)
BS = chr(92)

path = sys.argv[1]
src = open(path, encoding='utf-8').read()
out = []
i = 0
n = len(src)
while i < n:
    ch = src[i]
    if ch == '/' and i + 1 < n and src[i + 1] == '/':
        nl = src.find('\n', i)
        i = n if nl < 0 else nl
        continue
    if ch == DQ:
        if src[i:i + 3] == DQ * 3:
            end = src.find(DQ * 3, i + 3)
            i = (end + 3) if end >= 0 else n
            out.append('""')
            continue
        i += 1
        while i < n and src[i] != DQ and src[i] != '\n':
            if src[i] == BS:
                i += 2
                continue
            if src[i] == '$' and i + 1 < n and src[i + 1] == '{':
                depth = 1
                i += 2
                inner = []
                while i < n and depth > 0:
                    if src[i] == '{':
                        depth += 1
                    elif src[i] == '}':
                        depth -= 1
                        if depth == 0:
                            break
                    inner.append(src[i])
                    i += 1
                i += 1
                out.append(''.join(inner))
                continue
            i += 1
        if i < n and src[i] == DQ:
            i += 1
        out.append('""')
        continue
    out.append(ch)
    i += 1
s = ''.join(out)
# rebuild lines aligned to original (we kept \n)
stripped_lines = s.split('\n')
orig = src.split('\n')
depth = 0
opens = []
for ln, line in enumerate(stripped_lines, 1):
    for ch in line:
        if ch == '{':
            depth += 1
            opens.append(ln)
        elif ch == '}':
            depth -= 1
            if opens:
                opens.pop()
# after pass, report unmatched opens (top of stack) - but also print per-line
# where depth should return to 0 between top-level funs
print('final depth:', depth)
for p in opens[:10]:
    print('unmatched { opened at line', p, ':', repr(orig[p - 1][:70]))
# parens
pd = 0
popens = []
for ln, line in enumerate(stripped_lines, 1):
    for ch in line:
        if ch == '(':
            pd += 1
            popens.append(ln)
        elif ch == ')':
            pd -= 1
            if popens:
                popens.pop()
print('final paren depth:', pd)
for p in popens[:10]:
    print('unmatched ( opened at line', p, ':', repr(orig[p - 1][:70]))
