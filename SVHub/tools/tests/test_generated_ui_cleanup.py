import importlib.util
from pathlib import Path
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


GEN = module("generate-ui-textures")


class GeneratedUiCleanupTests(unittest.TestCase):
    def test_generation_removes_stale_recipe_output(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            stale = output / GEN.PREFIX / "retired_texture.png"
            stale.parent.mkdir(parents=True)
            stale.write_bytes(b"stale")

            self.assertEqual(0, GEN.main(["--output", str(output)]))

            self.assertFalse(stale.exists())
            self.assertEqual(
                set(GEN.generated()),
                {path.name for path in (output / GEN.PREFIX).glob("*.png")},
            )
            self.assertEqual(0, GEN.main(["--check", "--output", str(output)]))


if __name__ == "__main__":
    unittest.main()
