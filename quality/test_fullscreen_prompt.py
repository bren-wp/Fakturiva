"""Verify the CI never dismisses unrelated system or Fakturiva dialogs."""
import importlib.util
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

source=Path(__file__).resolve().parents[1]/"ci"/"acknowledge_immersive_prompt.py"
spec=importlib.util.spec_from_file_location("immersive_prompt",source)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def ui(title_package="com.android.systemui",title="Viewing full screen",
       button_package="com.android.systemui",button="Got it"):
    root=ET.Element("hierarchy")
    ET.SubElement(root,"node",{"package":title_package,"text":title,
        "resource-id":"com.android.systemui:id/immersive_cling_title"})
    ET.SubElement(root,"node",{"package":button_package,"text":button,
        "resource-id":"android:id/ok","bounds":"[600,300][780,450]"})
    return root


class FullscreenPromptTest(unittest.TestCase):
    def test_android16_systemui_prompt(self):
        self.assertEqual((690,375),module.acknowledgment(ui()))
    def test_unrelated_system_dialog_is_never_dismissed(self):
        self.assertIsNone(module.acknowledgment(ui(title="Permission required")))
    def test_app_owned_button_is_never_tapped(self):
        self.assertIsNone(module.acknowledgment(ui(button_package="com.brendigo.fakturiva")))
    def test_app_owned_title_is_not_a_system_prompt(self):
        self.assertIsNone(module.acknowledgment(ui(title_package="com.brendigo.fakturiva")))
    def test_bad_coordinates_cannot_be_tapped(self):
        root=ui()
        list(root)[1].set("bounds","[-5,400][100,500]")
        self.assertIsNone(module.acknowledgment(root))


if __name__=="__main__":
    unittest.main()
