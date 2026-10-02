#!/usr/bin/env python3
"""Frames raw app captures into store screenshots: a caption over the brand teal, the capture below.

Reads docs/store/raw/<platform>/<lang>/<n>-<name>.png and writes docs/store/<platform>/<lang>/<n>-<name>.png.
  app-store: 1320x2868 (iPhone 6.9")
  play-store: 1080x1920 (phone, 9:16)
Also draws the Play feature graphic (1024x500) at docs/store/play-store/feature-graphic.png.
Needs Pillow built with libraqm for Arabic shaping (pip3 install pillow), and rsvg-convert for the glyph.
"""
import io
import re
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, features

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "docs/store/raw"
OUT = ROOT / "docs/store"
FONTS = ROOT / "core/designsystem/src/main/res/font"

# Design system: primary, primaryContainer, onPrimary (core/designsystem/.../theme/Color.kt).
PRIMARY = (0x0B, 0x6E, 0x6E)
PRIMARY_DEEP = (0x06, 0x4A, 0x4A)
ON_PRIMARY = (0xFF, 0xFF, 0xFF)
PRIMARY_CONTAINER = (0xD7, 0xEC, 0xEA)

SIZES = {"app-store": (1320, 2868), "play-store": (1080, 1920)}

CAPTIONS = {
    "en": {
        "1-search": ("Find any paper", "Search millions of scholarly works,\nor paste a DOI, arXiv ID or link"),
        "2-preview": ("Preview, then save", "Abstract, authors and citations at a glance"),
        "3-library": ("Track your reading", "Mark each paper To read, Reading or Read"),
        "4-library-search": ("Your library, offline", "Search your saved papers anytime,\neven without a connection"),
        "5-reader": ("Read with your notes", "Open the PDF and jot down the summary,\nmethod and findings as you go"),
        "6-collections": ("Organize into collections", "Group papers by project, thesis or course,\nand export them as BibTeX"),
    },
    "ar": {
        "1-search": ("ابحث عن أي ورقة بحثية", "ملايين الأعمال العلمية في مكان واحد،\nأو الصق DOI أو معرّف arXiv أو رابطًا"),
        "2-preview": ("اطّلع ثم احفظ", "الملخص والمؤلفون والاستشهادات في لمحة"),
        "3-library": ("تابع قراءاتك", "صنّف كل ورقة: للقراءة، قيد القراءة، مقروءة"),
        "4-library-search": ("مكتبتك معك دائمًا", "ابحث في أوراقك المحفوظة في أي وقت،\nحتى دون اتصال"),
        "5-reader": ("اقرأ وملاحظاتك بجانبك", "افتح ملف PDF ودوّن الخلاصة\nوالمنهجية والنتائج أثناء القراءة"),
        "6-collections": ("نظّم أوراقك في مجموعات", "جمّع الأوراق حسب المشروع أو الرسالة أو المقرر،\nوصدّرها بصيغة BibTeX"),
    },
}


def font(lang: str, weight: str, size: int) -> ImageFont.FreeTypeFont:
    name = f"ibm_plex_sans_arabic_{weight}.ttf" if lang == "ar" else f"inter_{weight}.ttf"
    return ImageFont.truetype(str(FONTS / name), size, layout_engine=ImageFont.Layout.RAQM)


def background(width: int, height: int) -> Image.Image:
    """A vertical gradient from the primary to a deeper teal."""
    top, bottom = PRIMARY, PRIMARY_DEEP
    column = Image.new("RGB", (1, height))
    for y in range(height):
        t = y / (height - 1)
        column.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return column.resize((width, height))


def rounded(image: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), radius, fill=255)
    out = image.convert("RGBA")
    out.putalpha(mask)
    return out


def draw_centered(draw: ImageDraw.ImageDraw, text: str, fnt, y: int, width: int, fill, lang: str, spacing: int) -> int:
    direction = "rtl" if lang == "ar" else "ltr"
    for line in text.split("\n"):
        box = draw.textbbox((0, 0), line, font=fnt, direction=direction)
        draw.text(((width - (box[2] - box[0])) / 2 - box[0], y), line, font=fnt, fill=fill, direction=direction)
        y += fnt.size + spacing
    return y


def with_status_bar(shot: Image.Image, lang: str) -> Image.Image:
    """Android captures are rendered without system bars: adds a 24dp status bar (time, signal, battery).
    In Arabic the bar is mirrored, as Android does for right-to-left locales."""
    dp = shot.width / 411
    bar_h = round(24 * dp)
    out = Image.new("RGB", (shot.width, shot.height + bar_h), shot.getpixel((shot.width // 2, 2)))
    out.paste(shot, (0, bar_h))
    draw = ImageDraw.Draw(out)
    ink = (0x1B, 0x1F, 0x23)
    clock = ImageFont.truetype(str(FONTS / "inter_medium.ttf"), round(14 * dp))
    margin = round(16 * dp)
    time_w = draw.textlength("9:41", font=clock)
    rtl = lang == "ar"
    time_x = shot.width - margin - time_w if rtl else margin
    draw.text((time_x, bar_h / 2), "9:41", font=clock, fill=ink, anchor="lm")

    # Signal triangle and battery, on the side opposite the clock.
    icons_w = 32 * dp
    x = margin if rtl else shot.width - margin - icons_w
    cy = bar_h / 2
    s = 11 * dp
    draw.polygon([(x, cy + s / 2), (x + s, cy + s / 2), (x + s, cy - s / 2)], fill=ink)
    bx = x + s + 6 * dp
    draw.rounded_rectangle((bx, cy - 6 * dp, bx + 8 * dp, cy + 6 * dp), radius=1.5 * dp, fill=ink)
    draw.rectangle((bx + 2.5 * dp, cy - 7.5 * dp, bx + 5.5 * dp, cy - 6 * dp), fill=ink)
    return out


def frame(capture: Path, platform: str, lang: str, key: str) -> Image.Image:
    width, height = SIZES[platform]
    canvas = background(width, height).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    title, subtitle = CAPTIONS[lang][key]

    unit = width / 100
    y = round(height * 0.055)
    y = draw_centered(draw, title, font(lang, "semibold", round(unit * 7.4)), y, width, ON_PRIMARY, lang, round(unit * 1.2))
    y += round(unit * 1.6)
    y = draw_centered(draw, subtitle, font(lang, "medium", round(unit * 3.9)), y, width, PRIMARY_CONTAINER, lang, round(unit * 1.4))

    # The capture, scaled to fill the space below the caption, with a bezel and a soft shadow.
    shot = Image.open(capture).convert("RGB")
    if platform == "play-store":
        shot = with_status_bar(shot, lang)
    top = y + round(unit * 5)
    bottom_margin = round(height * 0.035)
    target_h = height - top - bottom_margin
    target_w = round(shot.width * target_h / shot.height)
    max_w = round(width * 0.84)
    if target_w > max_w:
        target_w, target_h = max_w, round(shot.height * max_w / shot.width)
    shot = shot.resize((target_w, target_h), Image.LANCZOS)
    radius = round(target_w * 0.085)
    bezel = round(unit * 1.1)
    x = (width - target_w) // 2

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (x - bezel, top - bezel + round(unit * 1.5), x + target_w + bezel, top + target_h + bezel + round(unit * 1.5)),
        radius + bezel, fill=(0, 0, 0, 90))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(unit * 2.5)))
    ImageDraw.Draw(canvas).rounded_rectangle(
        (x - bezel, top - bezel, x + target_w + bezel, top + target_h + bezel), radius + bezel, fill=(0x10, 0x16, 0x1A))
    canvas.alpha_composite(rounded(shot, radius), (x, top))
    return canvas.convert("RGB")


def feature_graphic() -> Image.Image:
    """Play's 1024x500 banner: the icon's glyph beside the English and Arabic names and a tagline."""
    width, height = 1024, 500
    canvas = background(width, height).convert("RGBA")

    # The glyph alone, in white, from the icon's source SVG (its background rect dropped).
    svg = (ROOT / "docs/brand/app-icon.svg").read_text()
    svg = re.sub(r"<rect[^>]*/>", "", svg)
    png = subprocess.run(["rsvg-convert", "-w", "340"], input=svg.encode(), capture_output=True, check=True).stdout
    glyph = Image.open(io.BytesIO(png)).convert("RGBA")
    canvas.alpha_composite(glyph, (40, (height - glyph.height) // 2))

    draw = ImageDraw.Draw(canvas)
    x = 400
    draw.text((x, 118), "Hashiya", font=font("en", "semibold", 88), fill=ON_PRIMARY)
    draw.text((x, 222), "حاشية", font=font("ar", "semibold", 72), fill=ON_PRIMARY, direction="rtl")
    draw.text((x, 340), "Find, save and track research papers", font=font("en", "medium", 30), fill=PRIMARY_CONTAINER)
    return canvas.convert("RGB")


def main() -> None:
    assert features.check("raqm"), "This Pillow has no libraqm, so Arabic would render unshaped; use a Python whose Pillow has it (e.g. Homebrew's)"
    count = 0
    for capture in sorted(RAW.glob("*/*/*.png")):
        platform, lang, key = capture.parent.parent.name, capture.parent.name, capture.stem
        out = OUT / platform / lang / capture.name
        out.parent.mkdir(parents=True, exist_ok=True)
        frame(capture, platform, lang, key).save(out, optimize=True)
        count += 1
    print(f"Framed {count} screenshots.")
    feature_graphic().save(OUT / "play-store/feature-graphic.png", optimize=True)
    print("Drew the Play feature graphic.")


if __name__ == "__main__":
    main()
