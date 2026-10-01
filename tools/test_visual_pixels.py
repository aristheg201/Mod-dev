import unittest
from verify_visual_pixels import validate_samples

class PackPixelTests(unittest.TestCase):
 def samples(self,central):
  return [((40+(x+y)%100,50+x%120,60+y%140) if (528<=x<752 and 200<=y<560)==central else (6,16,30)) for y in range(0,720,8) for x in range(0,1280,8)]
 def test_visible_pack_with_dark_surroundings_is_accepted(self):
  validate_samples('08-pack-enter.png',1280,720,self.samples(True))
  validate_samples('09-pack-opening.png',1280,720,self.samples(True))
 def test_black_frame_is_rejected(self):
  with self.assertRaises(AssertionError):validate_samples('08-pack-enter.png',1280,720,[(6,16,30)]*(160*90))
 def test_bright_hud_cannot_hide_an_empty_pack_area(self):
  rgb=[((50+x%150,70+y%120,80) if x<350 or x>930 else (6,16,30)) for y in range(0,720,8) for x in range(0,1280,8)]
  with self.assertRaises(AssertionError):validate_samples('09-pack-opening.png',1280,720,rgb)
 def test_non_pack_frames_keep_the_existing_brightness_gate(self):
  with self.assertRaises(AssertionError):validate_samples('07-packs.png',1280,720,self.samples(True))

if __name__=='__main__':unittest.main()
