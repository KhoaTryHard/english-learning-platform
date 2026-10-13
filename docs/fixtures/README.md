# FIXTURE-01 evidence publication

Parser-tested fixtures are versioned here. Existing shared W3 templates were preserved.

Published to the existing W3 Drive folder on 14/10/2026 and verified by folder readback, preserving Office formats:

- [Vocabulary valid — two rows](https://docs.google.com/spreadsheets/d/1jYLPMTc5KMXyRCKhzLg4DZCeynVgjsWN/edit)
- [Vocabulary invalid — duplicate and missing meaning](https://docs.google.com/spreadsheets/d/1LAKXTenajQg6cTALvX4TyxVWFogbiSSo/edit)
- [Word missing Title style](https://docs.google.com/document/d/1S-p-qh1VeLSN6Fukngh6CTTDvxk8lPIE/edit)
- [Expected results](https://drive.google.com/file/d/1pAiNRIhylkxwU7L2azuDwNP-7YjL_FB3/view)

`lesson-leading-empty.docx` verifies that the first **non-empty** paragraph, not the absolute first paragraph, must use Title style. XLSX valid/invalid cases and Word cases passed in ContentIntegrationTest; ZIP traversal/entity/expanded-size boundaries passed in ImportBoundaryTest. See docs/verification.md for exact CI evidence. Huy's contract review remains pending; publication does not fabricate acceptance.
