package com.findatex.validator.docs;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny string-builder for Markdown output. Centralises pipe/escape handling so the per-template
 * generator stays readable. Not a general-purpose Markdown library — it knows the subset of
 * syntax the generator emits (headings, paragraphs, GFM tables, blockquotes, fenced code).
 */
final class MarkdownEmitter {

    private final StringBuilder buf = new StringBuilder(8192);

    MarkdownEmitter heading1(String text)  { buf.append("# ").append(text).append("\n\n"); return this; }
    MarkdownEmitter heading2(String text)  { buf.append("## ").append(text).append("\n\n"); return this; }
    MarkdownEmitter heading3(String text)  { buf.append("### ").append(text).append("\n\n"); return this; }
    MarkdownEmitter heading4(String text)  { buf.append("#### ").append(text).append("\n\n"); return this; }

    MarkdownEmitter paragraph(String text) { buf.append(text).append("\n\n"); return this; }

    MarkdownEmitter line(String text)      { buf.append(text).append("\n"); return this; }

    MarkdownEmitter blank()                { buf.append("\n"); return this; }

    MarkdownEmitter rule()                 { buf.append("---\n\n"); return this; }

    MarkdownEmitter blockquote(String text) {
        for (String l : text.split("\n", -1)) {
            buf.append("> ").append(l).append("\n");
        }
        buf.append("\n");
        return this;
    }

    MarkdownEmitter bullet(String text)    { buf.append("- ").append(text).append("\n"); return this; }

    /** Emits a GFM table. Each row must have the same column count as the header. */
    MarkdownEmitter table(List<String> header, List<List<String>> rows) {
        buf.append('|');
        for (String h : header) buf.append(' ').append(escapeCell(h)).append(" |");
        buf.append('\n').append('|');
        for (int i = 0; i < header.size(); i++) buf.append("---|");
        buf.append('\n');
        for (List<String> row : rows) {
            buf.append('|');
            for (int i = 0; i < header.size(); i++) {
                String cell = i < row.size() ? row.get(i) : "";
                buf.append(' ').append(escapeCell(cell)).append(" |");
            }
            buf.append('\n');
        }
        buf.append('\n');
        return this;
    }

    /** Helper to build a single table row from variadic strings. */
    static List<String> row(String... cells) {
        List<String> out = new ArrayList<>(cells.length);
        for (String c : cells) out.add(c == null ? "" : c);
        return out;
    }

    String build() { return buf.toString(); }

    /** Replace pipes and newlines so the cell stays on one Markdown table row. */
    static String escapeCell(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = s.replace("|", "\\|");
        out = out.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        return out;
    }
}
