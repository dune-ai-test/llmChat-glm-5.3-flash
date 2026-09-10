# Renders 5 different icon *types* for Aster (squircle + circle previews + grid sheet).
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont
import numpy as np, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "tools", "icon_preview")
os.makedirs(OUT, exist_ok=True)
S = 4096
U = S / 108.0

# ------------------------------------------------------------ primitives --
def quad_bezier(p0, p1, p2, n=32):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t**2*p2[0],
             (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t**2*p2[1])
            for t in (i/n for i in range(n+1))]

def px(shape):
    return [(p[0]*U, p[1]*U) for p in shape]

def sparkle(cx, cy, R, k=0.14, n=40):
    N, E, Sm, W = (cx, cy-R), (cx+R, cy), (cx, cy+R), (cx-R, cy)
    d = R * k
    pts = []
    for a, b, (sx, sy) in ((N, E, (1, -1)), (E, Sm, (1, 1)), (Sm, W, (-1, 1)), (W, N, (-1, -1))):
        pts += quad_bezier(a, (cx + sx*d, cy + sy*d), b, n)
    return pts

def grad_img(stops, diag=True):
    if diag:
        t = (np.linspace(0, 1, S)[:, None] + np.linspace(0, 1, S)[None, :]) / 2.0
    else:
        t = np.linspace(0, 1, S)[:, None] * np.ones((1, S))
    img = np.zeros((S, S, 3))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = np.clip((t - t0) / (t1 - t0), 0, 1)[..., None]
        img += m * (np.array(c1, dtype=np.float64) - np.array(c0, dtype=np.float64))[None, None, :]
    img += np.array(stops[0][1], dtype=np.float64)[None, None, :]
    return np.clip(img, 0, 255).astype(np.uint8)

def bg_from(stops, diag=True, bloom=None, vignette=0.0):
    base = grad_img(stops, diag).astype(np.float64)
    yy, xx = np.mgrid[0:S, 0:S]
    if bloom is not None:
        bx, by, br, ba = bloom
        dd = np.hypot(xx - bx*S, yy - by*S) / (br*S)
        base += 255 * (ba * np.clip(1 - dd, 0, 1) ** 2.2)[..., None]
    if vignette > 0:
        dd2 = np.hypot(xx - S, yy - S) / (1.15 * S)
        base *= (1 - vignette * np.clip(dd2, 0, 1))[..., None]
    return Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB").convert("RGBA")

def glow(canvas, shape_pts, radius_u, color, alpha, offset_u=(0, 0)):
    sil = Image.new("L", (S, S), 0)
    ImageDraw.Draw(sil).polygon(px(shape_pts), fill=alpha)
    sil = sil.filter(ImageFilter.GaussianBlur(int(radius_u * U)))
    g = Image.new("RGBA", (S, S), color + (255,)); g.putalpha(sil)
    canvas.alpha_composite(g, (int(offset_u[0]*U), int(offset_u[1]*U)))

# ------------------------------------------------------------------ 1 orb --
def t1_orb():
    yy, xx = np.mgrid[0:S, 0:S]
    t = np.clip(np.hypot((xx - 54*U) / (0.78*S), (yy - 54*U) / (0.78*S)), 0, 1)
    c0, c1 = np.array([42, 46, 102]), np.array([14, 16, 36])
    img = Image.fromarray((c0[None, None, :] + t[..., None] * (c1 - c0)[None, None, :]).astype(np.uint8), "RGB").convert("RGBA")
    R = 23.5
    circle = Image.new("L", (S, S), 0)
    ImageDraw.Draw(circle).ellipse([(54-R)*U, (54-R)*U, (54+R)*U, (54+R)*U], fill=255)
    circle = circle.filter(ImageFilter.GaussianBlur(int(0.35*U)))
    # conic AI-palette fill with spherical shading (bright top-left, deep edge)
    dx, dy = xx - 54*U, yy - 54*U
    ang = (np.arctan2(dy, dx) + math.pi) / (2*math.pi)
    pal = [(0.00, (0x5A, 0xC8, 0xFA)), (0.25, (0x7D, 0x7A, 0xFF)), (0.50, (0xB5, 0x7B, 0xFF)),
           (0.72, (0xFF, 0x7B, 0xAC)), (0.88, (0xFF, 0xB5, 0x6B)), (1.00, (0x5A, 0xC8, 0xFA))]
    ts = np.linspace(0, 1, 2048)
    lut = np.zeros((2048, 3))
    for ch in range(3):
        lut[:, ch] = np.interp(ts, [p[0] for p in pal], [p[1][ch] for p in pal])
    rgb = lut[np.clip((ang * 2047).astype(int), 0, 2047)]
    rr = np.clip(np.hypot(dx, dy) / (R*U), 0, 1)
    rgb *= (1 - 0.32 * rr ** 1.3)[..., None]
    hld = np.hypot((xx - (54-7)*U) / (17*U), (yy - (54-8.5)*U) / (17*U))
    rgb += 255 * (0.38 * np.clip(1 - hld, 0, 1) ** 2)[..., None]
    img.paste(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB"), (0, 0), circle)
    # soft under-glow and rim
    glow_l = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gg = Image.new("RGBA", (S, S), (125, 122, 255, 255)); gg.putalpha(circle.point(lambda v: v * 60 // 255))
    glow_l.alpha_composite(gg, (0, int(3.5*U)))
    img.alpha_composite(glow_l.filter(ImageFilter.GaussianBlur(int(6*U))))
    rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(rim).ellipse([(54-R)*U, (54-R)*U, (54+R)*U, (54+R)*U],
                                outline=(255, 255, 255, 170), width=int(0.9*U))
    img.alpha_composite(rim.filter(ImageFilter.GaussianBlur(int(0.4*U))))
    # top crescent highlight
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).arc([(54-R+2.2)*U, (54-R+2.2)*U, (54+R-2.2)*U, (54+R-2.2)*U],
                           start=165, end=280, fill=(255, 255, 255, 160), width=int(1.6*U))
    img.alpha_composite(hl.filter(ImageFilter.GaussianBlur(int(1.1*U))))
    spk = sparkle(54, 54, 7.0, k=0.15)
    ImageDraw.Draw(img).polygon(px(spk), fill=(255, 255, 255, 255))
    return img

# --------------------------------------------------------- 2 flat asterisk --
def t2_asterisk():
    img = Image.new("RGBA", (S, S), (0x5B, 0x5B, 0xD6, 255))
    R, w = 20.0, 5.6
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(6):
        a = math.radians(i * 60)
        x2, y2 = 54 + R*math.cos(a), 54 + R*math.sin(a)
        d.line([(54*U, 54*U), (x2*U, y2*U)], fill=(255, 255, 255, 255), width=int(w*U))
        rr = w/2 * U
        d.ellipse([x2*U-rr, y2*U-rr, x2*U+rr, y2*U+rr], fill=(255, 255, 255, 255))
    img.alpha_composite(layer)
    return img

# ----------------------------------------------------------- 3 monogram A --
def t3_monogram():
    img = bg_from([(0.0, (0x34, 0xC7, 0xF0)), (0.55, (0x0A, 0x84, 0xFF)), (1.0, (0x2B, 0x3A, 0xF0))],
                  bloom=(0.24, 0.18, 0.42, 0.22), vignette=0.10)
    w = 6.4
    apex, fl, fr = (54.0, 33.0), (36.5, 76.0), (71.5, 76.0)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for a, b in ((apex, fl), (apex, fr)):
        d.line([(a[0]*U, a[1]*U), (b[0]*U, b[1]*U)], fill=(255, 255, 255, 255), width=int(w*U))
    tt = (61.0 - apex[1]) / (fl[1] - apex[1])
    xl = apex[0] + (fl[0]-apex[0])*tt; xr = apex[0] + (fr[0]-apex[0])*tt
    d.line([(xl*U, 61*U), (xr*U, 61*U)], fill=(255, 255, 255, 255), width=int(w*U))
    rr = w/2 * U
    for (x, y) in (apex, fl, fr, (xl, 61.0), (xr, 61.0)):
        d.ellipse([x*U-rr, y*U-rr, x*U+rr, y*U+rr], fill=(255, 255, 255, 255))
    img.alpha_composite(layer)
    return img

# -------------------------------------------------------------- 4 AI ring --
def t4_airing():
    img = bg_from([(0.0, (0x1A, 0x1A, 0x38)), (1.0, (0x24, 0x22, 0x54))], diag=False,
                  bloom=(0.5, 0.45, 0.6, 0.10))
    yy, xx = np.mgrid[0:S, 0:S]
    dx, dy = xx - 54*U, yy - 54*U
    t = ((np.arctan2(dy, dx) + math.pi) / (2*math.pi))
    pal = [(0.00, (0x5A, 0xC8, 0xFA)), (0.25, (0x7D, 0x7A, 0xFF)), (0.50, (0xB5, 0x7B, 0xFF)),
           (0.72, (0xFF, 0x7B, 0xAC)), (0.88, (0xFF, 0xB5, 0x6B)), (1.00, (0x5A, 0xC8, 0xFA))]
    ts = np.linspace(0, 1, 2048)
    lut = np.zeros((2048, 3))
    for ch in range(3):
        lut[:, ch] = np.interp(ts, [p[0] for p in pal], [p[1][ch] for p in pal])
    ring_rgb = lut[np.clip((t * 2047).astype(int), 0, 2047)]
    rad = np.hypot(dx, dy) / U
    R, hw = 21.0, 2.6
    a = (np.clip(1 - np.abs(rad - R) / hw, 0, 1) ** 1.4 * 255).astype(np.uint8)
    ring = Image.fromarray(np.dstack([ring_rgb, a]).astype(np.uint8), "RGBA")
    img.alpha_composite(ring.filter(ImageFilter.GaussianBlur(int(5*U))))
    img.alpha_composite(ring.filter(ImageFilter.GaussianBlur(int(0.6*U))))
    spk = sparkle(54, 54, 8.2, k=0.15)
    glow(img, spk, 5, (255, 255, 255), 80)
    ImageDraw.Draw(img).polygon(px(spk), fill=(255, 255, 255, 255))
    return img

# ---------------------------------------------------------- 5 light minimal --
def t5_light():
    img = bg_from([(0.0, (0xFB, 0xFB, 0xFD)), (1.0, (0xEC, 0xEC, 0xF3))], diag=False,
                  bloom=(0.2, 0.15, 0.5, 0.5))
    main = sparkle(54, 55, 19.5, k=0.15)
    comp = sparkle(72.5, 33.0, 6.6, k=0.16)
    grad = Image.fromarray(grad_img([(0.0, (0x60, 0x5C, 0xF0)), (0.5, (0x8B, 0x4D, 0xF6)), (1.0, (0xC4, 0x45, 0xF7))]))
    for shp, alpha in ((main, 42), (comp, 30)):
        sil = Image.new("L", (S, S), 0)
        ImageDraw.Draw(sil).polygon(px(shp), fill=alpha)
        sh = sil.filter(ImageFilter.GaussianBlur(int(2.2*U)))
        shi = Image.new("RGBA", (S, S), (90, 90, 160, 255)); shi.putalpha(sh)
        img.alpha_composite(shi, (int(1.2*U), int(1.8*U)))
    m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).polygon(px(main), fill=255)
    img.paste(grad, (0, 0), m)
    m2 = Image.new("L", (S, S), 0); ImageDraw.Draw(m2).polygon(px(comp), fill=235)
    img.paste(grad, (0, 0), m2)
    return img

# ----------------------------------------------------------------- masks --
def masked(art):
    a = np.array(art.resize((1024, 1024), Image.LANCZOS))
    yy, xx = np.mgrid[0:1024, 0:1024]
    nx, ny = xx/512.0 - 1.0, yy/512.0 - 1.0
    out = {}
    for name, m in {"squircle": (np.abs(nx)**5 + np.abs(ny)**5) <= 1.0,
                    "circle": (nx**2 + ny**2) <= 1.0}.items():
        al = np.where(m, 255, 0).astype(np.uint8)
        al = np.array(Image.fromarray(al).filter(ImageFilter.GaussianBlur(1.2)))
        im = Image.fromarray(a.copy(), "RGBA"); im.putalpha(Image.fromarray(al))
        out[name] = im
    return out

TYPES = {
    "type1_orb": t1_orb,
    "type2_asterisk": t2_asterisk,
    "type3_monogram": t3_monogram,
    "type4_airing": t4_airing,
    "type5_light": t5_light,
}

for name, fn in TYPES.items():
    art = fn()
    for mname, im in masked(art).items():
        im.save(os.path.join(OUT, f"{name}_{mname}.png"))
    print("rendered", name)

CELL, LABEL = 480, 64
tiles = [("current (ref)", Image.open(os.path.join(OUT, "icon_squircle.png")).convert("RGBA"))]
tiles += [(n, Image.open(os.path.join(OUT, f"{n}_squircle.png"))) for n in TYPES]
cols, rows = 3, 2
try:
    font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 34)
except OSError:
    font = ImageFont.load_default()
sheet = Image.new("RGBA", (cols*CELL, rows*(CELL+LABEL)), (250, 250, 252, 255))
d = ImageDraw.Draw(sheet)
for i, (label, im) in enumerate(tiles):
    cx, cy = (i % cols)*CELL, (i // cols)*(CELL+LABEL)
    sheet.alpha_composite(im.resize((CELL, CELL), Image.LANCZOS), (cx, cy))
    d.text((cx + CELL//2, cy + CELL + 30), label, font=font, fill=(40, 40, 46, 255), anchor="mm")
sheet.convert("RGB").save(os.path.join(OUT, "types_grid.png"))
print("grid done")
