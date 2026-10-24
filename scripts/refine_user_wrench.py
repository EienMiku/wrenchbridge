"""Render a manual 16px refinement and comparison; never overwrite the user's source."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--variant', choices=('refined', 'soft'), default='soft')
variant = parser.parse_args().variant
ART = ROOT / 'art/user-refinement'
ITEM = ROOT / 'common/src/main/resources/assets/wrenchbridge/textures/item'
SOURCE = ITEM / 'universal_wrench_2.png'
OUTPUT = ITEM / f'universal_wrench_2_{variant}.png'

source_bytes = SOURCE.read_bytes()
original = Image.open(SOURCE).convert('RGBA')
assert original.size == (16, 16)
spec = json.loads((ART / f'universal_wrench_2_{variant}.sprite.json').read_text(encoding='utf-8'))
assert spec['size'] == [16, 16]
assert len(spec['pixels']) == 16 and all(len(row) == 16 for row in spec['pixels'])
palette = {key: tuple(bytes.fromhex(color.removeprefix('#'))) for key, color in spec['palette'].items()}
refined = Image.new('RGBA', (16, 16))
refined.putdata([palette[pixel] for row in spec['pixels'] for pixel in row])
assert refined.getchannel('A').tobytes() == original.getchannel('A').tobytes(), 'Silhouette changed'
assert set(refined.getchannel('A').getdata()) == {0, 255}

backup = ART / 'source-original.png'
if backup.exists():
    assert backup.read_bytes() == source_bytes, 'The user updated their source; review it before rendering again'
else:
    shutil.copy2(SOURCE, backup)
(ART / 'source.sha256').write_text(hashlib.sha256(source_bytes).hexdigest() + '\n', encoding='utf-8')
refined.save(OUTPUT, optimize=True)
refined.save(ART / f'universal-wrench-{variant}.png', optimize=True)
refined.resize((320, 320), Image.Resampling.NEAREST).save(ART / f'{variant}-preview.png')

canvas = Image.new('RGB', (720, 484), '#292C32')
draw = ImageDraw.Draw(canvas)
font_path = Path('C:/Windows/Fonts/segoeui.ttf')
font = ImageFont.truetype(str(font_path), 15) if font_path.exists() else ImageFont.load_default()
for left, sprite, label in ((24, original, 'Original'), (376, refined, variant.title())):
    draw.text((left, 8), label, fill='#E2E5E9', font=font)
    for y in range(16):
        for x in range(16):
            color = '#656870' if (x+y) % 2 else '#767981'
            draw.rectangle((left+x*20, 32+y*20, left+x*20+19, 32+y*20+19), fill=color)
    enlarged = sprite.resize((320, 320), Image.Resampling.NEAREST)
    canvas.paste(enlarged, (left, 32), enlarged)
    for offset, scale in ((0, 1), (64, 2), (160, 4)):
        size = 18*scale
        x, y = left+offset, 374
        draw.rectangle((x, y, x+size-1, y+size-1), fill='#8B8B8B', outline='#373737', width=scale)
        tile = sprite.resize((16*scale, 16*scale), Image.Resampling.NEAREST)
        canvas.paste(tile, (x+scale, y+scale), tile)
        draw.text((x, y+size+4), f'{scale}x', fill='#B9BEC6', font=font)
comparison_name = 'before-after-soft.png' if variant == 'soft' else 'before-after.png'
canvas.save(ART / comparison_name, optimize=True)
assert SOURCE.read_bytes() == source_bytes, 'Original file was modified'
colors = len({pixel for pixel in refined.getdata() if pixel[3]})
print(f'Saved {OUTPUT}; {colors} opaque colors; original silhouette and source file preserved.')
