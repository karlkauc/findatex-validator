package com.findatex.validator.ui.notification;

import java.nio.file.Path;
import java.time.Duration;

public record ToastInfo(
        Path target,
        boolean isFolder,
        long bytes,
        Duration elapsed,
        int reportCount,
        String title) {

    public static ToastInfo singleFile(Path file, long bytes, Duration elapsed) {
        return new ToastInfo(file, false, bytes, elapsed, 1, file.getFileName().toString());
    }

    public static ToastInfo batchFolder(Path folder, long bytes, Duration elapsed, int reportCount) {
        String title = folder.getFileName() + " (" + reportCount + " reports)";
        return new ToastInfo(folder, true, bytes, elapsed, reportCount, title);
    }
}
