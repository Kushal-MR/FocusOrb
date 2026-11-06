import os
import sys
from pathlib import Path
import shutil
import glob
from PIL import Image

def generate_icons(source_image, res_dir):
    # Sizes for standard Android icons
    sizes = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192
    }
    
    # 1. Delete all existing ic_launcher* files
    for root, dirs, files in os.walk(res_dir):
        for file in files:
            if file.startswith("ic_launcher"):
                os.remove(os.path.join(root, file))
    
    # Also delete mipmap-anydpi as it's specifically for adaptive icons which we are replacing with flat pngs
    anydpi_dir = os.path.join(res_dir, 'mipmap-anydpi')
    if os.path.exists(anydpi_dir):
        shutil.rmtree(anydpi_dir)
        
    anydpi_v26_dir = os.path.join(res_dir, 'mipmap-anydpi-v26')
    if os.path.exists(anydpi_v26_dir):
        shutil.rmtree(anydpi_v26_dir)
        
    # 2. Open image and resize for each bucket
    img = Image.open(source_image)
    
    # Make sure we're in RGBA mode
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
        
    for density, size in sizes.items():
        mipmap_dir = os.path.join(res_dir, f'mipmap-{density}')
        os.makedirs(mipmap_dir, exist_ok=True)
        
        # High quality resize
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save as ic_launcher and ic_launcher_round
        resized.save(os.path.join(mipmap_dir, 'ic_launcher.png'))
        resized.save(os.path.join(mipmap_dir, 'ic_launcher_round.png'))
        
        print(f"Generated {size}x{size} for {density} in {res_dir}")

# Paths are resolved relative to this script, so it works wherever the repo lives.
# Usage: python apply_icons.py [path/to/source.png]   (default: art/icon_source.png)
ROOT = Path(__file__).resolve().parent
source_img = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "art" / "icon_source.png"
app_res = ROOT / "app" / "src" / "main" / "res"
phone_res = ROOT / "focusorb" / "src" / "main" / "res"

if not source_img.is_file():
    sys.exit(f"Source image not found: {source_img}")

print("Generating for watch app...")
generate_icons(source_img, app_res)

print("Generating for phone app...")
generate_icons(source_img, phone_res)

print("Done!")
