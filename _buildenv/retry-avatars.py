#!/usr/bin/env python3
"""Retry only the 4 avatars that failed when the universal-LLM key hit
its budget cap during the first batch. Same style brief as
generate-avatars.py so the visuals stay consistent.

Safe to re-run — overwrites any existing file with the same name."""
import asyncio
import os
import sys

# Reuse the parent module's plumbing
sys.path.insert(0, "/app/_buildenv")
from importlib import import_module
gen = import_module("generate-avatars")

RETRIES = [
    ("h15_omar",  "a young middle-eastern man with short dark hair and a trim beard, olive skin, dark eyes, calm smile, ocean-blue background"),
    ("h16_chloe", "a teenage girl with curly blond hair, fair skin, blue eyes, big smile, soft-pink background"),
    ("h17_kai",   "a pacific-islander young man with short dark hair, warm tan skin, dark eyes, easy smile, turquoise background"),
    ("h18_lila",  "a young woman with shoulder-length curly black hair, warm brown skin, dark eyes, bright smile, coral background"),
]


async def main():
    api_key = os.getenv("EMERGENT_LLM_KEY")
    print(f"Retrying {len(RETRIES)} previously-failed avatars…")
    ok = 0
    for name, subject in RETRIES:
        success = await gen.generate_one(api_key, name, subject)
        if success:
            ok += 1
    print()
    print(f"[done] {ok}/{len(RETRIES)} recovered.")


if __name__ == "__main__":
    from dotenv import load_dotenv
    load_dotenv("/app/backend/.env")
    asyncio.run(main())
