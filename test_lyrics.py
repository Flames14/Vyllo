# -*- coding: utf-8 -*-
import urllib.request
import urllib.parse
import json
import re
import time

def fetch_lrclib(query):
    time.sleep(1.0)
    url = f"https://lrclib.net/api/search?q={urllib.parse.quote(query)}"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'VylloMusicTest/2.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        return []

def clean_title_and_artist(raw_title):
    title = raw_title.strip()
    suffixes = [
        r"\(Official(?: Music)? Video\)", r"\[Official(?: Music)? Video\]",
        r"\(Official Audio\)", r"\[Official Audio\]",
        r"\(Lyric(?:s)? Video\)", r"\[Lyric(?:s)?\]",
        r"\(Audio\)", r"\[Audio\]", r"\|.*$"
    ]
    for s in suffixes:
        title = re.sub(s, "", title, flags=re.IGNORECASE).strip()
    artist = ""
    m = re.match(r"^(.+?)\s*[-]\s*(.+)$", title)
    if m:
        right_side = m.group(2).strip()
        left_side = m.group(1).strip()
        if len(left_side) <= len(right_side) + 5:
            artist = left_side
            title = right_side
        else:
            title = left_side
            artist = right_side
    return title.strip(), artist.strip()

songs = [
    "Monica O My Darling - Coolie Tamil",
    "Arabic Kuthu - Halamithi Habibo Lyric Video | Thalapathy Vijay | Sun Pictures | Anirudh",
    "Shape of You - Ed Sheeran (Official Music Video)",
    "Naatu Naatu Full Video Song (Telugu) [4K] | RRR Songs | NTR,Ram Charan | MM Keeravaani | SS Rajamouli",
    "Katchi Sera - Sai Abhyankkar"
]

with open("test_results.txt", "w", encoding="utf-8") as f:
    def log(msg):
        f.write(msg + "\n")
        f.flush()

    for raw_title in songs:
        log(f"\n--- Testing: {raw_title} ---")
        clean_t, clean_a = clean_title_and_artist(raw_title)
        log(f"Clean: '{clean_t}', '{clean_a}'")
        
        first_word = clean_t.split(" ")[0]
        segments = [s.strip() for s in re.split(r"[-|:,]", raw_title) if len(s.strip()) > 2]
        langs = ["Tamil", "Malayalam", "Telugu", "Hindi"]
        
        found = False
        r = fetch_lrclib(clean_t)
        if r:
            log(f"FOUND with Clean Title: {r[0].get('trackName')}")
            found = True
            continue
            
        for seg in segments:
            if clean_t.lower() in seg.lower(): continue
            q = f"{first_word} {seg}"
            r = fetch_lrclib(q)
            if r:
                log(f"FOUND with FirstWord+Segment '{q}': {r[0].get('trackName')}")
                found = True
                break
                
        if found: continue

        for lang in langs:
            q = f"{first_word} {lang}"
            r = fetch_lrclib(q)
            if r:
                log(f"FOUND with FirstWord+Lang '{q}': {r[0].get('trackName')}")
                found = True
                break
                
        if not found:
            log("FAILED to find lyrics")

