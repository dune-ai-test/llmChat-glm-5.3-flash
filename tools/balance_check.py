import os

BS = chr(92)  # backslash
DQ = chr(34)  # double quote
SQ = chr(39)  # single quote


def balance(src):
    i, n = 0, len(src)
    # stack entries: ('brace', ch) or ('template', saved_mode)
    stack = []
    pairs = {'}': '{', ')': '(', ']': '['}
    opens = set('{([')
    mode = None  # None | 'sq' | 'dq' | 'tq'
    in_line = in_block = False
    while i < n:
        ch = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if in_line:
            if ch == '\n':
                in_line = False
            i += 1
            continue
        if in_block:
            if ch == '*' and nxt == '/':
                in_block = False
                i += 2
                continue
            i += 1
            continue
        if mode is None:
            if ch == '/' and nxt == '/':
                in_line = True
                i += 2
                continue
            if ch == '/' and nxt == '*':
                in_block = True
                i += 2
                continue
            if ch == DQ:
                if src[i:i + 3] == DQ * 3:
                    mode = 'tq'
                    i += 3
                    continue
                mode = 'dq'
                i += 1
                continue
            if ch == SQ:
                mode = 'sq'
                i += 1
                continue
            if ch in opens:
                stack.append(('brace', ch))
                i += 1
                continue
            if ch in pairs:
                if ch == '}' and stack and stack[-1][0] == 'template':
                    # this } closes a string-template: restore the string mode
                    saved_mode = stack.pop()[1]
                    mode = saved_mode
                    i += 1
                    continue
                if stack and stack[-1] == ('brace', pairs[ch]):
                    stack.pop()
                i += 1
                continue
            i += 1
            continue
        # inside a string literal
        if ch == BS:
            i += 2
            continue
        if mode == 'sq' and ch == SQ:
            mode = None
            i += 1
            continue
        if mode == 'dq' and ch == DQ:
            mode = None
            i += 1
            continue
        if mode == 'tq' and src[i:i + 3] == DQ * 3:
            mode = None
            i += 3
            continue
        if ch == '$' and nxt == '{':
            # enter template expression; remember to restore string mode
            stack.append(('template', mode, i))
            mode = None
            i += 2
            continue
        i += 1
    counts = {'{': 0, '(': 0, '[': 0}
    for entry in stack:
        if entry[0] == 'brace':
            counts[entry[1]] += 1
        elif entry[0] == 'template':
            counts['{'] += 1
    return counts


bad = []
for root, dirs, files in os.walk('app/src/main/java'):
    for f in sorted(files):
        if not f.endswith('.kt'):
            continue
        p = os.path.join(root, f)
        r = balance(open(p, encoding='utf-8').read())
        tot = r['{'] + r['('] + r['[']
        line = f'{p}: {r}'
        if tot:
            line += '  <<< MISMATCH'
            bad.append(p)
        print(line)
print('BAD:', len(bad))
