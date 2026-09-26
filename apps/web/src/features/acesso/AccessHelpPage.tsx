import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router'
import { Logo } from '@/components/layout/Logo'

export function AccessHelpPage() {
  return (
    <Box component="main" sx={{ maxWidth: 680, mx: 'auto', p: 4 }}>
      <Logo size="lg" tone="dark" />
      <Typography variant="h4" sx={{ mt: 4 }}>
        Ajuda para acessar
      </Typography>
      <Typography sx={{ mt: 2 }}>
        No primeiro acesso, peça o código de ativação ao administrador da instalação e use a opção
        “Ativar meu acesso” na tela de login.
      </Typography>
      <Typography sx={{ mt: 2 }}>
        Se o código venceu, peça ao administrador que reemita um novo código em “Ativações
        pendentes”. Se você esqueceu a senha de uma conta já ativa, procure o suporte ou
        administrador da sua instalação.
      </Typography>
      <Link component={RouterLink} to="/login" sx={{ display: 'inline-block', mt: 3 }}>
        Voltar ao login
      </Link>
    </Box>
  )
}
