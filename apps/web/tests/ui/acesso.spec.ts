import { expect, test } from '../support/test.ts'

test.describe('login com os dados de demonstração', () => {
  test('exige usuário e senha antes de enviar', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('Informe usuário e senha.')
    await expect(page).toHaveURL('/login')
  })

  test('entra, lembra só o usuário e volta ao painel', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Usuário', { exact: true }).fill('gestor')
    await page.getByLabel('Senha', { exact: true }).fill('qualquer-senha')
    await page.getByRole('checkbox', { name: /Lembrar somente o usuário/ }).check()
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await expect(page).toHaveURL('/painel')
    expect(await page.evaluate(() => localStorage.getItem('esusdata.remembered-username'))).toBe(
      'gestor',
    )
    await page.getByRole('button', { name: 'Sair' }).click()
    await expect(page.getByLabel('Usuário', { exact: true })).toHaveValue('gestor')
    await expect(page.getByLabel('Senha', { exact: true })).toHaveValue('')
  })

  test('mostra e oculta a senha', async ({ page }) => {
    await page.goto('/login')
    const senha = page.getByLabel('Senha', { exact: true })
    await expect(senha).toHaveAttribute('type', 'password')
    await page.getByRole('button', { name: 'Mostrar senha' }).click()
    await expect(senha).toHaveAttribute('type', 'text')
    await page.getByRole('button', { name: 'Ocultar senha' }).click()
    await expect(senha).toHaveAttribute('type', 'password')
  })

  test('"Esqueci minha senha" orienta a procurar o administrador', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: 'Esqueci minha senha' }).click()
    await expect(page.getByRole('status')).toContainText('Solicite ajuda ao administrador')
  })

  test('links para ativação e ajuda', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('link', { name: 'Ativar meu acesso' }).click()
    await expect(page).toHaveURL('/ativar-acesso')
    await page.getByRole('link', { name: 'Voltar ao login' }).click()
    await page.getByRole('link', { name: 'Ajuda e suporte' }).click()
    await expect(page).toHaveURL('/ajuda-acesso')
  })

  test('com sessão, login e ativação levam ao painel', async ({ page }) => {
    await page.goto('/painel?mock-login=1')
    await page.goto('/login')
    await expect(page).toHaveURL('/painel')
    await page.goto('/ativar-acesso')
    await expect(page).toHaveURL('/painel')
  })
})

test.describe('ativação: validações antes de chamar a API', () => {
  test('exige o código e as duas senhas, e que coincidam', async ({ page }) => {
    await page.goto('/ativar-acesso')
    await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
    await expect(page.getByRole('alert')).toHaveText('Informe o código e as duas senhas.')
    await page.getByLabel('Código de ativação').fill('codigo')
    await page.getByLabel('Nova senha', { exact: true }).fill('uma-senha-longa-1')
    await page.getByLabel('Confirmar nova senha').fill('outra-senha-longa-2')
    await page.getByRole('button', { name: 'Ativar meu acesso' }).click()
    await expect(page.getByRole('alert')).toHaveText('As senhas não coincidem.')
  })
})
