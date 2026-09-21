// Captures screenshots of every screen against the running dev server.
// Usage: npm run screenshot [-- --only=03-indicadores] [--base=http://localhost:5173]
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const args = Object.fromEntries(process.argv.slice(2).map((a) => a.replace(/^--/, '').split('=')))
const base = args.base ?? 'http://localhost:5173'
const outDir = path.resolve(process.cwd(), '../../docs/ui-screenshots')
const desktop = { width: 1448, height: 1086 }

const shots = [
  { slug: '01-painel', path: '/painel?mock-login=1', viewport: desktop },
  { slug: '02-login', path: '/login', viewport: desktop, logout: true },
  { slug: '03-indicadores', path: '/indicadores?mock-login=1', viewport: desktop },
  { slug: '04-indicador-detalhe', path: '/indicadores/PB-01?mock-login=1', viewport: desktop },
  { slug: '05-execucao', path: '/execucao?mock-login=1', viewport: desktop },
  { slug: '06-fonte-de-dados', path: '/configuracoes?mock-login=1', viewport: desktop },
  { slug: '07-relatorios', path: '/relatorios?mock-login=1', viewport: desktop },
  { slug: '08-isolamento-municipal', path: '/configuracoes/isolamento-municipal?mock-login=1', viewport: desktop },
  { slug: '09-tablet-painel', path: '/painel?mock-login=1', viewport: { width: 1024, height: 768 } },
  { slug: '09-phone-indicadores', path: '/indicadores?mock-login=1', viewport: { width: 390, height: 844 } },
  { slug: '09-phone-detalhe', path: '/indicadores/PB-01?mock-login=1', viewport: { width: 390, height: 844 } },
]

await mkdir(outDir, { recursive: true })
const browser = await chromium.launch()
const errors = []
for (const shot of shots) {
  if (args.only && !args.only.split(',').includes(shot.slug)) continue
  const context = await browser.newContext({ viewport: shot.viewport, deviceScaleFactor: 1, locale: 'pt-BR' })
  const page = await context.newPage()
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`${shot.slug}: ${m.text()}`)
  })
  page.on('pageerror', (e) => errors.push(`${shot.slug}: ${e.message}`))
  await page.goto(`${base}${shot.path}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  const file = path.join(outDir, `${shot.slug}.png`)
  await page.screenshot({ path: file, fullPage: false })
  console.log(`saved ${path.relative(process.cwd(), file)}`)
  await context.close()
}
await browser.close()
if (errors.length) {
  console.error('\nConsole errors:')
  for (const e of errors) console.error(' -', e)
  process.exitCode = 1
}
