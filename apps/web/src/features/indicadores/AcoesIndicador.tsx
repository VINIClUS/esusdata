import { useState } from 'react'
import IconButton from '@mui/material/IconButton'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import { EllipsisVertical } from 'lucide-react'
import { useNavigate } from 'react-router'
import { baixarExportacao, gerarExportacao } from '@/api/hooks'
import { normalizeExport } from '@/api/normalizers'
import { useScope } from '@/app/scope-context'
import { colors } from '@/theme/tokens'

/**
 * A row's actions: open the detail, run the indicator again for the competência, or download the
 * competência's aggregate CSV (ADR 0024) for this indicator alone.
 */
export function AcoesIndicador({
  codigo,
  competencia,
  onErro,
}: {
  codigo: string
  competencia: string | undefined
  onErro: (mensagem: string) => void
}) {
  const navigate = useNavigate()
  const { municipalityIbge } = useScope()
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const fechar = () => setAnchor(null)

  async function exportar() {
    fechar()
    if (!competencia || !municipalityIbge) return
    try {
      const gerada = await gerarExportacao(municipalityIbge, competencia, competencia, codigo)
      await baixarExportacao(normalizeExport(gerada), municipalityIbge)
    } catch {
      onErro(`Não foi possível exportar ${codigo}. Tente novamente em Relatórios.`)
    }
  }

  const executar = new URLSearchParams({ indicador: codigo })
  if (competencia) executar.set('competencia', competencia)

  return (
    <>
      <IconButton
        size="small"
        aria-label={`Ações de ${codigo}`}
        aria-haspopup="menu"
        onClick={(e) => {
          e.stopPropagation()
          setAnchor(e.currentTarget)
        }}
        sx={{ color: colors.primary }}
      >
        <EllipsisVertical size={18} />
      </IconButton>
      <Menu
        anchorEl={anchor}
        open={anchor !== null}
        onClose={fechar}
        onClick={(e) => e.stopPropagation()}
      >
        <MenuItem onClick={() => void navigate(`/indicadores/${codigo}`)}>Ver detalhe</MenuItem>
        <MenuItem onClick={() => void navigate(`/execucao?${executar.toString()}`)}>
          Executar novamente
        </MenuItem>
        <MenuItem disabled={!competencia || !municipalityIbge} onClick={() => void exportar()}>
          Exportar CSV da competência
        </MenuItem>
      </Menu>
    </>
  )
}
