// sizeRatio scales the sprite against the layout's reference illustration.
// heightRatio measures the object's effective height above the desk in units
// of that sprite's full side, including its transparent margins.
export const DESK_OBJECT_PROFILES = {
  journal: { sizeRatio: 1, heightRatio: 0.045 },
  record: { sizeRatio: 0.98, heightRatio: 0.018 },
  recipe: { sizeRatio: 0.94, heightRatio: 0.027 },
  photo: { sizeRatio: 0.88, heightRatio: 0.009 },
  cup: { sizeRatio: 0.83, heightRatio: 0.14 },
  envelope: { sizeRatio: 0.8, heightRatio: 0.014 },
  note: { sizeRatio: 0.75, heightRatio: 0.01 },
  phone: { sizeRatio: 0.72, heightRatio: 0.032 },
  fruit: { sizeRatio: 0.7, heightRatio: 0.105 },
  bookmark: { sizeRatio: 0.65, heightRatio: 0.008 },
  leaf: { sizeRatio: 0.58, heightRatio: 0.012 },
}
