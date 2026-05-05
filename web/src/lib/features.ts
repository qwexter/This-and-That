export interface Features {
  priority: boolean;
  deadline: boolean;
  description: boolean;
  records: boolean;
  groups: boolean;
  spaces: boolean;
  profile: boolean;
}

// Each flag is ON by default. Set VITE_FEATURE_X=false to disable.
export const features: Features = {
  priority: import.meta.env.VITE_FEATURE_PRIORITY !== "false",
  deadline: import.meta.env.VITE_FEATURE_DEADLINE !== "false",
  description: import.meta.env.VITE_FEATURE_DESCRIPTION !== "false",
  records: import.meta.env.VITE_FEATURE_RECORDS == "false",
  groups: import.meta.env.VITE_FEATURE_GROUPS !== "false",
  spaces: import.meta.env.VITE_FEATURE_SPACES !== "false",
  profile: import.meta.env.VITE_FEATURE_PROFILE !== "false",
};
