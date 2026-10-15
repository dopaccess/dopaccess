export interface Crowd {
  unit: number;
  walkers: number;
  giants: number;
}

const MAX_WALKERS = 40;
const MAX_GIANTS = 5;

/** 00-overview.md §6 相対スケール */
export function relativeCrowd(current: number, baseline: number): Crowd {
  const unit = Math.max(1, Math.ceil(baseline / 20));
  const walkers = Math.min(MAX_WALKERS, Math.floor(current / unit));
  const giantStep = Math.max(baseline, 1) * 3;
  const giants =
    current >= giantStep && current >= 100
      ? Math.min(MAX_GIANTS, Math.floor(current / giantStep))
      : 0;
  return { unit, walkers, giants };
}

/** 絶対モード: 通常人 1 人 = 1 ユーザー、巨人 1 人 = 100 ユーザー */
export function absoluteCrowd(current: number): Crowd {
  const giants = Math.min(MAX_GIANTS, Math.floor(current / 100));
  const walkers = Math.min(MAX_WALKERS, current - giants * 100);
  return { unit: 1, walkers: Math.max(0, walkers), giants };
}
