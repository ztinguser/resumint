# 第二套模板设计 QA

- Source visual truth: `C:\Users\mofang\Downloads\Template 20.png`
- Implementation screenshot: `C:\Users\mofang\Desktop\agent\project\resumint\frontend\test-results\candidates\centered-print.png`
- Combined comparison: `C:\Users\mofang\Desktop\agent\project\resumint\.test-tmp\design-qa\centered-comparison.png`
- Actual exported PDF render: `C:\Users\mofang\Desktop\agent\project\resumint\tmp\pdfs\centered-template-export-final.png`
- Viewport: A4 page at 210 × 297 mm; browser CSS output 794 × 1123 px at device scale 1
- Source pixels: 1190 × 1684
- Implementation pixels: 794 × 1123; normalized to 1190 × 1684 for the combined comparison
- State: default Chinese example content, print media, first page

**Findings**

- No actionable P0/P1/P2 mismatch remains.
- Typography: the implementation uses the project's available Microsoft YaHei stack for Chinese rather than the Latin face visible in the English reference. Weight, hierarchy, line height, and gray/black contrast follow the reference; the typeface difference is intentional because the delivered instance content is Chinese.
- Spacing and layout rhythm: circular portrait size, top offset, centered identity, body start, column tracks, inter-column gap, and page margins align with the normalized reference.
- Colors and visual tokens: white page, near-black headings, and neutral gray body copy match the source direction; no decorative category color was introduced.
- Image quality and asset fidelity: the candidate reuses the existing content-addressed monochrome example portrait. The portrait subject differs from the reference while its crop, scale, circular mask, and tonal treatment match; this is intentional pending final material confirmation.
- Copy and content: the source's English content was replaced with Chinese instance text while preserving the same information hierarchy and approximate density.

**Focused Region Evidence**

The 1190 × 1684 normalized full-page comparison keeps the portrait, header, both column headings, contact rows, education entries, employment entries, and skill list readable, so an additional crop was not required.

**Comparison History**

1. Initial print comparison found a P1 mismatch: a legacy `business-ledger` print rule forced top padding to zero, while the workbench retained 17 mm.
2. Removed the obsolete dark-header print rules and restored the candidate's 17 mm top padding.
3. Post-fix automated geometry comparison confirms identical page padding, header height, column widths, gap, and name size between workbench and print view.
4. The first real PDF export exposed a blank second page caused by the editable preview's 18 px bottom margin winning the print cascade.
5. Added an explicit zero print margin for the centered template. The final Chromium PDF is one A4 page according to Poppler and its rendered PNG contains all expected content without clipping or overlap.

**Interaction and Runtime Evidence**

- Tested template-library skeleton without instance copy.
- Tested direct editing of Chinese contact content.
- Tested selected-element style inspection against the rendered gray value.
- Tested workbench/print composition parity.
- Generated and rendered the real downloaded PDF; verified one A4 page and no blank trailing page.
- Checked the in-app browser console: no errors or warnings.

**Follow-up Polish**

- P3: revisit the exact Chinese font only if a separately licensed font asset is approved for the final published version.
- P3: replace the temporary display name after product confirmation.

final result: passed
