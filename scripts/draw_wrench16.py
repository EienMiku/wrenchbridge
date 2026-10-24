"""Render the hand-placed 16px source grid without resampling or image generation."""
from pathlib import Path
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/handdrawn16'
SOURCE = ART / 'universal_wrench.sprite.json'
TARGET = ROOT / 'common/src/main/resources/assets/wrenchbridge/textures/item/universal_wrench.png'

spec = json.loads(SOURCE.read_text(encoding='utf-8'))
assert spec['size'] == [16, 16]
assert len(spec['pixels']) == 16 and all(len(row) == 16 for row in spec['pixels'])
palette = {key: tuple(bytes.fromhex(color.removeprefix('#'))) for key, color in spec['palette'].items()}
sprite = Image.new('RGBA', (16, 16))
sprite.putdata([palette[pixel] for row in spec['pixels'] for pixel in row])
assert set(sprite.getchannel('A').getdata()) == {0, 255}
TARGET.parent.mkdir(parents=True, exist_ok=True)
sprite.save(TARGET, optimize=True)
sprite.save(ART / 'universal-wrench-16.png', optimize=True)
sprite.resize((320, 320), Image.Resampling.NEAREST).save(ART / 'preview.png', optimize=True)

# Show the exact source at common inventory UI scales, using the same nearest-neighbor sampling as pixel art.
preview = Image.new('RGB', (288, 112), '#22262c')
for x, scale in ((24, 1), (80, 2), (160, 4)):
    size = 16 * scale
    tile = sprite.resize((size, size), Image.Resampling.NEAREST)
    preview.paste(tile, (x, (112-size)//2), tile)
preview.save(ART / 'inventory-scales.png', optimize=True)
print(f'Rendered original 16x16 grid, {len(palette)-1} opaque palette colors: {TARGET}')
