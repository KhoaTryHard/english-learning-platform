"""Append dated local evidence without rewriting historical Word content/images."""
from pathlib import Path
from xml.sax.saxutils import escape
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
workspace = root.parents[1]
backup_dir = workspace / ".cache/w5-weekly-document-backups"
backup_dir.mkdir(parents=True, exist_ok=True)
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"


def plain(text):
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1 (\2)", text)
    return text.replace("**", "").replace("`", "")


def paragraph(text, bold=False):
    formatting = "<w:rPr><w:b/></w:rPr>" if bold else ""
    return '<w:p><w:r>' + formatting + '<w:t xml:space="preserve">' + escape(plain(text)) + '</w:t></w:r></w:p>'


def markdown(content):
    lines = content.splitlines()
    output = []
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if line.startswith("|"):
            rows = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                cells = [cell.strip() for cell in lines[index].strip().strip("|").split("|")]
                if not all(re.fullmatch(r"[:\- ]+", cell) for cell in cells):
                    rows.append(cells)
                index += 1
            borders = ''.join('<w:' + side + ' w:val="single" w:sz="4" w:color="BBBBBB"/>' for side in ("top", "left", "bottom", "right", "insideH", "insideV"))
            output.append('<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblBorders>' + borders + '</w:tblBorders></w:tblPr>')
            columns = max(len(row) for row in rows)
            output.append('<w:tblGrid>' + ''.join('<w:gridCol w:w="' + str(9360 // columns) + '"/>' for _ in range(columns)) + '</w:tblGrid>')
            for number, row in enumerate(rows):
                output.append('<w:tr>' + ''.join('<w:tc>' + paragraph(cell, number == 0) + '</w:tc>' for cell in row) + '</w:tr>')
            output.append('</w:tbl>')
            continue
        if line:
            output.append(paragraph(line.lstrip("# ") if line.startswith("#") else line, line.startswith("#")))
        index += 1
    return ''.join(output)


def image_paragraph(relation, doc_id):
    width, height = 5486400, 4903470
    return f'''<w:p><w:r><w:drawing><wp:inline xmlns:wp="{WP}">
<wp:extent cx="{width}" cy="{height}"/><wp:docPr id="{doc_id}" name="Huy Content ERD review 2026-10-31"/>
<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="Content physical ERD"/><pic:cNvPicPr/></pic:nvPicPr>
<pic:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="{relation}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{width}" cy="{height}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'''


def append(prefix, marker, content, include_image=False):
    path = next(workspace.joinpath("Drive").rglob(prefix + "*.docx"))
    with zipfile.ZipFile(path) as archive:
        info = archive.infolist()
        original = {item.filename: archive.read(item.filename) for item in info}
    raw = original["word/document.xml"].decode("utf-8")
    if marker in raw:
        return {"file": str(path.relative_to(workspace)), "status": "already appended"}
    before_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    backup = backup_dir / (prefix + "-" + before_hash[:12] + ".docx")
    if not backup.exists():
        shutil.copyfile(path, backup)
    added = '<w:p><w:r><w:br w:type="page"/></w:r></w:p>' + paragraph(marker, True) + markdown(content)
    replacements = {}
    allowed_changed = {"word/document.xml"}
    if include_image:
        relation = "rIdHuyContentReview20261031"
        image_name = "huy-content-review-20261031.png"
        relationship_path = "word/_rels/document.xml.rels"
        relationships = original[relationship_path].decode("utf-8")
        if relation in relationships:
            raise RuntimeError("Image relation already exists without appendix marker")
        entry = f'<Relationship Id="{relation}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{image_name}"/>'
        replacements[relationship_path] = relationships.replace('</Relationships>', entry + '</Relationships>').encode("utf-8")
        content_types = original["[Content_Types].xml"].decode("utf-8")
        if 'Extension="png"' not in content_types:
            replacements["[Content_Types].xml"] = content_types.replace('</Types>', '<Default Extension="png" ContentType="image/png"/></Types>').encode("utf-8")
        doc_ids = [int(e.get("id", "0")) for e in ET.fromstring(raw).iter('{' + WP + '}docPr')]
        added += image_paragraph(relation, max(doc_ids, default=0) + 1)
        replacements["word/media/" + image_name] = (root / "docs/architecture/diagrams/content-physical-erd.png").read_bytes()
        allowed_changed |= {relationship_path, "[Content_Types].xml"}
    position = raw.rfind('<w:sectPr')
    if position < 0:
        position = raw.rfind('</w:body>')
    if position < 0:
        raise RuntimeError("Word body/section not found")
    replacements["word/document.xml"] = (raw[:position] + added + raw[position:]).encode("utf-8")
    for name, data in replacements.items():
        if name.endswith('.xml') or name.endswith('.rels'):
            ET.fromstring(data)
    temp = path.with_suffix('.huy-update.tmp')
    with zipfile.ZipFile(temp, 'w') as archive:
        for item in info:
            archive.writestr(item, replacements.get(item.filename, original[item.filename]))
        for name, data in replacements.items():
            if name not in original:
                archive.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(temp) as archive:
        assert archive.testzip() is None
        for name, data in original.items():
            if name not in allowed_changed:
                assert archive.read(name) == data, name
        def paragraphs(data):
            return [''.join(t.text or '' for t in p.iter('{' + W + '}t')) for p in ET.fromstring(data).iter('{' + W + '}p')]
        old_text = paragraphs(original["word/document.xml"])
        new_text = paragraphs(archive.read("word/document.xml"))
        assert new_text[:len(old_text)] == old_text
    destination = path
    replaced = True
    try:
        temp.replace(path)
    except PermissionError:
        # Keep the original untouched when Windows denies atomic replacement.
        destination = workspace / "Drive/Cap nhat Huy 2026-10-31" / ("HUY - " + path.name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(temp.read_bytes())
        if temp.resolve().is_relative_to(workspace.resolve()):
            temp.unlink()
        replaced = False
    return {"file": str(path.relative_to(workspace)), "outputFile": str(destination.relative_to(workspace)),
            "originalReplaced": replaced, "beforeSha256": before_hash,
            "afterSha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
            "backup": str(backup.relative_to(workspace)), "originalTextAndMediaPreserved": True,
            "imageAdded": include_image,
            "status": "local appendix added; online Drive not changed" if replaced else "updated copy prepared; original replacement denied by Windows; online Drive not changed"}


records = [
    append("W5-04", "E-HUY-20261031-W5-04", (root / "docs/planning/w5-04-huy-evidence-update-2026-10-31.md").read_text(encoding="utf-8")),
    append("W5-05", "E-HUY-20261031-W5-05", (root / "docs/planning/w5-05-huy-handoff-update-2026-10-31.md").read_text(encoding="utf-8")),
    append("W3-03", "E-HUY-20261031-CONTENT-ERD", """# ERD Content bổ sung để review #20

Đối chiếu migration Content tại bf183461; có LearningContent và cardinality parent 1 → child 0..*.
Hình này bổ sung bản physical để Khoa/Huy review, không xóa hình lịch sử hoặc tự đổi approved baseline.
Source Mermaid/PNG: docs/architecture/diagrams/content-physical-erd.* trong PR #43.
Review mới cũng ghi rõ khác biệt RevokedToken, Question/Option/Answer JSON và CourseProgress count projection.
Xem docs/reviews/2026-10-31-huy-schema-auth-review.md; các mapping khác biệt chưa được nhóm chấp thuận tự động.
""", include_image=True),
]
report = root / "docs/evidence/w5-weekly-local-document-update-2026-10-31.json"
report.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(records, ensure_ascii=False, indent=2))
