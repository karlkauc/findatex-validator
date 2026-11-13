/* Office.js access for the task pane. Everything Office-specific lives here so
   marks.ts stays a pure, testable function. */
import { ExcelMark, SEVERITY_FILL, chunkAddresses } from './marks';

const SLICE_SIZE = 4 * 1024 * 1024;
/** Cells per Excel.run batch when reading/writing fills. */
const BATCH = 1000;

/**
 * The open workbook as .xlsx bytes (Office.FileType.Compressed), read in
 * slices. The server reads the first worksheet, like any upload.
 */
export function readWorkbookFile(): Promise<File> {
  return new Promise((resolve, reject) => {
    Office.context.document.getFileAsync(
      Office.FileType.Compressed,
      { sliceSize: SLICE_SIZE },
      (res) => {
        if (res.status !== Office.AsyncResultStatus.Succeeded) {
          reject(new Error(res.error?.message ?? 'Could not read the workbook'));
          return;
        }
        const file = res.value;
        const parts: Uint8Array[] = new Array(file.sliceCount);
        let received = 0;
        const close = () => file.closeAsync(() => undefined);
        const fetchSlice = (i: number) => {
          file.getSliceAsync(i, (sr) => {
            if (sr.status !== Office.AsyncResultStatus.Succeeded) {
              close();
              reject(new Error(sr.error?.message ?? 'Could not read the workbook'));
              return;
            }
            parts[i] = new Uint8Array(sr.value.data as number[]);
            received++;
            if (received === file.sliceCount) {
              close();
              resolve(new File(parts as BlobPart[], 'workbook.xlsx', {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
              }));
            } else {
              fetchSlice(i + 1);
            }
          });
        };
        if (file.sliceCount === 0) {
          close();
          reject(new Error('The workbook is empty'));
        } else {
          fetchSlice(0);
        }
      },
    );
  });
}

/** Original fill per address, so the markers can be removed again. null = no fill. */
export type SavedFills = Map<string, string | null>;

function isNoFill(color: string | null | undefined): boolean {
  // Excel reports an unfilled cell as white.
  return !color || color.toUpperCase() === '#FFFFFF';
}

/**
 * Colours every marked cell of the first worksheet by its worst severity and
 * returns the fills it replaced. Fills already saved (a second run) are kept,
 * so "remove markers" always restores the state before the first run.
 */
export async function applyMarkers(marks: ExcelMark[], saved: SavedFills): Promise<SavedFills> {
  const out: SavedFills = new Map(saved);
  await Excel.run(async (ctx) => {
    const sheet = ctx.workbook.worksheets.getFirst();
    for (let i = 0; i < marks.length; i += BATCH) {
      const batch = marks.slice(i, i + BATCH).filter((m) => !out.has(m.address));
      const ranges = batch.map((m) => {
        const r = sheet.getRange(m.address);
        r.load('format/fill/color');
        return r;
      });
      await ctx.sync();
      batch.forEach((m, j) => {
        const c = ranges[j].format.fill.color;
        out.set(m.address, isNoFill(c) ? null : c);
      });
    }
    // Then paint, one RangeAreas per severity and chunk.
    for (const sev of ['INFO', 'WARNING', 'ERROR'] as const) {
      const addrs = marks.filter((m) => m.severity === sev).map((m) => m.address);
      for (const chunk of chunkAddresses(addrs)) {
        sheet.getRanges(chunk).format.fill.color = SEVERITY_FILL[sev];
      }
      await ctx.sync();
    }
  });
  return out;
}

/** Puts back the fills recorded by {@link applyMarkers}. */
export async function clearMarkers(saved: SavedFills): Promise<void> {
  if (saved.size === 0) return;
  await Excel.run(async (ctx) => {
    const sheet = ctx.workbook.worksheets.getFirst();
    const noFill: string[] = [];
    const byColor = new Map<string, string[]>();
    for (const [addr, color] of saved) {
      if (color === null) noFill.push(addr);
      else {
        const list = byColor.get(color);
        if (list) list.push(addr);
        else byColor.set(color, [addr]);
      }
    }
    for (const chunk of chunkAddresses(noFill)) sheet.getRanges(chunk).format.fill.clear();
    for (const [color, addrs] of byColor) {
      for (const chunk of chunkAddresses(addrs)) sheet.getRanges(chunk).format.fill.color = color;
    }
    await ctx.sync();
  });
}

/** Activates the first worksheet and selects one cell. */
export async function selectCell(address: string): Promise<void> {
  await Excel.run(async (ctx) => {
    const sheet = ctx.workbook.worksheets.getFirst();
    sheet.activate();
    sheet.getRange(address).select();
    await ctx.sync();
  });
}

/** Name of the first worksheet — the one the server validates. */
export async function firstSheetName(): Promise<string> {
  return Excel.run(async (ctx) => {
    const sheet = ctx.workbook.worksheets.getFirst();
    sheet.load('name');
    await ctx.sync();
    return sheet.name;
  });
}
