#!/usr/bin/env python3
"""Draw the Shush mark, launcher sizes, and Play listing graphics.

The geometry matches ShushMark in app/src/main/java/com/galynte/shush/ui/Drawings.kt.
Coordinates are a 108 grid (the adaptive-icon viewport):

  moon outer  (45, 54) r19
  moon cut    (55, 50) r15.5
  bars        x 66 / 73.5 / 81, heights 12 / 22 / 14, centered on y 54
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
FONT = Path("/usr/share/fonts/truetype/macos")

NIGHT = (20, 18, 40, 255)  # #141228
ICON_BG = (27, 23, 51, 255)  # #1B1733
MOON = (230, 222, 255, 255)  # #E6DEFF
BARS = (213, 201, 255, 255)  # #D5C9FF
TEXT = (243, 240, 255, 255)
MUTED = (163, 155, 191, 255)
GLOW = (213, 201, 255, 42)

LAUNCHER = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT / name), size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def paste_color(base: Image.Image, color, mask: Image.Image):
    layer = Image.new("RGBA", base.size, color)
    base.paste(layer, (0, 0), mask)


def draw_mark(side: int, background=None, monochrome=False, radius=None) -> Image.Image:
    image = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    if background is not None:
        tile = Image.new("RGBA", (side, side), background)
        if radius:
            mask = Image.new("L", (side, side), 0)
            ImageDraw.Draw(mask).rounded_rectangle((0, 0, side - 1, side - 1), radius=radius, fill=255)
            image.paste(tile, (0, 0), mask)
        else:
            image.paste(tile, (0, 0))

    scale = side / 108
    moon = (255, 255, 255, 255) if monochrome else MOON
    bars = (255, 255, 255, 255) if monochrome else BARS

    def oval(cx, cy, r, fill):
        mask = Image.new("L", (side, side), 0)
        box = [int((cx - r) * scale), int((cy - r) * scale), int((cx + r) * scale), int((cy + r) * scale)]
        ImageDraw.Draw(mask).ellipse(box, fill=fill)
        return mask

    if not monochrome:
        glow = oval(50, 54, 28, 255)
        # Soften the glow by scaling its alpha.
        glow = glow.point(lambda p: int(p * (GLOW[3] / 255)))
        paste_color(image, GLOW[:3] + (255,), glow)

    outer = oval(45, 54, 19, 255)
    inner = oval(55, 50, 15.5, 255)
    crescent = Image.new("L", (side, side), 0)
    crescent.paste(outer, (0, 0))
    # Punch the inner circle out of the outer one.
    cut = Image.eval(inner, lambda p: 255 - p)
    crescent = Image.frombytes("L", (side, side), bytes(min(a, b) for a, b in zip(crescent.tobytes(), cut.tobytes())))
    paste_color(image, moon, crescent)

    draw = ImageDraw.Draw(image)
    for left, height in ((66, 12), (73.5, 22), (81, 14)):
        top = 54 - height / 2
        box = [left * scale, top * scale, (left + 5) * scale, (top + height) * scale]
        draw.rounded_rectangle(box, radius=2.5 * scale, fill=bars)
    return image


def gradient(width, height, left, right) -> Image.Image:
    image = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(image)
    for x in range(width):
        color = lerp(left, right, x / (width - 1))
        draw.line([(x, 0), (x, height)], fill=color[:3])
    return image


def feature_graphic() -> Image.Image:
    image = gradient(1024, 500, (16, 14, 32), (48, 40, 86)).convert("RGBA")
    mark = draw_mark(300, background=ICON_BG, radius=68)
    image.alpha_composite(mark, (78, 100))
    draw = ImageDraw.Draw(image)
    draw.text((430, 150), "Shush", font=font("Inter-SemiBold.ttf", 108), fill=TEXT)
    draw.text((434, 280), "White noise for the night", font=font("Inter-Regular.ttf", 36), fill=MUTED)
    draw.text((434, 360), "White   ·   Pink   ·   Brown", font=font("Inter-Medium.ttf", 24), fill=BARS)
    return image.convert("RGB")


def main():
    for density, side in LAUNCHER.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = draw_mark(side, background=ICON_BG)
        icon.save(folder / "ic_launcher.png")
        icon.save(folder / "ic_launcher_round.png")

    for density, side in FOREGROUND.items():
        folder = RES / f"drawable-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        draw_mark(side).save(folder / "ic_launcher_foreground.png")
        draw_mark(side, monochrome=True).save(folder / "ic_launcher_monochrome.png")

    play = ROOT / "docs" / "play-store"
    brand = ROOT / "docs" / "assets" / "brand"
    shots = ROOT / "docs" / "assets" / "images"
    for folder in (play, brand, shots):
        folder.mkdir(parents=True, exist_ok=True)

    draw_mark(512, background=ICON_BG).save(play / "icon-512.png")
    feature_graphic().save(play / "feature-graphic.png", optimize=True)
    draw_mark(512, background=ICON_BG, radius=118).save(brand / "mark.png")
    write_svg(brand / "shush-mark.svg")
    print("wrote brand assets")


def write_svg(path: Path):
    path.write_text(
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" role="img" aria-label="Shush">
  <rect width="108" height="108" rx="24" fill="#1B1733"/>
  <circle cx="50" cy="54" r="28" fill="#D5C9FF" fill-opacity="0.16"/>
  <path fill="#E6DEFF" fill-rule="evenodd" d="M45 35 a19 19 0 1 0 0.1 0 z M55 34.5 a15.5 15.5 0 1 1 -0.1 0 z"/>
  <rect x="66" y="48" width="5" height="12" rx="2.5" fill="#D5C9FF"/>
  <rect x="73.5" y="43" width="5" height="22" rx="2.5" fill="#D5C9FF"/>
  <rect x="81" y="47" width="5" height="14" rx="2.5" fill="#D5C9FF"/>
</svg>
""",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
