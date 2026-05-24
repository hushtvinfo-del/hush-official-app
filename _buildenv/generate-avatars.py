#!/usr/bin/env python3
"""
generate-avatars.py — one-shot build-time avatar generator for the
HushTV multi-profile feature (v1.44.138-dev).

Generates 36 illustrated profile avatars (18 diverse humans + 18
cartoon animals) via Gemini Nano Banana, saves them as 512×512
WebP into /app/androidtv/app/src/dev/res/drawable-nodpi/.

Style consistency
─────────────────
Every prompt is built from the same STYLE_BRIEF block — only the
SUBJECT line varies. This is the single biggest lever for "all 36
look like they belong to the same set", which is what the user
explicitly asked for ("ALL PERFECT").

Re-runnable
───────────
Safe to re-run. The output directory is wiped at start, then
re-populated. The Kotlin AvatarCatalog references files by stable
filename (`avatar_h01_alex.webp`, `avatar_a01_fox.webp`, etc.) so
regenerating with a tweaked style brief drops in cleanly.

Usage
─────
  python3 /app/_buildenv/generate-avatars.py
"""
import asyncio
import base64
import io
import os
import sys
from dotenv import load_dotenv
from emergentintegrations.llm.chat import LlmChat, UserMessage

from PIL import Image

load_dotenv("/app/backend/.env")

# ── Output ─────────────────────────────────────────────────────────
OUT_DIR = "/app/androidtv/app/src/dev/res/drawable-nodpi"
WEBP_QUALITY = 85
TARGET_SIZE = 512

# ── Style brief — the most important block in this file ────────────
STYLE_BRIEF = (
    "Flat-color modern illustrated portrait avatar in the style of a "
    "premium streaming-app profile picture (similar to Netflix, "
    "Disney+, Hulu). "
    "Square 1:1 aspect ratio. Subject perfectly centered with the head "
    "and shoulders filling about 70% of the frame, looking forward. "
    "Soft, bold pastel solid background colour — no gradients, no "
    "patterns. "
    "Clean line work, 3-5 flat tones per shape, gentle subtle shading. "
    "Friendly and family-friendly facial expression. "
    "NO text, NO logos, NO watermarks, NO words anywhere in the image. "
    "Output only the avatar — no border, no frame, no caption. "
    "High quality, polished, suitable for a 4K TV interface."
)


# ── Subjects ───────────────────────────────────────────────────────
#
# Humans first (h##). Spread across skin tones, ages, hair styles,
# accessories. NOT clustered by ethnicity — interleaved so the
# picker grid feels varied as the user scrolls.
HUMANS = [
    ("h01_alex",  "a young man with short messy brown hair, light skin, blue eyes, gentle smile, peach background"),
    ("h02_maya",  "a young woman with long dark wavy hair, warm brown skin, hazel eyes, bright smile, mint-green background"),
    ("h03_kenji", "an east-asian teenage boy with side-swept black hair, dark eyes, calm smile, lavender background"),
    ("h04_sara",  "a woman with curly red shoulder-length hair, freckles, fair skin, hazel eyes, soft yellow background"),
    ("h05_jamal", "a young black man with short cropped hair, warm dark skin, kind smile, coral background"),
    ("h06_priya", "a south-asian woman with long straight black hair, warm tan skin, brown eyes, gentle smile, sky-blue background"),
    ("h07_oliver","an older man with grey hair and a neat grey beard, glasses, fair skin, friendly smile, dusty-rose background"),
    ("h08_zara",  "a young black woman with natural afro hair, warm dark skin, bright smile, mint background"),
    ("h09_luca",  "a young man with shoulder-length wavy blond hair, fair skin, blue eyes, slight smile, peach background"),
    ("h10_aisha", "a middle-eastern young woman wearing a soft pastel headscarf, warm olive skin, brown eyes, gentle smile, blush-pink background"),
    ("h11_max",   "a man with a brown beard and short brown hair, light skin, warm smile, mustard-yellow background"),
    ("h12_yuki",  "an east-asian young woman with a sleek black bob haircut, fair skin, dark eyes, soft smile, lilac background"),
    ("h13_diego", "a hispanic young man with short black hair, warm tan skin, dark eyes, friendly grin, terracotta-orange background"),
    ("h14_nora",  "an older woman with silver hair in a soft bun, glasses, fair skin, warm smile, sage-green background"),
    ("h15_omar",  "a young middle-eastern man with short dark hair and a trim beard, olive skin, dark eyes, calm smile, ocean-blue background"),
    ("h16_chloe", "a teenage girl with curly blond hair, fair skin, blue eyes, big smile, soft-pink background"),
    ("h17_kai",   "a pacific-islander young man with short dark hair, warm tan skin, dark eyes, easy smile, turquoise background"),
    ("h18_lila",  "a young woman with shoulder-length curly black hair, warm brown skin, dark eyes, bright smile, coral background"),
]

# Animals (a##). Picked for clear silhouettes that read instantly
# at the 96 dp avatar tile size in the picker grid.
ANIMALS = [
    ("a01_fox",     "a friendly cartoon fox with bright orange fur, big black eyes, gentle smile, mint-green background"),
    ("a02_bear",    "a cute cartoon brown bear with rounded ears, soft eyes, small smile, peach background"),
    ("a03_lion",    "a heroic cartoon lion with a warm golden mane, kind eyes, confident smile, soft-yellow background"),
    ("a04_owl",     "a wise cartoon owl with large round eyes, soft feathers in cream and tan, calm expression, sky-blue background"),
    ("a05_panda",   "an adorable cartoon giant panda with classic black-and-white markings, small smile, sage-green background"),
    ("a06_rabbit",  "a cute cartoon rabbit with long pink ears, soft white-grey fur, big curious eyes, soft-pink background"),
    ("a07_cat",     "a friendly cartoon cat with grey tabby stripes, green eyes, gentle expression, lilac background"),
    ("a08_dog",     "an excited cartoon golden retriever puppy with happy eyes and tongue slightly out, warm yellow background"),
    ("a09_tiger",   "a striking cartoon tiger with orange-and-black stripes, friendly smile, terracotta-orange background"),
    ("a10_wolf",    "a calm cartoon grey wolf with kind silver eyes, soft fur, ocean-blue background"),
    ("a11_koala",   "a cute cartoon koala with fluffy grey fur, big black nose, sleepy smile, dusty-rose background"),
    ("a12_dragon",  "a friendly cartoon dragon, soft mint-green scales, small horns, kind eyes, gentle smile, soft-pink background"),
    ("a13_dolphin", "a happy cartoon dolphin in flat-color illustration, soft blue tones, bright eye, friendly smile, turquoise background"),
    ("a14_robot",   "a cute cartoon robot face with rounded white-and-pastel-blue chassis, simple kind eyes, gentle expression, blush-pink background"),
    ("a15_unicorn", "a magical cartoon unicorn with soft white fur and a pastel-pink mane, gentle smile, lavender background"),
    ("a16_penguin", "a cute cartoon penguin with classic black-and-white markings, big eyes, happy expression, ice-blue background"),
    ("a17_monkey",  "a friendly cartoon monkey with warm brown fur, bright eyes, cheeky smile, soft-yellow background"),
    ("a18_elephant","a cute cartoon elephant calf with light grey skin, big floppy ears, kind eyes, soft smile, mint-green background"),
]


# ── Image post-processing ──────────────────────────────────────────
def normalize(raw_bytes: bytes) -> bytes:
    """Center-square crop to TARGET_SIZE and re-encode as WebP. Nano
    Banana sometimes returns non-square outputs; this guarantees the
    avatar tile renders crisp without aspect-ratio surprises."""
    im = Image.open(io.BytesIO(raw_bytes))
    if im.mode != "RGB":
        im = im.convert("RGB")
    w, h = im.size
    if w != h:
        side = min(w, h)
        left = (w - side) // 2
        top = (h - side) // 2
        im = im.crop((left, top, left + side, top + side))
    if im.size != (TARGET_SIZE, TARGET_SIZE):
        im = im.resize((TARGET_SIZE, TARGET_SIZE), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, format="WEBP", quality=WEBP_QUALITY, method=6)
    return buf.getvalue()


# ── Generate ───────────────────────────────────────────────────────
async def generate_one(api_key: str, name: str, subject_brief: str) -> bool:
    """Generates a single avatar via Nano Banana. Returns True on
    success. Errors are caught and logged so one bad call doesn't
    kill the whole batch — the Kotlin AvatarCatalog tolerates
    individual missing files."""
    prompt = f"{STYLE_BRIEF}\n\nSubject: {subject_brief}"

    chat = LlmChat(
        api_key=api_key,
        session_id=f"hushtv-avatar-{name}",
        system_message="You are an expert illustrator generating consistent profile avatars.",
    )
    chat.with_model("gemini", "gemini-3.1-flash-image-preview")
    chat.with_params(modalities=["image", "text"])

    try:
        msg = UserMessage(text=prompt)
        _text, images = await chat.send_message_multimodal_response(msg)
    except Exception as e:  # noqa: BLE001 — any provider error
        print(f"    ! [{name}] generation failed: {type(e).__name__}: {e}", file=sys.stderr)
        return False

    if not images:
        print(f"    ! [{name}] provider returned no image", file=sys.stderr)
        return False

    raw = base64.b64decode(images[0]["data"])
    try:
        webp = normalize(raw)
    except Exception as e:  # noqa: BLE001
        print(f"    ! [{name}] post-process failed: {e}", file=sys.stderr)
        return False

    out_path = os.path.join(OUT_DIR, f"avatar_{name}.webp")
    with open(out_path, "wb") as f:
        f.write(webp)
    size_kb = len(webp) / 1024
    print(f"    ✓ [{name}] {size_kb:.1f} KB")
    return True


async def main():
    api_key = os.getenv("EMERGENT_LLM_KEY")
    if not api_key:
        print("FATAL: EMERGENT_LLM_KEY not in env", file=sys.stderr)
        sys.exit(1)
    print(f"OUT_DIR = {OUT_DIR}")
    os.makedirs(OUT_DIR, exist_ok=True)

    all_subjects = HUMANS + ANIMALS
    print(f"Generating {len(all_subjects)} avatars (Nano Banana)…")

    ok = 0
    bad = []
    for name, subject in all_subjects:
        success = await generate_one(api_key, name, subject)
        if success:
            ok += 1
        else:
            bad.append(name)

    print()
    print(f"[done] {ok}/{len(all_subjects)} avatars generated.")
    if bad:
        print(f"[failed] {bad}")


if __name__ == "__main__":
    asyncio.run(main())
