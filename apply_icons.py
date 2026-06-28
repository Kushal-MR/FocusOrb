import os
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

source_img = r"C:\Users\kusha\.gemini\antigravity\brain\13e4b3fe-a2ba-4ecc-8154-ab8b8daae50a\focusorb_concept_3_improved_b_1782621854987.png"
app_res = r"c:\AndroidProjects\FocusOrb\app\src\main\res"
phone_res = r"c:\AndroidProjects\FocusOrb\focusorb\src\main\res"

print("Generating for watch app...")
generate_icons(source_img, app_res)

print("Generating for phone app...")
generate_icons(source_img, phone_res)

print("Done!")
