from pathlib import Path
from PIL import Image,ImageDraw
P=Path(__file__).resolve().parents[1];canvas=Image.new('RGB',(1000,660),(24,31,40));draw=ImageDraw.Draw(canvas)
for i,name in enumerate(['kh38mt','kh29td','kab500kr','x555']):
 image=Image.open(P/f'model_review/{name}.png').convert('RGBA');thumb=image.copy();thumb.thumbnail((480,288));x=(i%2)*500;y=(i//2)*330;canvas.paste(thumb,(x+(500-thumb.width)//2,y+22),thumb);draw.text((x+18,y+12),name.upper(),fill=(233,237,244));icon=Image.new('RGBA',(16,16));image=image.crop(image.getbbox());image.thumbnail((16,16),Image.Resampling.LANCZOS);icon.alpha_composite(image,((16-image.width)//2,(16-image.height)//2));out=P/f'src/main/resources/assets/sbw_loadout/textures/overlay/weapon/icons/{name}.png';out.parent.mkdir(parents=True,exist_ok=True);icon.save(out)
canvas.save(P/'model_review/four_models.png')
