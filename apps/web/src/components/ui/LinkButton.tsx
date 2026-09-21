import Button, { type ButtonProps } from '@mui/material/Button'
import { Link } from 'react-router'

/** Text-style link ("Ver todos") used as card actions. */
export function LinkButton({ to, children, ...rest }: ButtonProps & { to?: string }) {
  const props = to ? { component: Link, to } : {}
  return (
    <Button variant="text" size="small" {...props} {...rest} sx={{ fontSize: 13.5, fontWeight: 600, px: 0.5, minHeight: 28, ...rest.sx }}>
      {children}
    </Button>
  )
}
