"""
Generate a Nano Banana mockup of the proposed GameCard / PpvCard redesign
for HushTV's Live Sports section on Android TV.

Output: /app/_mockups/sports_cards_mockup.png
"""
import asyncio
import base64
import os
from dotenv import load_dotenv
from emergentintegrations.llm.chat import LlmChat, UserMessage

load_dotenv("/app/backend/.env")

PROMPT = """
Create a single, high-fidelity, photoreal UI mockup screenshot of an Android TV
"Live Sports" page for an IPTV app called HushTV. 16:9 widescreen, 1920x1080,
dark cinematic UI. Render as if it were a real screen capture from a 4K TV.

== OVERALL PAGE LAYOUT (top to bottom) ==
- Background: deep navy-to-black vertical gradient (#050810 → #0B1220), with a
  large blurred hero image behind everything (looks like a stadium with cyan
  light leaks at 8% opacity).
- Top-left corner: small "HUSHTV" wordmark in cyan (#22D3EE). Top-right: time "9:41 PM".
- Below the wordmark, a HERO BANNER (full-width, 320px tall) showing a live
  hockey game: home team logo on the right, away team logo on the left, large
  bold scores "3 — 2", a pulsing red "LIVE" pill with white text, and league
  badge "NHL" in the corner. The hero has cinematic dark gradient on the bottom
  for text legibility.
- Below the hero, a HORIZONTAL ROW of pill-shaped league filter chips:
  "ALL" (selected, solid cyan #22D3EE fill, dark text), then "LIVE" (red dot),
  "PPV" (red dot), "NHL", "MLB", "NBA", "EPL". Each chip has a small circular
  league logo on the left and uppercase bold label text. Chips are 54px tall,
  rounded-full, with subtle cyan ring borders.

== THE STAR OF THE MOCKUP — A ROW OF 4 GAME CARDS + 1 PPV CARD ==
Show 5 cards in a horizontal row. Cards are 360x220px (Game) or 300x220px (PPV),
18px rounded corners. The SECOND card is FOCUSED — it has a glowing 3px cyan
border and a soft cyan halo glow extending beyond the card (TV focus indicator).
The other cards have a subtle 1px white-10% border.

— GAME CARD (template, repeat 4x with different teams) —
Background: vertical dark gradient (#111A2C → #050810).
Internal layout (16px padding all around):

  TOP ROW (height ~20px):
    Left: a 3px-wide x 14px-tall vertical accent bar (color = league accent,
    e.g. cyan for NHL, red for MLB, orange for NBA), then 8px gap, then the
    league name in tiny uppercase bold letters with wide letter-spacing,
    e.g. "NHL" in cyan, 11px font.
    Right: status indicator. For LIVE games: a 7px red pulsing dot + "LIVE"
    in red bold uppercase. For UPCOMING: gray countdown text "IN 2H 14M".
    For FINAL: gray "FINAL" text.

  CENTER (vertically centered, 60% of card height):
    A row with: [away team badge circle, 56px] — [BIG SCORES "3 — 2"
    centered, 44px font weight black white, em-dash separator gray] —
    [home team badge circle, 56px]. Team badges are real-looking circular
    sports logos (Maple Leafs blue, Rangers blue/red, etc). Scores DOMINATE
    the visual weight — they should be the first thing the eye lands on.

  BOTTOM (a thin 2px horizontal line in cyan-15% running across the card,
  10px from bottom edge — this is a NEW design touch suggesting "live
  pulse"). NO channel chip at bottom (removed in v1.44.13).

  Show 4 GAME CARDS with these examples:
  Card 1 (UPCOMING, gray): NHL — Toronto Maple Leafs vs Montreal Canadiens,
    countdown "IN 2H 14M", no scores, just "VS" gray text in middle.
  Card 2 (FOCUSED, LIVE): NHL — NY Rangers vs Boston Bruins, "LIVE" red
    pulsing pill, scores "3 — 2", glowing cyan border + cyan halo glow.
  Card 3 (LIVE): NBA — Lakers vs Warriors, orange accent, "LIVE", "98 — 102".
  Card 4 (FINAL): MLB — Yankees vs Red Sox, "FINAL", "5 — 4", scores in
    slightly dimmer white.

— PPV CARD (the 5th card, 300x220px) —
Full-bleed poster background (UFC fight poster aesthetic — two boxers facing
off, dramatic red/black lighting, motion blur) with a strong dark vertical
gradient on bottom for legibility.
Top-left: red 3px accent bar + "PPV EVENT" in red uppercase bold tiny text.
Bottom area: title "UFC 312: JONES VS ASPINALL" in white bold uppercase
18px, 2 lines. Below: countdown "IN 4D 6H" in light gray. Below that: a
white pill chip "▶ FIGHT NETWORK 1" (channel chip, white background, dark
text — channel chips ARE kept on PPV cards because they have unique branding).

== STYLE NOTES ==
- Typography: Inter font, very heavy weights (Black 900) for scores/titles,
  generous letter-spacing on caps.
- Colors: cyan #22D3EE accent, red #EF4444 for LIVE / PPV, gray #94A3B8 for
  metadata, white #FFFFFF for hero text.
- Lighting: subtle film grain + 12px cyan glow on the focused card.
- This is a TV interface — text must be legible from 10 feet away. NO clipping,
  NO cut-off scores. Every element fits comfortably inside its card with
  generous breathing room.
- Render this as a single cohesive screenshot, NOT separate cards on a white
  background. Make it look REAL — like a marketing screenshot you'd see on
  the Google Play Store.
"""


async def main():
    api_key = os.getenv("EMERGENT_LLM_KEY")
    assert api_key, "EMERGENT_LLM_KEY missing in /app/backend/.env"

    chat = LlmChat(
        api_key=api_key,
        session_id="hushtv-sports-card-mockup",
        system_message="You are a senior UI/UX designer producing pixel-perfect Android TV mockups.",
    ).with_model("gemini", "gemini-3-pro-image-preview").with_params(modalities=["image", "text"])

    msg = UserMessage(text=PROMPT)
    text, images = await chat.send_message_multimodal_response(msg)
    print("TEXT RESPONSE (truncated):", (text or "")[:300])
    if not images:
        raise SystemExit("No images returned from Nano Banana.")
    out_dir = "/app/_mockups"
    os.makedirs(out_dir, exist_ok=True)
    for i, img in enumerate(images):
        img_bytes = base64.b64decode(img["data"])
        out = f"{out_dir}/sports_cards_mockup_{i}.png"
        with open(out, "wb") as f:
            f.write(img_bytes)
        print(f"Saved {out} ({len(img_bytes)} bytes, mime={img['mime_type']})")


if __name__ == "__main__":
    asyncio.run(main())
