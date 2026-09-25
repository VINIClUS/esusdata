import type { SxProps, Theme } from '@mui/material/styles'

type SxEntry = Exclude<SxProps<Theme>, readonly unknown[]>

/**
 * A component's own styles followed by the caller's `sx`, in MUI's array form: `sx` may itself be
 * an array or a theme callback, which an object spread would silently drop.
 */
export function mergeSx(own: SxEntry, sx: SxProps<Theme> | undefined): SxProps<Theme> {
  const extra = Array.isArray(sx) ? (sx as readonly SxEntry[]) : [sx as SxEntry | undefined]
  return [own, ...extra.filter((entry) => entry !== undefined)]
}
