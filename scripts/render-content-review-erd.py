"""Render the source-reviewed Content relationships for the local Word appendix.

Optional documentation tooling: requires Pillow. No application/runtime dependency.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
output = root / "docs/architecture/diagrams/content-physical-erd.png"
image = Image.new("RGB", (1600, 1430), "white")
draw = ImageDraw.Draw(image)
font_dir = Path("C:/Windows/Fonts")
font = ImageFont.truetype(str(font_dir / "arial.ttf"), 23)
small = ImageFont.truetype(str(font_dir / "arial.ttf"), 20)
heading = ImageFont.truetype(str(font_dir / "arialbd.ttf"), 27)
title = ImageFont.truetype(str(font_dir / "arialbd.ttf"), 35)
draw.text((50, 30), "Content physical ERD - review artifact #20", fill="#17283d", font=title)
draw.text((50, 82), "Source: Khoa bf183461 | parent 1 -> children 0..* | no cross-service FK", fill="#46566a", font=font)

boxes = {
    "COURSE": (590, 145, ["PK course_id", "published"]),
    "LEVEL": (60, 375, ["PK level_id", "FK course_id"]),
    "COURSE_MODULE": (590, 375, ["PK module_id; FK course_id", "order_no; published"]),
    "LESSON": (590, 605, ["PK lesson_id; FK module_id", "order_no; published"]),
    "VOCABULARY": (60, 855, ["PK vocabulary_id; FK lesson_id", "word_normalized", "meaning_normalized"]),
    "LEARNING_CONTENT": (1120, 855, ["PK content_id; FK lesson_id", "type: GRAMMAR/LISTENING/READING", "text / resource_url; published"]),
    "USER_VOCABULARY": (60, 1135, ["PK user_vocabulary_id", "FK vocabulary_id", "user_id: external ref only"]),
}
width, height = 420, 160
edges = [
    ([(590, 225), (270, 225), (270, 375)], (280, 240)),
    ([(800, 305), (800, 375)], (825, 330)),
    ([(800, 535), (800, 605)], (825, 560)),
    ([(680, 765), (680, 805), (270, 805), (270, 855)], (340, 775)),
    ([(920, 765), (920, 805), (1330, 805), (1330, 855)], (1080, 775)),
    ([(270, 1015), (270, 1135)], (295, 1065)),
]
for points, label in edges:
    draw.line(points, fill="#526e94", width=4)
    x, y = points[-1]
    draw.polygon([(x, y), (x - 9, y - 16), (x + 9, y - 16)], fill="#526e94")
    draw.text(label, "1 -> 0..*", fill="#294f80", font=font)

for name, (x, y, fields) in boxes.items():
    draw.rounded_rectangle((x, y, x + width, y + height), radius=10, fill="#f6f9fe", outline="#526e94", width=3)
    draw.text((x + 15, y + 12), name, fill="#17283d", font=heading)
    draw.line((x, y + 52, x + width, y + 52), fill="#526e94", width=2)
    for index, field in enumerate(fields):
        draw.text((x + 14, y + 63 + index * 28), field, fill="#253a53", font=small)

draw.text((520, 1200), "Vocabulary UNIQUE: lesson + word_normalized + meaning_normalized", fill="#253a53", font=font)
draw.text((520, 1242), "Saved UNIQUE: user_id + vocabulary_id", fill="#253a53", font=font)
draw.text((50, 1350), "Review pending. Full columns/checks/indexes remain in the SQL migration.", fill="#46566a", font=font)
output.parent.mkdir(parents=True, exist_ok=True)
image.save(output)
print(output)
