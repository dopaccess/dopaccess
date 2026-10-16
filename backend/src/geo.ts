import { prefOfCity } from "./jpcities";

/** 1 地点ぶんの人数。pref: 1..47 = 都道府県, 0 = 国内だが場所不明, -1 = 海外 */
export interface Spot {
  pref: number;
  device: "mobile" | "desktop";
  users: number;
}

export interface Geo {
  mobile: number;
  desktop: number;
  overseas: number;
  spots: Spot[];
}

export function buildGeo(rows: { device: string; countryId: string; cityId: string; users: number }[]): Geo {
  const acc = new Map<string, Spot>();
  let mobile = 0;
  let desktop = 0;
  let overseas = 0;
  for (const r of rows) {
    if (!(r.users > 0)) continue;
    const device = r.device === "desktop" ? "desktop" : "mobile"; // tablet などはスマホ扱い
    if (device === "desktop") desktop += r.users;
    else mobile += r.users;
    let pref: number;
    if (r.countryId && r.countryId !== "JP") {
      pref = -1;
      overseas += r.users;
    } else {
      pref = prefOfCity(r.cityId) ?? 0;
    }
    const key = `${pref}:${device}`;
    const s = acc.get(key);
    if (s) s.users += r.users;
    else acc.set(key, { pref, device, users: r.users });
  }
  const spots = [...acc.values()].sort((a, b) => b.users - a.users);
  return { mobile, desktop, overseas, spots };
}
