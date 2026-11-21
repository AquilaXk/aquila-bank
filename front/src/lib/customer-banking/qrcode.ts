/*
 * Clean-Room ISO/IEC 18004 Bit-Packed QR Code Encoder (Byte Mode / SVG Path)
 *
 * Authored from the public ISO/IEC 18004 specification using bit-packed Uint32Array
 * module planes, GF(2^8) log/antilog lookup tables, precomputed 32-bit mask words,
 * and register-based penalty evaluation with Fail-Closed input bounds.
 */

export const MAX_QR_INPUT_BYTES = 512;
const MAX_QR_BORDER = 10;

const PENALTY_N1 = 3;
const PENALTY_N2 = 3;
const PENALTY_N3 = 40;
const PENALTY_N4 = 10;

export type QrEccLevel = "LOW" | "MEDIUM" | "QUARTILE" | "HIGH";

export type QrCodeSvgResult = {
  version: number;
  ecc: QrEccLevel;
  moduleCount: number;
  size: number;
  viewBox: string;
  pathData: string;
};

export type QrMatrixResult = {
  version: number;
  ecc: QrEccLevel;
  size: number;
  wordsPerRow: number;
  bits: Uint32Array;
  isDark: (x: number, y: number) => boolean;
};

const UTF8_ENCODER = new TextEncoder();

const ECC_LEVEL_BY_INDEX: readonly QrEccLevel[] = ["LOW", "MEDIUM", "QUARTILE", "HIGH"];
const ECC_FORMAT_BITS = new Uint8Array([1, 0, 3, 2]);

function resolveEccIndex(ecc: QrEccLevel): number {
  const idx = ECC_LEVEL_BY_INDEX.indexOf(ecc);
  if (idx < 0) {
    throw new TypeError("Unsupported QR error correction level.");
  }
  return idx;
}

// ISO/IEC 18004 Table 9: ECC codewords per block [level * 40 + (version - 1)]
const ISO_ECC_CODEWORDS_PER_BLOCK = new Uint8Array([
  // LOW (v1..v40)
  7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28,
  28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
  // MEDIUM (v1..v40)
  10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
  26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
  // QUARTILE (v1..v40)
  13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30,
  28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
  // HIGH (v1..v40)
  17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28,
  30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
]);

// ISO/IEC 18004 Table 9: Total error correction blocks [level * 40 + (version - 1)]
const ISO_NUM_ECC_BLOCKS = new Uint8Array([
  // LOW (v1..v40)
  1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8,
  8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25,
  // MEDIUM (v1..v40)
  1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
  17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
  // QUARTILE (v1..v40)
  1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20,
  23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68,
  // HIGH (v1..v40)
  1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25,
  25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81,
]);

// GF(2^8) with primitive polynomial x^8 + x^4 + x^3 + x^2 + 1 (0x11d)
// Doubled antilog table eliminates % 255 in Reed-Solomon inner multiply loops.
const GF_EXP = new Uint8Array(512);
const GF_LOG = new Uint8Array(256);
(() => {
  let x = 1;
  for (let i = 0; i < 255; i += 1) {
    GF_EXP[i] = x;
    GF_EXP[i + 255] = x;
    GF_LOG[x] = i;
    x <<= 1;
    if ((x & 0x100) !== 0) {
      x ^= 0x11d;
    }
  }
  GF_EXP[510] = GF_EXP[0];
  GF_EXP[511] = GF_EXP[1];
})();

// Precomputed raw codeword counts for versions 1..40 per ISO 18004 module geometry
const RAW_CODEWORDS_BY_VERSION = new Uint16Array(41);
for (let v = 1; v <= 40; v += 1) {
  const n = 17 + 4 * v;
  let modules = n * n - 192 - 31 - 2 * (n - 16);
  if (v >= 2) {
    const k = Math.floor(v / 7) + 2;
    modules -= 25 * (k * k - 3) - 10 * (k - 2);
  }
  if (v >= 7) {
    modules -= 36;
  }
  RAW_CODEWORDS_BY_VERSION[v] = modules >>> 3;
}

// Precomputed 32-bit horizontal mask words for masks 0..7 across period 12 rows x 3 words (96 bits)
const MASK_WORD_TABLE = new Uint32Array(8 * 12 * 3);
(() => {
  for (let mask = 0; mask < 8; mask += 1) {
    for (let y = 0; y < 12; y += 1) {
      for (let w = 0; w < 3; w += 1) {
        let word = 0;
        const baseX = w * 32;
        for (let b = 0; b < 32; b += 1) {
          const x = baseX + b;
          let invert = false;
          switch (mask) {
            case 0:
              invert = ((x + y) & 1) === 0;
              break;
            case 1:
              invert = (y & 1) === 0;
              break;
            case 2:
              invert = x % 3 === 0;
              break;
            case 3:
              invert = (x + y) % 3 === 0;
              break;
            case 4:
              invert = (((y >>> 1) + ((x / 3) | 0)) & 1) === 0;
              break;
            case 5:
              invert = ((x * y) & 1) + ((x * y) % 3) === 0;
              break;
            case 6:
              invert = ((((x * y) & 1) + ((x * y) % 3)) & 1) === 0;
              break;
            case 7:
              invert = ((((x + y) & 1) + ((x * y) % 3)) & 1) === 0;
              break;
          }
          if (invert) {
            word |= 1 << b;
          }
        }
        MASK_WORD_TABLE[(mask * 12 + y) * 3 + w] = word;
      }
    }
  }
})();

const RS_GEN_LOGS_CACHE = new Map<number, Int16Array>();

function getRsGeneratorLogs(degree: number): Int16Array {
  const cached = RS_GEN_LOGS_CACHE.get(degree);
  if (cached) {
    return cached;
  }
  const coeffs = new Uint8Array(degree);
  coeffs[degree - 1] = 1;
  let rootPow = 1;
  for (let i = 0; i < degree; i += 1) {
    const rootLog = GF_LOG[rootPow];
    for (let j = 0; j < degree; j += 1) {
      const mul = coeffs[j] !== 0 ? GF_EXP[GF_LOG[coeffs[j]] + rootLog] : 0;
      coeffs[j] = j + 1 < degree ? mul ^ coeffs[j + 1] : mul;
    }
    rootPow = GF_EXP[GF_LOG[rootPow] + 1];
  }
  const logs = new Int16Array(degree);
  for (let i = 0; i < degree; i += 1) {
    logs[i] = coeffs[i] === 0 ? -1 : GF_LOG[coeffs[i]];
  }
  RS_GEN_LOGS_CACHE.set(degree, logs);
  return logs;
}

function popcnt32(v: number): number {
  let x = v - ((v >>> 1) & 0x55555555);
  x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
  return (((x + (x >>> 4)) & 0x0f0f0f0f) * 0x01010101) >>> 24;
}

function getAlignmentPositions(version: number): number[] {
  if (version === 1) {
    return [];
  }
  const count = Math.floor(version / 7) + 2;
  const step = version === 32 ? 26 : Math.ceil((version * 4 + 4) / (count * 2 - 2)) * 2;
  const coords = new Array<number>(count);
  coords[0] = 6;
  let pos = version * 4 + 10;
  for (let i = count - 1; i >= 1; i -= 1) {
    coords[i] = pos;
    pos -= step;
  }
  return coords;
}

function selectVersionForBytePayload(
  payloadLen: number,
  requestedEccIndex: number,
): { version: number; eccIndex: number; dataCodewords: number } {
  for (let version = 1; version <= 40; version += 1) {
    const rawCodewords = RAW_CODEWORDS_BY_VERSION[version];
    const reqEccPerBlock = ISO_ECC_CODEWORDS_PER_BLOCK[requestedEccIndex * 40 + (version - 1)];
    const reqBlocks = ISO_NUM_ECC_BLOCKS[requestedEccIndex * 40 + (version - 1)];
    const reqDataCodewords = rawCodewords - reqEccPerBlock * reqBlocks;
    const countHeaderBits = version <= 9 ? 8 : 16;
    const requiredBits = 4 + countHeaderBits + payloadLen * 8;
    if (requiredBits <= reqDataCodewords * 8) {
      // Maximize error-correction robustness within the chosen symbol version
      for (let cand = 3; cand > requestedEccIndex; cand -= 1) {
        const eccPerBlock = ISO_ECC_CODEWORDS_PER_BLOCK[cand * 40 + (version - 1)];
        const blocks = ISO_NUM_ECC_BLOCKS[cand * 40 + (version - 1)];
        const dataCodewords = rawCodewords - eccPerBlock * blocks;
        if (requiredBits <= dataCodewords * 8) {
          return { version, eccIndex: cand, dataCodewords };
        }
      }
      return { version, eccIndex: requestedEccIndex, dataCodewords: reqDataCodewords };
    }
  }
  throw new RangeError("QR payload exceeds ISO/IEC 18004 capacity.");
}

function packByteModeCodewords(
  utf8: Uint8Array,
  version: number,
  dataCodewords: number,
): Uint8Array {
  const buf = new Uint8Array(dataCodewords);
  const len = utf8.length;
  let pos = 0;

  if (version <= 9) {
    buf[pos++] = 0x40 | (len >>> 4);
  } else {
    buf[pos++] = 0x40 | (len >>> 12);
    buf[pos++] = (len >>> 4) & 0xff;
  }

  let carry = (len & 0x0f) << 4;
  for (let i = 0; i < len; i += 1) {
    const b = utf8[i];
    buf[pos++] = carry | (b >>> 4);
    carry = (b & 0x0f) << 4;
  }
  if (pos < dataCodewords) {
    buf[pos++] = carry;
  }

  let padByte = 0xec;
  while (pos < dataCodewords) {
    buf[pos++] = padByte;
    padByte ^= 0xec ^ 0x11;
  }
  return buf;
}

function encodeEccAndInterleave(
  version: number,
  eccIndex: number,
  dataBuf: Uint8Array,
): Uint8Array {
  const tableIdx = eccIndex * 40 + (version - 1);
  const eccLen = ISO_ECC_CODEWORDS_PER_BLOCK[tableIdx];
  const blockCount = ISO_NUM_ECC_BLOCKS[tableIdx];
  const rawCodewords = RAW_CODEWORDS_BY_VERSION[version];
  const shortBlockTotal = Math.floor(rawCodewords / blockCount);
  const numShortBlocks = blockCount - (rawCodewords % blockCount);
  const shortDataLen = shortBlockTotal - eccLen;
  const dataTotal = dataBuf.length;

  const genLogs = getRsGeneratorLogs(eccLen);
  const out = new Uint8Array(rawCodewords);
  const rem = new Uint8Array(eccLen);

  let dataCursor = 0;
  for (let b = 0; b < blockCount; b += 1) {
    const blockDataLen = b < numShortBlocks ? shortDataLen : shortDataLen + 1;
    rem.fill(0);

    for (let k = 0; k < blockDataLen; k += 1) {
      const d = dataBuf[dataCursor++];
      const destIdx =
        k < shortDataLen
          ? k * blockCount + b
          : shortDataLen * blockCount + (b - numShortBlocks);
      out[destIdx] = d;

      const factor = d ^ rem[0];
      if (factor !== 0) {
        const factorLog = GF_LOG[factor];
        for (let j = 0; j < eccLen - 1; j += 1) {
          const gLog = genLogs[j];
          rem[j] = gLog >= 0 ? rem[j + 1] ^ GF_EXP[factorLog + gLog] : rem[j + 1];
        }
        const lastLog = genLogs[eccLen - 1];
        rem[eccLen - 1] = lastLog >= 0 ? GF_EXP[factorLog + lastLog] : 0;
      } else {
        for (let j = 0; j < eccLen - 1; j += 1) {
          rem[j] = rem[j + 1];
        }
        rem[eccLen - 1] = 0;
      }
    }

    for (let j = 0; j < eccLen; j += 1) {
      out[dataTotal + j * blockCount + b] = rem[j];
    }
  }

  return out;
}

function setBit(plane: Uint32Array, wordsPerRow: number, x: number, y: number, dark: boolean): void {
  const idx = y * wordsPerRow + (x >>> 5);
  const mask = 1 << (x & 31);
  if (dark) {
    plane[idx] |= mask;
  } else {
    plane[idx] &= ~mask;
  }
}

function setFunctionBit(
  baseBits: Uint32Array,
  reservedBits: Uint32Array,
  wordsPerRow: number,
  x: number,
  y: number,
  dark: boolean,
): void {
  const idx = y * wordsPerRow + (x >>> 5);
  const mask = 1 << (x & 31);
  reservedBits[idx] |= mask;
  if (dark) {
    baseBits[idx] |= mask;
  } else {
    baseBits[idx] &= ~mask;
  }
}

// Renders concentric Chebyshev-distance square rings (Finder+Separator: half=4, darkMask=0x0b; Alignment: half=2, darkMask=0x05)
function drawConcentricSquare(
  baseBits: Uint32Array,
  reservedBits: Uint32Array,
  wordsPerRow: number,
  size: number,
  cx: number,
  cy: number,
  half: number,
  darkDistMask: number,
): void {
  const yStart = cy - half < 0 ? 0 : cy - half;
  const yEnd = cy + half >= size ? size - 1 : cy + half;
  const xStart = cx - half < 0 ? 0 : cx - half;
  const xEnd = cx + half >= size ? size - 1 : cx + half;

  for (let y = yStart; y <= yEnd; y += 1) {
    const dy = y < cy ? cy - y : y - cy;
    for (let x = xStart; x <= xEnd; x += 1) {
      const dx = x < cx ? cx - x : x - cx;
      const dist = dx > dy ? dx : dy;
      setFunctionBit(baseBits, reservedBits, wordsPerRow, x, y, ((darkDistMask >>> dist) & 1) !== 0);
    }
  }
}

function computeFormatBch(eccFormatBits: number, maskId: number): number {
  const data = (eccFormatBits << 3) | maskId;
  let rem = data;
  for (let i = 0; i < 10; i += 1) {
    rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  }
  return ((data << 10) | rem) ^ 0x5412;
}

function writeFormatBits(
  plane: Uint32Array,
  reservedBits: Uint32Array | null,
  wordsPerRow: number,
  size: number,
  eccFormatBits: number,
  maskId: number,
): void {
  const bits = computeFormatBch(eccFormatBits, maskId);
  const put = (x: number, y: number, bitIdx: number) => {
    const dark = ((bits >>> bitIdx) & 1) !== 0;
    if (reservedBits) {
      setFunctionBit(plane, reservedBits, wordsPerRow, x, y, dark);
    } else {
      setBit(plane, wordsPerRow, x, y, dark);
    }
  };

  for (let i = 0; i <= 5; i += 1) {
    put(8, i, i);
  }
  put(8, 7, 6);
  put(8, 8, 7);
  put(7, 8, 8);
  for (let i = 9; i < 15; i += 1) {
    put(14 - i, 8, i);
  }

  for (let i = 0; i < 8; i += 1) {
    put(size - 1 - i, 8, i);
  }
  for (let i = 8; i < 15; i += 1) {
    put(8, size - 15 + i, i);
  }
  if (reservedBits) {
    setFunctionBit(plane, reservedBits, wordsPerRow, 8, size - 8, true);
  } else {
    setBit(plane, wordsPerRow, 8, size - 8, true);
  }
}

function writeVersionBits(
  baseBits: Uint32Array,
  reservedBits: Uint32Array,
  wordsPerRow: number,
  size: number,
  version: number,
): void {
  if (version < 7) {
    return;
  }
  let rem = version;
  for (let i = 0; i < 12; i += 1) {
    rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
  }
  const bits = (version << 12) | rem;
  for (let i = 0; i < 18; i += 1) {
    const dark = ((bits >>> i) & 1) !== 0;
    const a = size - 11 + (i % 3);
    const b = Math.floor(i / 3);
    setFunctionBit(baseBits, reservedBits, wordsPerRow, a, b, dark);
    setFunctionBit(baseBits, reservedBits, wordsPerRow, b, a, dark);
  }
}

function drawFunctionPatterns(
  baseBits: Uint32Array,
  reservedBits: Uint32Array,
  wordsPerRow: number,
  size: number,
  version: number,
  eccFormatBits: number,
): void {
  // Timing strips (row 6 and column 6)
  for (let i = 0; i < size; i += 1) {
    const dark = (i & 1) === 0;
    setFunctionBit(baseBits, reservedBits, wordsPerRow, 6, i, dark);
    setFunctionBit(baseBits, reservedBits, wordsPerRow, i, 6, dark);
  }

  // Finder + quiet separator patterns at 3 corners (dark rings at Chebyshev distances 0, 1, 3 -> 0x0b)
  drawConcentricSquare(baseBits, reservedBits, wordsPerRow, size, 3, 3, 4, 0x0b);
  drawConcentricSquare(baseBits, reservedBits, wordsPerRow, size, size - 4, 3, 4, 0x0b);
  drawConcentricSquare(baseBits, reservedBits, wordsPerRow, size, 3, size - 4, 4, 0x0b);

  // Alignment patterns (dark rings at Chebyshev distances 0, 2 -> 0x05)
  const alignPos = getAlignmentPositions(version);
  const alignCount = alignPos.length;
  for (let i = 0; i < alignCount; i += 1) {
    for (let j = 0; j < alignCount; j += 1) {
      const skip =
        (i === 0 && j === 0) ||
        (i === 0 && j === alignCount - 1) ||
        (i === alignCount - 1 && j === 0);
      if (!skip) {
        drawConcentricSquare(baseBits, reservedBits, wordsPerRow, size, alignPos[i], alignPos[j], 2, 0x05);
      }
    }
  }

  // Reserve & seed format strips + dark module + version info
  writeFormatBits(baseBits, reservedBits, wordsPerRow, size, eccFormatBits, 0);
  writeVersionBits(baseBits, reservedBits, wordsPerRow, size, version);
}

function placeCodewords(
  baseBits: Uint32Array,
  reservedBits: Uint32Array,
  wordsPerRow: number,
  size: number,
  codewords: Uint8Array,
): void {
  let bitIndex = 0;
  const totalBits = codewords.length * 8;

  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) {
      right = 5;
    }
    const upward = (((right + 1) & 2) === 0);
    for (let vert = 0; vert < size; vert += 1) {
      const y = upward ? size - 1 - vert : vert;
      const rowBase = y * wordsPerRow;
      for (let j = 0; j < 2; j += 1) {
        const x = right - j;
        const wIdx = rowBase + (x >>> 5);
        const mask = 1 << (x & 31);
        if ((reservedBits[wIdx] & mask) === 0 && bitIndex < totalBits) {
          const dark = ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) !== 0;
          if (dark) {
            baseBits[wIdx] |= mask;
          }
          bitIndex += 1;
        }
      }
    }
  }
}

function countPenaltyN3AtLightClose(
  h0: number,
  h1: number,
  h2: number,
  h3: number,
  h4: number,
  h5: number,
  h6: number,
): number {
  if (h1 > 0 && h2 === h1 && h3 === h1 * 3 && h4 === h1 && h5 === h1) {
    let matches = 0;
    if (h0 >= h1 * 4 && h6 >= h1) {
      matches += 1;
    }
    if (h6 >= h1 * 4 && h0 >= h1) {
      matches += 1;
    }
    return matches * PENALTY_N3;
  }
  return 0;
}

function evaluateMaskPenalty(plane: Uint32Array, wordsPerRow: number, size: number): number {
  let penalty = 0;
  let darkCount = 0;

  // 1) Linear run pass for rows (axis=0) and columns (axis=1): N1 + N3, plus N4 dark popcnt on rows
  for (let axis = 0; axis < 2; axis += 1) {
    for (let outer = 0; outer < size; outer += 1) {
      const rowBase = outer * wordsPerRow;
      if (axis === 0) {
        for (let w = 0; w < wordsPerRow; w += 1) {
          darkCount += popcnt32(plane[rowBase + w]);
        }
      }
      const colWordOff = outer >>> 5;
      const colBitMask = 1 << (outer & 31);

      let runColor = 0;
      let runLen = 0;
      let h0 = 0;
      let h1 = 0;
      let h2 = 0;
      let h3 = 0;
      let h4 = 0;
      let h5 = 0;
      let h6 = 0;

      for (let inner = 0; inner < size; inner += 1) {
        const bit =
          axis === 0
            ? (plane[rowBase + (inner >>> 5)] >>> (inner & 31)) & 1
            : (plane[inner * wordsPerRow + colWordOff] & colBitMask) !== 0
              ? 1
              : 0;
        if (bit === runColor) {
          runLen += 1;
          if (runLen === 5) {
            penalty += PENALTY_N1;
          } else if (runLen > 5) {
            penalty += 1;
          }
        } else {
          const pushed = h0 === 0 ? runLen + size : runLen;
          h6 = h5;
          h5 = h4;
          h4 = h3;
          h3 = h2;
          h2 = h1;
          h1 = h0;
          h0 = pushed;
          if (runColor === 0) {
            penalty += countPenaltyN3AtLightClose(h0, h1, h2, h3, h4, h5, h6);
          }
          runColor = bit;
          runLen = 1;
        }
      }

      if (runColor === 1) {
        const pushed = h0 === 0 ? runLen + size : runLen;
        h6 = h5;
        h5 = h4;
        h4 = h3;
        h3 = h2;
        h2 = h1;
        h1 = h0;
        h0 = pushed;
        runLen = 0;
      }
      const tailLen = h0 === 0 ? runLen + size * 2 : runLen + size;
      h6 = h5;
      h5 = h4;
      h4 = h3;
      h3 = h2;
      h2 = h1;
      h1 = h0;
      h0 = tailLen;
      penalty += countPenaltyN3AtLightClose(h0, h1, h2, h3, h4, h5, h6);
    }
  }

  // 3) 2x2 same-color block pass (N2) via word-level vertical + horizontal shifts & popcnt32
  const validPairCount = size - 1;
  const lastPairWord = (validPairCount - 1) >>> 5;
  const remPairBits = validPairCount & 31;
  const lastPairMask = remPairBits === 0 ? 0xffffffff : (1 << remPairBits) - 1;

  let block2x2Count = 0;
  for (let y = 0; y < size - 1; y += 1) {
    const r0 = y * wordsPerRow;
    const r1 = (y + 1) * wordsPerRow;
    for (let w = 0; w <= lastPairWord; w += 1) {
      const w00 = plane[r0 + w];
      const w10 = plane[r1 + w];
      const hasNext = w + 1 < wordsPerRow;
      const w0Next = hasNext ? (plane[r0 + w + 1] & 1) << 31 : 0;
      const w1Next = hasNext ? (plane[r1 + w + 1] & 1) << 31 : 0;
      const w01 = (w00 >>> 1) | w0Next;
      const w11 = (w10 >>> 1) | w1Next;

      const allDark = w00 & w10 & w01 & w11;
      const allLight = ~(w00 | w10 | w01 | w11);
      let hits = allDark | allLight;
      if (w === lastPairWord) {
        hits &= lastPairMask;
      }
      block2x2Count += popcnt32(hits);
    }
  }
  penalty += block2x2Count * PENALTY_N2;

  // 4) Dark/light module ratio balance (N4)
  const totalModules = size * size;
  const k = Math.floor((Math.abs(darkCount * 20 - totalModules * 10) + totalModules - 1) / totalModules) - 1;
  if (k > 0) {
    penalty += k * PENALTY_N4;
  }
  return penalty;
}

function applyMaskPattern(
  srcBits: Uint32Array,
  reservedBits: Uint32Array,
  dstBits: Uint32Array,
  wordsPerRow: number,
  size: number,
  maskId: number,
): void {
  const maskBase = maskId * 36;
  const remBits = size & 31;
  const lastWordMask = remBits === 0 ? 0xffffffff : (1 << remBits) - 1;

  for (let y = 0; y < size; y += 1) {
    const rowBase = y * wordsPerRow;
    const mRow = maskBase + (y % 12) * 3;
    for (let w = 0; w < wordsPerRow; w += 1) {
      const idx = rowBase + w;
      const mWord = MASK_WORD_TABLE[mRow + (w % 3)];
      let val = srcBits[idx] ^ (mWord & ~reservedBits[idx]);
      if (w === wordsPerRow - 1) {
        val &= lastWordMask;
      }
      dstBits[idx] = val;
    }
  }
}

export function encodeQrCodeMatrix(text: string, ecc: QrEccLevel = "MEDIUM"): QrMatrixResult {
  if (typeof text !== "string" || text.length === 0) {
    throw new TypeError("QR payload must be a non-empty string.");
  }

  const utf8 = UTF8_ENCODER.encode(text);
  if (utf8.length === 0 || utf8.length > MAX_QR_INPUT_BYTES) {
    throw new RangeError(`QR payload must be between 1 and ${MAX_QR_INPUT_BYTES} UTF-8 bytes.`);
  }

  const requestedEccIndex = resolveEccIndex(ecc);
  const { version, eccIndex, dataCodewords } = selectVersionForBytePayload(
    utf8.length,
    requestedEccIndex,
  );
  const selectedEcc = ECC_LEVEL_BY_INDEX[eccIndex];
  const formatBits = ECC_FORMAT_BITS[eccIndex];
  const packedData = packByteModeCodewords(utf8, version, dataCodewords);
  const allCodewords = encodeEccAndInterleave(version, eccIndex, packedData);

  const size = 17 + 4 * version;
  const wordsPerRow = (size + 31) >>> 5;
  const baseBits = new Uint32Array(size * wordsPerRow);
  const reservedBits = new Uint32Array(size * wordsPerRow);

  drawFunctionPatterns(baseBits, reservedBits, wordsPerRow, size, version, formatBits);
  placeCodewords(baseBits, reservedBits, wordsPerRow, size, allCodewords);

  const candidateBits = new Uint32Array(size * wordsPerRow);
  let bestMask = 0;
  let minPenalty = Number.POSITIVE_INFINITY;

  for (let maskId = 0; maskId < 8; maskId += 1) {
    applyMaskPattern(baseBits, reservedBits, candidateBits, wordsPerRow, size, maskId);
    writeFormatBits(candidateBits, null, wordsPerRow, size, formatBits, maskId);
    const penalty = evaluateMaskPenalty(candidateBits, wordsPerRow, size);
    if (penalty < minPenalty) {
      minPenalty = penalty;
      bestMask = maskId;
    }
  }

  applyMaskPattern(baseBits, reservedBits, candidateBits, wordsPerRow, size, bestMask);
  writeFormatBits(candidateBits, null, wordsPerRow, size, formatBits, bestMask);

  return {
    version,
    ecc: selectedEcc,
    size,
    wordsPerRow,
    bits: candidateBits,
    isDark: (x: number, y: number) =>
      x >= 0 &&
      x < size &&
      y >= 0 &&
      y < size &&
      ((candidateBits[y * wordsPerRow + (x >>> 5)] >>> (x & 31)) & 1) !== 0,
  };
}

export function encodeQrCodeSvgPaths(
  text: string,
  options?: { ecc?: QrEccLevel; border?: number },
): QrCodeSvgResult {
  const ecc = options?.ecc ?? "MEDIUM";
  const border = options?.border ?? 4;
  if (!Number.isInteger(border) || border < 0 || border > MAX_QR_BORDER) {
    throw new RangeError(`QR border must be an integer between 0 and ${MAX_QR_BORDER}.`);
  }

  const matrix = encodeQrCodeMatrix(text, ecc);
  const { size, wordsPerRow, bits, version } = matrix;

  // Serialize horizontal dark module runs directly to keep SVG path length compact.
  let pathData = "";
  for (let y = 0; y < size; y += 1) {
    const rowBase = y * wordsPerRow;
    const py = y + border;
    let x = 0;
    while (x < size) {
      const word = bits[rowBase + (x >>> 5)];
      const bitPos = x & 31;
      if (((word >>> bitPos) & 1) === 0) {
        // Fast skip empty zero bits within remaining word
        const shifted = word >>> bitPos;
        if (shifted === 0) {
          x += 32 - bitPos;
        } else {
          x += 1;
        }
        continue;
      }
      const startX = x;
      x += 1;
      while (x < size && ((bits[rowBase + (x >>> 5)] >>> (x & 31)) & 1) !== 0) {
        x += 1;
      }
      const len = x - startX;
      pathData += `M${startX + border} ${py}h${len}v1h-${len}z`;
    }
  }

  const totalSize = size + border * 2;
  return {
    version,
    ecc: matrix.ecc,
    moduleCount: size,
    size: totalSize,
    viewBox: `0 0 ${totalSize} ${totalSize}`,
    pathData,
  };
}
