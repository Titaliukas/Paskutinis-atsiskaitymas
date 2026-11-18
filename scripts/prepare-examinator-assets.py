#!/usr/bin/env python3
"""Prepare the four approved exports. Requires Pillow only when rerunning this offline tool.
Usage: python3 scripts/prepare-examinator-assets.py /path/to/approved/imagegen
Explicit measured head bounds exclude stray low-alpha export noise and neighboring frames.
Attack beams overlap faces: preserve internal discharge pixels; strip only exterior beam pixels.
"""
import sys
from pathlib import Path
from shutil import copyfile
from PIL import Image

source = Path(sys.argv[1])
assets = Path(__file__).resolve().parents[1] / 'game-client/src/main/resources/assets'
assets.mkdir(parents=True, exist_ok=True)
exports = {
    'EXAMINATOR_IDLE_SOURCE': 'examinator-cyborg-idle-v7.png',
    'EXAMINATOR_MOVEMENT_SOURCE': 'examinator-poses/examinator-movement-scan.png',
    'EXAMINATOR_ATTACKS_SOURCE': 'examinator-poses/examinator-attacks-8-directions.png',
    'EXAMINATOR_LASER_ORIGINAL': 'examinator-poses/examinator-laser.png',
}
for key, name in exports.items():
    copyfile(source / name, assets / (key + '.png'))

def head(key, sheet, bounds, isolate=False):
    frame = Image.open(source / sheet).convert('RGBA').crop(bounds)
    if isolate:
        # Only trim outside each row's opaque, non-discharge head silhouette.
        # Internal red/white pixels remain untouched (including beams crossing the face).
        pixels = frame.load()
        for y in range(frame.height):
            body = [x for x in range(frame.width) if pixels[x, y][3] >= 160
                    and not (pixels[x, y][0] > 140 and pixels[x, y][0] > pixels[x, y][1] * 1.4
                             and pixels[x, y][0] > pixels[x, y][2] * 1.4)
                    and min(pixels[x, y][:3]) < 220]
            left, right = (min(body), max(body)) if body else (frame.width, -1)
            for x in range(frame.width):
                if x < left or x > right:
                    pixels[x, y] = (0, 0, 0, 0)
    scale = 112 / frame.height
    frame = frame.resize((round(frame.width * scale), 112), Image.Resampling.NEAREST)
    canvas = Image.new('RGBA', (128, 128))
    canvas.alpha_composite(frame, ((128 - frame.width) // 2, 8))
    canvas.save(assets / (key + '.png'))

head('EXAMINATOR_IDLE', exports['EXAMINATOR_IDLE_SOURCE'], (275, 123, 985, 1123))
movement = exports['EXAMINATOR_MOVEMENT_SOURCE']
for key, bounds in [
    ('ARRIVE', (149, 19, 534, 541)), ('HOVER', (758, 20, 1138, 541)),
    ('LEFT', (148, 626, 534, 1161)), ('RIGHT', (757, 626, 1155, 1161)),
]:
    head('EXAMINATOR_' + key, movement, bounds)
# Measured per-head bounds (not equal atlas cells). Preserve discharges and facial pixels.
attack_bounds = [(52, 24, 377, 408), (517, 18, 833, 404), (970, 14, 1249, 403),
                 (1390, 24, 1715, 404), (73, 461, 417, 851), (517, 461, 830, 849),
                 (964, 462, 1250, 854), (1413, 463, 1696, 854)]
for direction, bounds in zip(['LEFT', 'UP_LEFT', 'UP', 'UP_RIGHT', 'RIGHT', 'DOWN_RIGHT', 'DOWN', 'DOWN_LEFT'], attack_bounds):
    head('EXAMINATOR_ATTACK_' + direction, exports['EXAMINATOR_ATTACKS_SOURCE'], bounds, isolate=True)
# Actual opaque beam is y=299..437; source center=(243,368), tip=(2016,368).
# Keep alpha in the measured crop, fixed 10-unit flare height at runtime.
laser = Image.open(source / exports['EXAMINATOR_LASER_ORIGINAL']).convert('RGBA').crop((156, 299, 2016, 438))
laser.save(assets / 'EXAMINATOR_LASER.png')
# Cache the three strips as resources; no cropping in paint, only middle stretches.
for key, bounds in [('SOURCE', (0, 0, 139, 139)), ('MIDDLE', (139, 0, 1719, 139)), ('TIP', (1719, 0, 1860, 139))]:
    laser.crop(bounds).save(assets / ('EXAMINATOR_LASER_' + key + '.png'))
