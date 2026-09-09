import sys

DQ = chr(34)
BS = chr(92)

path = sys.argv[1]
src = open(path, encoding='utf-8').read()

# Produce a code-only view of each line with strings replaced by '' and
# ${...} contents recursively stripped of their own strings.
def code_view(text):
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == '/' and i + 1 < n and text[i + 1] == '/':
            break
        if ch == DQ:
            if text[i:i + 3] == DQ * 3:
                end = text.find(DQ * 3, i + 3)
                i = (end + 3) if end >= 0 else n
                continue
            i += 1
            while i < n and text[i] != DQ and text[i] != '\n':
                if text[i] == BS:
                    i += 2
                    continue
                if text[i] == '$' and i + 1 < n and text[i + 1] == '{':
                    depth = 1
                    i += 2
                    inner = []
                    while i < n and depth > 0:
                        if text[i] == '{':
                            depth += 1
                        elif text[i] == '}':
                            depth -= 1
                            if depth == 0:
                                break
                        inner.append(text[i])
                        i += 1
                    i += 1
                    out.append(code_view(''.join(inner)))
                    continue
                i += 1
            if i < n and text[i] == DQ:
                i += 1
            continue
        out.append(ch)
        i += 1
    return ''.join(out)

lines = src.split('\n')
pd = 0
bd = 0
for ln, raw in enumerate(lines, 1):
    view = code_view(raw)
    before_p, before_b = pd, bd
    for ch in view:
        if ch == '(':
            pd += 1
        elif ch == ')':
            pd -= 1
        elif ch == '{':
            bd += 1
        elif ch == '}':
            bd -= 1
    if pd < before_p and pd < 0:
        print(f'paren goes negative at line {ln}: {raw.strip()[:90]!r}')
        break
print(f'final parens={pd} braces={bd}')
for ln in (57, 231, 233, 240, 295, 296, 309, 348, 349, 363, 364, 365, 366, 373):
    print('line', ln, repr(lines[ln - 1].rstrip()))
