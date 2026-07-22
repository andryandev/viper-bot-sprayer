import sys
from PIL import Image

try:
    img_path = 'viper.png'
    out_path = 'app/src/main/res/drawable/ic_viper_v2.png'
    
    # Buka gambar asli
    img = Image.open(img_path).convert("RGBA")
    
    # Hitung rasio untuk memastikan tidak melebihi 341x341 (66% dari 512)
    max_size = 341
    img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
    
    # Buat kanvas kosong transparan berukuran 512x512
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    
    # Hitung posisi agar gambar baru berada pas di tengah
    offset_x = (512 - img.width) // 2
    offset_y = (512 - img.height) // 2
    
    # Tempel gambar yang sudah dikecilkan ke kanvas
    canvas.paste(img, (offset_x, offset_y), img)
    
    # Simpan ke file baru (ic_viper_v2) untuk menghindari 'Permission Denied' karena file lama dilock
    canvas.save(out_path)
    
    print("Sukses: Gambar berhasil dimampatkan ke tengah!")
except Exception as e:
    print(f"Error: {e}")
