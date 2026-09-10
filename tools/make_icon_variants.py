# Renders 5 Apple-style icon variants for Aster as previews (squircle + circle + grid sheet).
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont
import numpy as np, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "tools", "icon_preview")
os.makedirs(OUT, exist_ok=True)
S = 4096
U = S / 108.0
CX = CY = 54.0

# ------------------------------------------------------------ primitives --
def quad_bezier(p0, p1, p2, n=32):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t**2*p2[0],
             (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t**2*p2[1])
            for t in (i/n for i in range(n+1))]

def cubic_bezier(p0, p1, p2, p3, n=32):
    return [((1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t**2*p2[0] + t**3*p3[0],
             (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t**2*p2[1] + t**3*p3[1])
            for t in (i/n for i in range(n+1))]

def arc(cx, cy, r, a0, a1, n=32):
    return [(cx + r*math.cos(math.radians(a0 + (a1-a0)*i/n)),
             cy + r*math.sin(math.radians(a0 + (a1-a0)*i/n)))
            for i in range(n+1)]

def px(shape):
    return [(p[0]*U, p[1]*U) for p in shape]

def sparkle(cx, cy, R, k=0.14, n=40):
    N, E, Sm, W = (cx, cy-R), (cx+R, cy), (cx, cy+R), (cx-R, cy)
    d = R * k
    pts = []
    for a, b, (sx, sy) in ((N, E, (1, -1)), (E, Sm, (1, 1)), (Sm, W, (-1, 1)), (W, N, (-1, -1))):
        pts += quad_bezier(a, (cx + sx*d, cy + sy*d), b, n)
    return pts

def bubble_outline(x0=28.0, y0=30.0, x1=80.0, y1=70.0, r=13.0):
    pts = [(x0+r, y0), (x1-r, y0)]
    pts += arc(x1-r, y0+r, r, -90, 0)
    pts += [(x1, y1-r)]
    pts += arc(x1-r, y1-r, r, 0, 90)
    pts += [(x0+0.55*(x1-x0), y1)]
    tip = (x0 - 0.055*(x1-x0), y1 + 0.32*(y1-y0))
    pts += cubic_bezier((x0+0.55*(x1-x0), y1),
                        (x0+0.42*(x1-x0), y1+0.14*(y1-y0)),
                        (x0+0.28*(x1-x0), y1+0.28*(y1-y0)), tip)
    pts += cubic_bezier(tip,
                        (x0+0.02*(x1-x0), y1+0.26*(y1-y0)),
                        (x0, y1-0.05*(y1-y0)), (x0, y1-0.30*(y1-y0)))
    pts += [(x0, y0+r)]
    pts += arc(x0+r, y0+r, r, 180, 270)
    return pts

def fit(shapes, max_radius, center=(CX, CY)):
    all_pts = [p for s in shapes for p in s]
    xs = [p[0] for p in all_pts]; ys = [p[1] for p in all_pts]
    mx, my = (min(xs)+max(xs))/2, (min(ys)+max(ys))/2
    rmax = max(math.hypot(p[0]-mx, p[1]-my) for p in all_pts)
    k = max_radius / rmax
    return [[((p[0]-mx)*k + center[0], (p[1]-my)*k + center[1]) for p in s] for s in shapes]

def linear_bg(stops, diag=True, bloom=None, vignette=0.0):
    if diag:
        t = (np.linspace(0, 1, S)[:, None] + np.linspace(0, 1, S)[None, :]) / 2.0
    else:
        t = np.linspace(0, 1, S)[:, None] * np.ones((1, S))
    img = np.zeros((S, S, 3))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = np.clip((t - t0) / (t1 - t0), 0, 1)[..., None]
        img += m * (np.array(c1, dtype=np.float64) - np.array(c0, dtype=np.float64))[None, None, :]
    img += np.array(stops[0][1], dtype=np.float64)[None, None, :]
    yy, xx = np.mgrid[0:S, 0:S]
    if bloom is not None:
        bx, by, br, ba = bloom
        dd = np.hypot(xx - bx*S, yy - by*S) / (br*S)
        img += 255 * (ba * np.clip(1 - dd, 0, 1) ** 2.2)[..., None]
    if vignette > 0:
        dd2 = np.hypot(xx - S, yy - S) / (1.15 * S)
        img *= (1 - vignette * np.clip(dd2, 0, 1))[..., None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")

def glow(canvas, shape_pts, radius_u, color, alpha, offset_u=(0, 0)):
    sil = Image.new("L", (S, S), 0)
    ImageDraw.Draw(sil).polygon(px(shape_pts), fill=alpha)
    sil = sil.filter(ImageFilter.GaussianBlur(int(radius_u * U)))
    g = Image.new("RGBA", (S, S), color + (255,)); g.putalpha(sil)
    canvas.alpha_composite(g, (int(offset_u[0]*U), int(offset_u[1]*U)))

def mask_from(draw_fn):
    m = Image.new("L", (S, S), 0)
    draw_fn(ImageDraw.Draw(m))
    return m

def paste_gradient(canvas, mask, stops, diag=True):
    grad = linear_bg(stops, diag)
    canvas.paste(grad, (0, 0), mask)

# --------------------------------------------------------------- variants --
BRAND = [(0.00, (0x7D, 0x7A, 0xFF)), (0.50, (0x5B, 0x5B, 0xD6)), (1.00, (0xA4, 0x4B, 0xE8))]

def v1_sparkle_cluster():
    img = linear_bg([(0.0, (0x4F, 0x46, 0xE5)), (0.55, (0x6D, 0x3A, 0xED)), (1.0, (0x9D, 0x33, 0xEA))],
                    bloom=(0.22, 0.18, 0.42, 0.20), vignette=0.10)
    main = sparkle(54, 55, 18.5, k=0.15)
    comp = sparkle(71.5, 33.5, 6.2, k=0.16)
    dot = sparkle(36.5, 71.5, 3.0, k=0.18)
    glow(img, main, 10, (255, 255, 255), 70)
    d = ImageDraw.Draw(img)
    d.polygon(px(main), fill=(255, 255, 255, 255))
    d.polygon(px(comp), fill=(255, 255, 255, 235))
    d.polygon(px(dot), fill=(255, 255, 255, 210))
    return img

def v2_light_bubble():
    img = linear_bg([(0.0, (0xFA, 0xFA, 0xFC)), (1.0, (0xEC, 0xEC, 0xF2))], diag=False,
                    bloom=(0.2, 0.15, 0.5, 0.5))
    tint = linear_bg([(0.0, (0x60, 0x5C, 0xF0)), (0.5, (0x7C, 0x4D, 0xF6)), (1.0, (0xA4, 0x45, 0xF7))])
    bub, spk = fit([bubble_outline(), sparkle(54, 49.5, 13.8, k=0.15)], 32.0)
    # soft shadow
    bub_mask = mask_from(lambda d: d.polygon(px(bub), fill=255))
    sh = bub_mask.filter(ImageFilter.GaussianBlur(int(2.6*U)))
    sh_img = Image.new("RGBA", (S, S), (58, 58, 140, 255)); sh_img.putalpha(sh.point(lambda v: v*60//255))
    img.alpha_composite(sh_img, (0, int(2.2*U)))
    body = ImageChops.subtract(bub_mask, mask_from(lambda d: d.polygon(px(spk), fill=255)))
    img.paste(tint, (0, 0), body)
    return img

def v3_asterisk():
    img = linear_bg(BRAND, bloom=(0.22, 0.18, 0.42, 0.18), vignette=0.10)
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

def v4_glass_orb():
    img = linear_bg([(0.0, (0x55, 0x51, 0xE8)), (0.55, (0x76, 0x4B, 0xEE)), (1.0, (0xA8, 0x4B, 0xF0))],
                    bloom=(0.22, 0.18, 0.42, 0.22), vignette=0.12)
    R = 23.0
    orb_mask = mask_from(lambda d: d.ellipse([(54-R)*U, (54-R)*U, (54+R)*U, (54+R)*U], fill=255))
    orb = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fill = Image.new("RGBA", (S, S), (255, 255, 255, 96))
    orb.paste(fill, (0, 0), orb_mask)
    # diagonal glass sheen, brightest top-left, clipped to the orb
    yy, xx = np.mgrid[0:S, 0:S]
    a = (64 * np.clip(1 - (xx + yy) / (2 * S * 0.85), 0, 1) ** 1.6).astype(np.uint8)
    sheen = Image.new("RGBA", (S, S), (255, 255, 255, 255)); sheen.putalpha(Image.fromarray(a))
    orb.paste(sheen, (0, 0), ImageChops.multiply(orb_mask, Image.fromarray(a)))
    # thin bright rim + soft top crescent highlight
    rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(rim)
    dr.ellipse([(54-R)*U, (54-R)*U, (54+R)*U, (54+R)*U],
               outline=(255, 255, 255, 190), width=int(1.1*U))
    dr.arc([(54-R+2.0)*U, (54-R+2.0)*U, (54+R-2.0)*U, (54+R-2.0)*U],
           start=160, end=285, fill=(255, 255, 255, 150), width=int(1.7*U))
    rim = rim.filter(ImageFilter.GaussianBlur(int(0.5*U)))
    orb.alpha_composite(rim)
    img.alpha_composite(orb)
    spk = sparkle(54, 54.5, 11.2, k=0.15)
    glow(img, spk, 6, (255, 255, 255), 90)
    ImageDraw.Draw(img).polygon(px(spk), fill=(255, 255, 255, 255))
    return img

def v5_ai_ring():
    img = linear_bg([(0.0, (0x1A, 0x1A, 0x38)), (1.0, (0x24, 0x22, 0x54))], diag=False,
                    bloom=(0.5, 0.45, 0.6, 0.10))
    yy, xx = np.mgrid[0:S, 0:S]
    dx, dy = xx - 54*U, yy - 54*U
    ang = np.arctan2(dy, dx)
    t = (ang + math.pi) / (2*math.pi)
    pal = [(0.00, (0x5A, 0xC8, 0xFA)), (0.25, (0x7D, 0x7A, 0xFF)), (0.50, (0xB5, 0x7B, 0xFF)),
           (0.72, (0xFF, 0x7B, 0xAC)), (0.88, (0xFF, 0xB5, 0x6B)), (1.00, (0x5A, 0xC8, 0xFA))]
    ts = np.linspace(0, 1, 2048)
    lut = np.zeros((2048, 3))
    for ch in range(3):
        lut[:, ch] = np.interp(ts, [p[0] for p in pal], [p[1][ch] for p in pal])
    idx = np.clip((t * 2047).astype(int), 0, 2047)
    ring_rgb = lut[idx]
    rad = np.hypot(dx, dy) / U
    R, hw = 21.0, 2.6
    a = np.clip(1 - np.abs(rad - R) / hw, 0, 1) ** 1.4
    ring = Image.fromarray(np.dstack([ring_rgb, (a*255).astype(np.uint8)]).astype(np.uint8), "RGBA")
    glow_layer = ring.filter(ImageFilter.GaussianBlur(int(5*U)))
    img.alpha_composite(glow_layer)
    ring_fine = ring.filter(ImageFilter.GaussianBlur(int(0.6*U)))
    img.alpha_composite(ring_fine)
    spk = sparkle(54, 54, 8.2, k=0.15)
    glow(img, spk, 5, (255, 255, 255), 80)
    ImageDraw.Draw(img).polygon(px(spk), fill=(255, 255, 255, 255))
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

VARIANTS = {
    "variant1_sparkle": v1_sparkle_cluster,
    "variant2_light":   v2_light_bubble,
    "variant3_asterisk": v3_asterisk,
    "variant4_glass":   v4_glass_orb,
    "variant5_airing":  v5_ai_ring,
}

arts = {}
for name, fn in VARIANTS.items():
    art = fn()
    arts[name] = art
    for mname, im in masked(art).items():
        im.save(os.path.join(OUT, f"{name}_{mname}.png"))
    print("rendered", name)

# ------------------------------------------------------------------ grid --
CELL, LABEL = 480, 64
current_path = os.path.join(OUT, "icon_squircle.png")
tiles = [("current", Image.open(current_path).convert("RGBA"))]
for name in VARIANTS:
    tiles.append((name, Image.open(os.path.join(OUT, f"{name}_squircle.png"))))
cols = 3
rows = math.ceil(len(tiles) / cols)
try:
    font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 34)
except OSError:
    font = ImageFont.load_default()
sheet = Image.new("RGBA", (cols*CELL, rows*(CELL+LABEL)), (250, 250, 252, 255))
d = ImageDraw.Draw(sheet)
for i, (label, im) in enumerate(tiles):
    cx, cy = (i % cols)*CELL, (i // cols)*(CELL+LABEL)
    sheet.alpha_composite(im.resize((CELL, CELL), Image.LANCZOS), (cx, cy))
    d.text((cx + CELL//2, cy + CELL + 30), label.replace("_", " "), font=font,
           fill=(40, 40, 46, 255), anchor="mm")
sheet.convert("RGB").save(os.path.join(OUT, "variants_grid.png"))
print("grid done")
