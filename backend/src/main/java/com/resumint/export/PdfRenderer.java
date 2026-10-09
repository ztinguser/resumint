package com.resumint.export;

import java.nio.file.Path;

public interface PdfRenderer {
    void render(ExportSnapshot snapshot, Path output) throws Exception;
}
