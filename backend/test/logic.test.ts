import { describe, expect, it } from "vitest";
import { computeSlots, median } from "../src/baseline";
import { absoluteCrowd, relativeCrowd } from "../src/crowd";
import { DEFAULT_THRESHOLDS } from "../src/env";
import { decideTier, divergence } from "../src/tier";
import { dowOfYmd, isoInZone, pastDates, zonedParts } from "../src/time";

const base = { minBaseline: 5, minBurstUsers: 10, thresholds: DEFAULT_THRESHOLDS };
const full = (current: number, baseline: number) =>
  decideTier({ ...base, current, baseline, tierSet: "full" });
const mvp = (current: number, baseline: number) =>
  decideTier({ ...base, current, baseline, tierSet: "mvp" });

describe("divergence", () => {
  it("uses max(baseline, 1) to avoid division by zero", () => {
    expect(divergence(3, 0)).toBe(300);
    expect(divergence(42, 28.5)).toBeCloseTo(47.37, 1);
  });
});

describe("decideTier (full)", () => {
  it("applies boundaries", () => {
    expect(full(39, 100)).toBe("KYOMU"); // -61%
    expect(full(40, 100)).toBe("KANKOTORI"); // -60%
    expect(full(69, 100)).toBe("KANKOTORI"); // -31%
    expect(full(70, 100)).toBe("NORMAL"); // -30%
    expect(full(129, 100)).toBe("NORMAL");
    expect(full(130, 100)).toBe("WAKUWAKU"); // +30%
    expect(full(199, 100)).toBe("WAKUWAKU");
    expect(full(200, 100)).toBe("BAKU_AGE"); // +100%
    expect(full(399, 100)).toBe("BAKU_AGE");
    expect(full(400, 100)).toBe("GOD_BURST"); // +300%
  });
  it("skips judging when both baseline and current are tiny", () => {
    expect(full(0, 4)).toBe("NORMAL");
    expect(full(4, 0)).toBe("NORMAL");
  });
  it("downgrades GOD_BURST below MIN_BURST_USERS", () => {
    expect(full(9, 1)).toBe("BAKU_AGE"); // +800% but only 9 users
    expect(full(10, 1)).toBe("GOD_BURST");
  });
});

describe("decideTier (mvp)", () => {
  it("collapses to three tiers", () => {
    expect(mvp(69, 100)).toBe("KYOMU");
    expect(mvp(70, 100)).toBe("NORMAL");
    expect(mvp(199, 100)).toBe("NORMAL");
    expect(mvp(200, 100)).toBe("GOD_BURST");
    expect(mvp(9, 1)).toBe("NORMAL");
  });
});

describe("crowd", () => {
  it("matches the spec example", () => {
    expect(relativeCrowd(42, 28.5)).toEqual({ unit: 2, walkers: 21, giants: 0 });
  });
  it("caps walkers and spawns giants", () => {
    expect(relativeCrowd(1000, 20)).toEqual({ unit: 1, walkers: 40, giants: 5 });
    expect(relativeCrowd(120, 40)).toEqual({ unit: 2, walkers: 40, giants: 1 });
    expect(relativeCrowd(99, 10).giants).toBe(0); // under 100 users
  });
  it("absolute mode", () => {
    expect(absoluteCrowd(250)).toEqual({ unit: 1, walkers: 40, giants: 2 });
    expect(absoluteCrowd(7)).toEqual({ unit: 1, walkers: 7, giants: 0 });
  });
});

describe("median / slots", () => {
  it("median", () => {
    expect(median([])).toBe(0);
    expect(median([3, 1, 2])).toBe(2);
    expect(median([4, 1, 3, 2])).toBe(2.5);
  });
  it("fills missing hours with zero and groups by weekday", () => {
    const dates = ["20261014", "20261007", "20260930", "20260923"]; // all Wednesdays
    const rows = [
      { date: "20261014", hour: 21, activeUsers: 10 },
      { date: "20261007", hour: 21, activeUsers: 20 },
      { date: "20260930", hour: 21, activeUsers: 30 },
    ];
    const slots = computeSlots(rows, dates);
    expect(slots[3][21]).toBe(15); // median of [10,20,30,0]
    expect(slots[3][0]).toBe(0);
    expect(slots[0][21]).toBe(0);
  });
});

describe("time", () => {
  it("zoned parts and iso", () => {
    const d = new Date("2026-10-15T12:03:40Z");
    expect(zonedParts(d, "Asia/Tokyo")).toMatchObject({ hour: 21, dow: 4, day: 15 });
    expect(isoInZone(d, "Asia/Tokyo")).toBe("2026-10-15T21:03:40+09:00");
    expect(isoInZone(d, "America/Los_Angeles")).toBe("2026-10-15T05:03:40-07:00");
  });
  it("dates", () => {
    expect(dowOfYmd("20261015")).toBe(4);
    const ds = pastDates(new Date("2026-10-15T00:30:00Z"), "Asia/Tokyo", 28);
    expect(ds[0]).toBe("20261014");
    expect(ds).toHaveLength(28);
  });
});
