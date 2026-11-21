/*
 * QR Code generator (Byte mode, ES module adaptation for offline OTP enrollment)
 *
 * 참조 구현: Project Nayuki QR-Code-generator (typescript-javascript/qrcodegen.ts)
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

export const MAX_QR_INPUT_BYTES = 512;

export type QrEccLevel = "LOW" | "MEDIUM" | "QUARTILE" | "HIGH";

export type QrCodeSvgResult = {
  version: number;
  ecc: QrEccLevel;
  moduleCount: number;
  size: number;
  viewBox: string;
  pathData: string;
};

const ECC_INDEX: Record<QrEccLevel, { ordinal: number; formatBits: number }> = {
  LOW: { ordinal: 0, formatBits: 1 },
  MEDIUM: { ordinal: 1, formatBits: 0 },
  QUARTILE: { ordinal: 2, formatBits: 3 },
  HIGH: { ordinal: 3, formatBits: 2 },
};

const ECC_ORDER: QrEccLevel[] = ["LOW", "MEDIUM", "QUARTILE", "HIGH"];

const MIN_VERSION = 1;
const MAX_VERSION = 40;
const PENALTY_N1 = 3;
const PENALTY_N2 = 3;
const PENALTY_N3 = 40;
const PENALTY_N4 = 10;

const ECC_CODEWORDS_PER_BLOCK: ReadonlyArray<ReadonlyArray<number>> = [
  [-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
  [-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  [-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
];

const NUM_ERROR_CORRECTION_BLOCKS: ReadonlyArray<ReadonlyArray<number>> = [
  [-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
  [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
  [-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
  [-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],
];

// GF(2^8) 다항식 0x11D 기준 정적 지수·로그 테이블 (런타임 비트 곱셈 루프 제거)
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
})();

function gfMultiply(a: number, b: number): number {
  if (a === 0 || b === 0) {
    return 0;
  }
  return GF_EXP[GF_LOG[a] + GF_LOG[b]];
}

function getNumRawDataModules(ver: number): number {
  let result = (16 * ver + 128) * ver + 64;
  if (ver >= 2) {
    const numAlign = Math.floor(ver / 7) + 2;
    result -= (25 * numAlign - 10) * numAlign - 55;
    if (ver >= 7) {
      result -= 36;
    }
  }
  return result;
}

function getNumDataCodewords(ver: number, eccOrdinal: number): number {
  return (
    Math.floor(getNumRawDataModules(ver) / 8) -
    ECC_CODEWORDS_PER_BLOCK[eccOrdinal][ver] * NUM_ERROR_CORRECTION_BLOCKS[eccOrdinal][ver]
  );
}

function getAlignmentPatternPositions(ver: number, size: number): number[] {
  if (ver === 1) {
    return [];
  }
  const numAlign = Math.floor(ver / 7) + 2;
  const step = Math.floor((ver * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4)) * 2;
  const result = [6];
  for (let pos = size - 7; result.length < numAlign; pos -= step) {
    result.splice(1, 0, pos);
  }
  return result;
}

function computeReedSolomonDivisor(degree: number): Uint8Array {
  const result = new Uint8Array(degree);
  result[degree - 1] = 1;
  let root = 1;
  for (let i = 0; i < degree; i += 1) {
    for (let j = 0; j < degree; j += 1) {
      result[j] = gfMultiply(result[j], root);
      if (j + 1 < degree) {
        result[j] ^= result[j + 1];
      }
    }
    root = gfMultiply(root, 0x02);
  }
  return result;
}

function computeReedSolomonRemainder(data: Uint8Array, divisor: Uint8Array): Uint8Array {
  const degree = divisor.length;
  const rem = new Uint8Array(degree);
  for (let d = 0; d < data.length; d += 1) {
    const factor = data[d] ^ rem[0];
    for (let i = 0; i < degree - 1; i += 1) {
      rem[i] = rem[i + 1] ^ gfMultiply(divisor[i], factor);
    }
    rem[degree - 1] = gfMultiply(divisor[degree - 1], factor);
  }
  return rem;
}

function appendBitsToBuffer(val: number, len: number, buf: number[]): void {
  for (let i = len - 1; i >= 0; i -= 1) {
    buf.push((val >>> i) & 1);
  }
}

function addEccAndInterleave(
  ver: number,
  eccOrdinal: number,
  data: Uint8Array,
): Uint8Array {
  const numBlocks = NUM_ERROR_CORRECTION_BLOCKS[eccOrdinal][ver];
  const blockEccLen = ECC_CODEWORDS_PER_BLOCK[eccOrdinal][ver];
  const rawCodewords = Math.floor(getNumRawDataModules(ver) / 8);
  const numShortBlocks = numBlocks - (rawCodewords % numBlocks);
  const shortBlockLen = Math.floor(rawCodewords / numBlocks);

  const blocks: Uint8Array[] = [];
  const rsDiv = computeReedSolomonDivisor(blockEccLen);
  let offset = 0;
  for (let i = 0; i < numBlocks; i += 1) {
    const dataLen = shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1);
    const slice = data.subarray(offset, offset + dataLen);
    offset += dataLen;
    const ecc = computeReedSolomonRemainder(slice, rsDiv);
    const block = new Uint8Array(shortBlockLen + 1);
    block.set(slice, 0);
    if (i < numShortBlocks) {
      block.set(ecc, dataLen + 1);
    } else {
      block.set(ecc, dataLen);
    }
    blocks.push(block);
  }

  const result = new Uint8Array(rawCodewords);
  let outIdx = 0;
  for (let i = 0; i <= shortBlockLen; i += 1) {
    for (let j = 0; j < numBlocks; j += 1) {
      if (i === shortBlockLen - blockEccLen && j < numShortBlocks) {
        continue;
      }
      result[outIdx] = blocks[j][i];
      outIdx += 1;
    }
  }
  return result;
}

class QrMatrix {
  private readonly version: number;
  readonly size: number;
  readonly modules: Uint8Array;
  readonly isFunction: Uint8Array;

  constructor(version: number) {
    this.version = version;
    this.size = version * 4 + 17;
    this.modules = new Uint8Array(this.size * this.size);
    this.isFunction = new Uint8Array(this.size * this.size);
  }

  setFunction(x: number, y: number, dark: boolean): void {
    const idx = y * this.size + x;
    this.modules[idx] = dark ? 1 : 0;
    this.isFunction[idx] = 1;
  }

  get(x: number, y: number): number {
    return this.modules[y * this.size + x];
  }

  drawFinderPattern(cx: number, cy: number): void {
    for (let dy = -4; dy <= 4; dy += 1) {
      for (let dx = -4; dx <= 4; dx += 1) {
        const xx = cx + dx;
        const yy = cy + dy;
        if (xx >= 0 && xx < this.size && yy >= 0 && yy < this.size) {
          const dist = Math.max(Math.abs(dx), Math.abs(dy));
          this.setFunction(xx, yy, dist !== 2 && dist !== 4);
        }
      }
    }
  }

  drawAlignmentPattern(cx: number, cy: number): void {
    for (let dy = -2; dy <= 2; dy += 1) {
      for (let dx = -2; dx <= 2; dx += 1) {
        this.setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
      }
    }
  }

  drawFormatBits(formatBitsPrefix: number, mask: number): void {
    const data = (formatBitsPrefix << 3) | mask;
    let rem = data;
    for (let i = 0; i < 10; i += 1) {
      rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
    }
    const bits = ((data << 10) | rem) ^ 0x5412;

    for (let i = 0; i <= 5; i += 1) {
      this.setFunction(8, i, ((bits >>> i) & 1) !== 0);
    }
    this.setFunction(8, 7, ((bits >>> 6) & 1) !== 0);
    this.setFunction(8, 8, ((bits >>> 7) & 1) !== 0);
    this.setFunction(7, 8, ((bits >>> 8) & 1) !== 0);
    for (let i = 9; i < 15; i += 1) {
      this.setFunction(14 - i, 8, ((bits >>> i) & 1) !== 0);
    }
    for (let i = 0; i < 8; i += 1) {
      this.setFunction(this.size - 1 - i, 8, ((bits >>> i) & 1) !== 0);
    }
    for (let i = 8; i < 15; i += 1) {
      this.setFunction(8, this.size - 15 + i, ((bits >>> i) & 1) !== 0);
    }
    this.setFunction(8, this.size - 8, true);
  }

  drawVersionBits(): void {
    if (this.version < 7) {
      return;
    }
    let rem = this.version;
    for (let i = 0; i < 12; i += 1) {
      rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
    }
    const bits = (this.version << 12) | rem;
    for (let i = 0; i < 18; i += 1) {
      const color = ((bits >>> i) & 1) !== 0;
      const a = this.size - 11 + (i % 3);
      const b = Math.floor(i / 3);
      this.setFunction(a, b, color);
      this.setFunction(b, a, color);
    }
  }

  drawFunctionPatterns(formatBitsPrefix: number): void {
    for (let i = 0; i < this.size; i += 1) {
      this.setFunction(6, i, i % 2 === 0);
      this.setFunction(i, 6, i % 2 === 0);
    }
    this.drawFinderPattern(3, 3);
    this.drawFinderPattern(this.size - 4, 3);
    this.drawFinderPattern(3, this.size - 4);

    const align = getAlignmentPatternPositions(this.version, this.size);
    for (let i = 0; i < align.length; i += 1) {
      for (let j = 0; j < align.length; j += 1) {
        if (
          !(
            (i === 0 && j === 0) ||
            (i === 0 && j === align.length - 1) ||
            (i === align.length - 1 && j === 0)
          )
        ) {
          this.drawAlignmentPattern(align[i], align[j]);
        }
      }
    }
    this.drawFormatBits(formatBitsPrefix, 0);
    this.drawVersionBits();
  }

  drawCodewords(allCodewords: Uint8Array): void {
    let bitIndex = 0;
    const totalBits = allCodewords.length * 8;
    for (let right = this.size - 1; right >= 1; right -= 2) {
      if (right === 6) {
        right = 5;
      }
      for (let vert = 0; vert < this.size; vert += 1) {
        for (let j = 0; j < 2; j += 1) {
          const x = right - j;
          const upward = ((right + 1) & 2) === 0;
          const y = upward ? this.size - 1 - vert : vert;
          const idx = y * this.size + x;
          if (this.isFunction[idx] === 0 && bitIndex < totalBits) {
            this.modules[idx] = (allCodewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1;
            bitIndex += 1;
          }
        }
      }
    }
  }

  applyMask(mask: number): void {
    for (let y = 0; y < this.size; y += 1) {
      for (let x = 0; x < this.size; x += 1) {
        let invert = false;
        switch (mask) {
          case 0:
            invert = (x + y) % 2 === 0;
            break;
          case 1:
            invert = y % 2 === 0;
            break;
          case 2:
            invert = x % 3 === 0;
            break;
          case 3:
            invert = (x + y) % 3 === 0;
            break;
          case 4:
            invert = (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0;
            break;
          case 5:
            invert = ((x * y) % 2) + ((x * y) % 3) === 0;
            break;
          case 6:
            invert = (((x * y) % 2) + ((x * y) % 3)) % 2 === 0;
            break;
          case 7:
            invert = (((x + y) % 2) + ((x * y) % 3)) % 2 === 0;
            break;
        }
        const idx = y * this.size + x;
        if (invert && this.isFunction[idx] === 0) {
          this.modules[idx] ^= 1;
        }
      }
    }
  }

  private finderPenaltyAddHistory(currentRunLength: number, runHistory: Int32Array): void {
    let runLen = currentRunLength;
    if (runHistory[0] === 0) {
      runLen += this.size;
    }
    for (let i = 6; i >= 1; i -= 1) {
      runHistory[i] = runHistory[i - 1];
    }
    runHistory[0] = runLen;
  }

  private finderPenaltyCountPatterns(runHistory: Int32Array): number {
    const n = runHistory[1];
    const core =
      n > 0 &&
      runHistory[2] === n &&
      runHistory[3] === n * 3 &&
      runHistory[4] === n &&
      runHistory[5] === n;
    return (
      (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0) +
      (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0)
    );
  }

  private finderPenaltyTerminate(
    currentRunColor: number,
    currentRunLength: number,
    runHistory: Int32Array,
  ): number {
    let runLen = currentRunLength;
    if (currentRunColor === 1) {
      this.finderPenaltyAddHistory(runLen, runHistory);
      runLen = 0;
    }
    runLen += this.size;
    this.finderPenaltyAddHistory(runLen, runHistory);
    return this.finderPenaltyCountPatterns(runHistory);
  }

  getPenaltyScore(): number {
    let result = 0;
    const runHistory = new Int32Array(7);

    for (let y = 0; y < this.size; y += 1) {
      let runColor = 0;
      let runX = 0;
      runHistory.fill(0);
      for (let x = 0; x < this.size; x += 1) {
        const mod = this.get(x, y);
        if (mod === runColor) {
          runX += 1;
          if (runX === 5) {
            result += PENALTY_N1;
          } else if (runX > 5) {
            result += 1;
          }
        } else {
          this.finderPenaltyAddHistory(runX, runHistory);
          if (runColor === 0) {
            result += this.finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
          }
          runColor = mod;
          runX = 1;
        }
      }
      result += this.finderPenaltyTerminate(runColor, runX, runHistory) * PENALTY_N3;
    }

    for (let x = 0; x < this.size; x += 1) {
      let runColor = 0;
      let runY = 0;
      runHistory.fill(0);
      for (let y = 0; y < this.size; y += 1) {
        const mod = this.get(x, y);
        if (mod === runColor) {
          runY += 1;
          if (runY === 5) {
            result += PENALTY_N1;
          } else if (runY > 5) {
            result += 1;
          }
        } else {
          this.finderPenaltyAddHistory(runY, runHistory);
          if (runColor === 0) {
            result += this.finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
          }
          runColor = mod;
          runY = 1;
        }
      }
      result += this.finderPenaltyTerminate(runColor, runY, runHistory) * PENALTY_N3;
    }

    for (let y = 0; y < this.size - 1; y += 1) {
      for (let x = 0; x < this.size - 1; x += 1) {
        const color = this.get(x, y);
        if (
          color === this.get(x + 1, y) &&
          color === this.get(x, y + 1) &&
          color === this.get(x + 1, y + 1)
        ) {
          result += PENALTY_N2;
        }
      }
    }

    let dark = 0;
    for (let i = 0; i < this.modules.length; i += 1) {
      dark += this.modules[i];
    }
    const total = this.size * this.size;
    const k = Math.ceil(Math.abs(dark * 20 - total * 10) / total) - 1;
    return result + k * PENALTY_N4;
  }
}

export function encodeQrCodeSvgPaths(
  text: string,
  options: { ecc?: QrEccLevel; border?: number } = {},
): QrCodeSvgResult {
  const border = options.border ?? 4;
  if (!Number.isInteger(border) || border < 0 || border > 10) {
    throw new RangeError("QR border must be an integer between 0 and 10.");
  }
  const bytes = new TextEncoder().encode(text);
  if (bytes.length === 0 || bytes.length > MAX_QR_INPUT_BYTES) {
    throw new RangeError(
      `QR input payload must be between 1 and ${MAX_QR_INPUT_BYTES} UTF-8 bytes.`,
    );
  }

  let selectedEcc = options.ecc ?? "MEDIUM";
  let version = MIN_VERSION;
  let dataUsedBits = 0;
  for (; version <= MAX_VERSION; version += 1) {
    const charCountBits = version <= 9 ? 8 : 16;
    const neededBits = 4 + charCountBits + bytes.length * 8;
    const capacityBits = getNumDataCodewords(version, ECC_INDEX[selectedEcc].ordinal) * 8;
    if (neededBits <= capacityBits) {
      dataUsedBits = neededBits;
      break;
    }
  }
  if (version > MAX_VERSION) {
    throw new RangeError("QR input exceeds symbol capacity.");
  }

  for (const candidate of ECC_ORDER) {
    if (
      ECC_INDEX[candidate].ordinal > ECC_INDEX[selectedEcc].ordinal &&
      dataUsedBits <= getNumDataCodewords(version, ECC_INDEX[candidate].ordinal) * 8
    ) {
      selectedEcc = candidate;
    }
  }

  const eccMeta = ECC_INDEX[selectedEcc];
  const bitBuffer: number[] = [];
  const charCountBits = version <= 9 ? 8 : 16;
  appendBitsToBuffer(0x4, 4, bitBuffer);
  appendBitsToBuffer(bytes.length, charCountBits, bitBuffer);
  for (let i = 0; i < bytes.length; i += 1) {
    appendBitsToBuffer(bytes[i], 8, bitBuffer);
  }

  const capacityBits = getNumDataCodewords(version, eccMeta.ordinal) * 8;
  appendBitsToBuffer(0, Math.min(4, capacityBits - bitBuffer.length), bitBuffer);
  appendBitsToBuffer(0, (8 - (bitBuffer.length % 8)) % 8, bitBuffer);
  for (let padByte = 0xec; bitBuffer.length < capacityBits; padByte ^= 0xec ^ 0x11) {
    appendBitsToBuffer(padByte, 8, bitBuffer);
  }

  const dataCodewords = new Uint8Array(capacityBits >>> 3);
  for (let i = 0; i < bitBuffer.length; i += 1) {
    dataCodewords[i >>> 3] |= bitBuffer[i] << (7 - (i & 7));
  }

  const matrix = new QrMatrix(version);
  matrix.drawFunctionPatterns(eccMeta.formatBits);
  const allCodewords = addEccAndInterleave(version, eccMeta.ordinal, dataCodewords);
  matrix.drawCodewords(allCodewords);

  let bestMask = 0;
  let minPenalty = Number.POSITIVE_INFINITY;
  for (let m = 0; m < 8; m += 1) {
    matrix.applyMask(m);
    matrix.drawFormatBits(eccMeta.formatBits, m);
    const penalty = matrix.getPenaltyScore();
    if (penalty < minPenalty) {
      bestMask = m;
      minPenalty = penalty;
    }
    matrix.applyMask(m);
  }
  matrix.applyMask(bestMask);
  matrix.drawFormatBits(eccMeta.formatBits, bestMask);

  // 가로 연속 어두운 셀을 한 번에 직렬화해 SVG <path> 길이와 DOM 노드를 최소화한다.
  const segments: string[] = [];
  for (let y = 0; y < matrix.size; y += 1) {
    let x = 0;
    while (x < matrix.size) {
      if (matrix.get(x, y) === 1) {
        const startX = x;
        while (x < matrix.size && matrix.get(x, y) === 1) {
          x += 1;
        }
        segments.push(`M${startX + border} ${y + border}h${x - startX}v1h${startX - x}z`);
      } else {
        x += 1;
      }
    }
  }

  const totalSize = matrix.size + border * 2;
  return {
    version,
    ecc: selectedEcc,
    moduleCount: matrix.size,
    size: totalSize,
    viewBox: `0 0 ${totalSize} ${totalSize}`,
    pathData: segments.join(""),
  };
}
