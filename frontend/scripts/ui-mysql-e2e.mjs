import fs from 'node:fs';
import process from 'node:process';
import { chromium } from 'playwright-core';
import mysql from 'mysql2/promise';

const appBaseUrl = process.env.APP_BASE_URL || 'http://127.0.0.1:8081';
const chromeCandidates = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].filter(Boolean);
const chromePath = chromeCandidates.find((candidate) => fs.existsSync(candidate));
if (!chromePath) {
  throw new Error('No supported browser executable found');
}

const db = await mysql.createConnection({
  host: process.env.MYSQL_HOST || '127.0.0.1',
  port: Number(process.env.MYSQL_PORT || '3306'),
  user: process.env.MYSQL_USERNAME || 'root',
  password: process.env.MYSQL_PASSWORD || 'root',
  database: process.env.MYSQL_DATABASE || 'firstapi',
});

const browser = await chromium.launch({
  headless: true,
  executablePath: chromePath,
  args: ['--disable-gpu', '--no-sandbox']
});

const page = await browser.newPage({ baseURL: appBaseUrl });
const stamp = Date.now().toString();
const results = [];

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function scalar(sql, params = []) {
  const [rows] = await db.execute(sql, params);
  const first = rows[0];
  if (!first) {
    return null;
  }
  return Object.values(first)[0];
}

async function execute(sql, params = []) {
  await db.execute(sql, params);
}

async function waitForDb(check, message, timeout = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (await check()) {
      return;
    }
    await page.waitForTimeout(250);
  }
  throw new Error(message);
}

async function gotoPath(path, readySelector = '.chart-card') {
  await page.goto(appBaseUrl + path, { waitUntil: 'networkidle' });
  await page.locator(readySelector).first().waitFor({ state: 'visible' });
}

async function openModalFromToolbar() {
  await page.locator('.controls-row .btn-primary').first().click();
  const modal = page.getByTestId('modal-content');
  await modal.waitFor({ state: 'visible' });
  return modal;
}

async function rowByText(text) {
  const row = page.locator('tbody tr', { hasText: text }).first();
  await row.waitFor({ state: 'visible' });
  return row;
}

async function rowCountByText(text) {
  return await page.locator('tbody tr', { hasText: text }).count();
}

async function ensureNoVisibleRow(text) {
  assert((await rowCountByText(text)) === 0, `Row should not be visible: ${text}`);
}

async function waitForRowGone(text) {
  await page.waitForFunction((needle) => !Array.from(document.querySelectorAll('tbody tr')).some((row) => row.textContent.includes(needle)), text);
}

async function clickAction(row, index) {
  await row.locator('td').last().locator('div[style*="cursor: pointer"]').nth(index).click();
}

async function fillInputs(modal, values) {
  for (const item of values) {
    if (item.kind === 'input') {
      await modal.locator(item.selector).nth(item.index).fill(item.value);
    }
    if (item.kind === 'textarea') {
      await modal.locator('textarea').nth(item.index).fill(item.value);
    }
    if (item.kind === 'select') {
      if (item.label) {
        await modal.locator('select').nth(item.index).selectOption({ label: item.label });
      } else {
        await modal.locator('select').nth(item.index).selectOption(item.value);
      }
    }
  }
}

async function submitModal() {
  const modal = page.getByTestId('modal-content');
  await modal.locator('.btn-primary').last().click();
  await page.waitForTimeout(300);
}

async function closeModal() {
  const modal = page.getByTestId('modal-content');
  const cancelButton = modal.locator('button', { hasText: '取消' }).last();
  if (await cancelButton.count() > 0) {
    await cancelButton.click();
  } else {
    await page.locator('.modal-close').click();
  }
  await modal.waitFor({ state: 'hidden' });
}

async function searchByTestId(testId, value, pressEnter = false) {
  const input = page.getByTestId(testId);
  await input.fill('');
  if (value) {
    await input.fill(value);
  }
  if (pressEnter) {
    await input.press('Enter');
  }
  await page.waitForTimeout(400);
}

async function searchGeneric(value, pressEnter = false) {
  const input = page.locator('.controls-row input').first();
  await input.fill('');
  if (value) {
    await input.fill(value);
  }
  if (pressEnter) {
    await input.press('Enter');
  }
  await page.waitForTimeout(400);
}

async function expectModalError(text) {
  const error = page.getByTestId('modal-error');
  await error.waitFor({ state: 'visible' });
  const message = await error.textContent();
  assert(message && message.includes(text), `Expected modal error to include "${text}", got: ${message}`);
}

async function runStep(name, fn) {
  try {
    await fn();
    results.push({ page: name, result: 'PASS', details: '' });
  } catch (error) {
    results.push({ page: name, result: 'FAIL', details: error.message });
  }
}

await runStep('Dashboard', async () => {
  await gotoPath('/');
});

await runStep('Monitor', async () => {
  await gotoPath('/monitor');
});

await runStep('Records', async () => {
  await gotoPath('/records');
});

await runStep('MyRecords', async () => {
  await gotoPath('/my-records');
});

await runStep('Users CRUD + Search + Validation + Recovery', async () => {
  const invalidEmail = `${stamp}-invalid-user@example.com`;
  const createEmail = `${stamp}@example.com`;
  const createMarker = `ui-user-${stamp}`;
  const updateMarker = `ui-user-up-${stamp}`;
  await gotoPath('/users', 'table');

  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: '' },
    { kind: 'input', selector: 'input', index: 1, value: invalidEmail },
    { kind: 'input', selector: 'input', index: 2, value: '123456' },
    { kind: 'select', index: 0, label: 'VIP' }
  ]);
  await submitModal();
  await expectModalError('Username is required');
  await waitForDb(async () => Number(await scalar('select count(*) from users where email = ?', [invalidEmail])) === 0, 'Invalid users submit should not write to MySQL');
  await modal.locator('input').nth(0).fill(createMarker);
  await modal.locator('input').nth(1).fill(createEmail);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [createMarker])) === 1, 'Users create not written to MySQL');

  await searchByTestId('users-search', createMarker);
  await rowByText(createMarker);
  await searchByTestId('users-search', 'missing-user-marker');
  await ensureNoVisibleRow(createMarker);
  await searchByTestId('users-search', '');
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  modal = page.getByTestId('modal-content');
  await modal.waitFor({ state: 'visible' });
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('select').nth(0).selectOption({ label: 'Enterprise' });
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [updateMarker])) === 1, 'Users update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 2);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [updateMarker])) === 0, 'Users delete not written to MySQL');
});
await runStep('Users Pagination', async () => {
  const prefix = `ui-page-user-${stamp}`;
  try {
    for (let i = 1; i <= 21; i += 1) {
      const marker = `${prefix}-${String(i).padStart(2, '0')}`;
      await execute(
        'insert into users (email, username, balance, group_name, role_name, status_name, time_label) values (?, ?, ?, ?, ?, ?, ?)',
        [`${marker}@example.com`, marker, '$0.00', 'Default', '用户', '正常', '2026-03-14']
      );
    }

    await gotoPath('/users', 'table');
    await searchByTestId('users-search', prefix);
    await rowByText(`${prefix}-21`);
    await ensureNoVisibleRow(`${prefix}-01`);
    assert(await page.getByTestId('users-page-2').isVisible(), 'Users page 2 button should be visible');
    await page.getByTestId('users-page-2').click();
    await rowByText(`${prefix}-01`);
  } finally {
    await execute('delete from users where username like ?', [`${prefix}%`]);
  }
});

await runStep('Users Pagination Delete Boundary', async () => {
  const prefix = `ui-page-user-del-${stamp}`;
  try {
    for (let i = 1; i <= 21; i += 1) {
      const marker = `${prefix}-${String(i).padStart(2, '0')}`;
      await execute(
        'insert into users (email, username, balance, group_name, role_name, status_name, time_label) values (?, ?, ?, ?, ?, ?, ?)',
        [`${marker}@example.com`, marker, '$0.00', 'Default', '用户', '正常', '2026-03-14']
      );
    }

    await gotoPath('/users', 'table');
    await searchByTestId('users-search', prefix);
    await page.getByTestId('users-page-2').click();
    const row = await rowByText(`${prefix}-01`);
    await clickAction(row, 2);
    await waitForDb(async () => Number(await scalar('select count(*) from users where username like ?', [`${prefix}%`])) === 20, 'Users pagination delete not written to MySQL');
    await rowByText(`${prefix}-21`);
    assert(await page.getByTestId('users-page-2').count() === 0, 'Users page 2 should disappear after deleting the last item on page 2');
  } finally {
    await execute('delete from users where username like ?', [`${prefix}%`]);
  }
});
await runStep('Groups CRUD + Search', async () => {
  const createMarker = `ui-group-${stamp}`;
  const updateMarker = `ui-group-up-${stamp}`;
  await gotoPath('/groups', 'table');
  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'input', selector: 'input', index: 1, value: '33' },
    { kind: 'input', selector: 'input', index: 2, value: '0.7x' },
    { kind: 'select', index: 0, label: '按量计费' }
  ]);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from `groups` where name = ? and billing_type = ?', [createMarker, '按量计费'])) === 1, 'Groups create not written to MySQL');

  await searchGeneric(createMarker);
  await rowByText(createMarker);
  await searchGeneric('missing-group-marker');
  await ensureNoVisibleRow(createMarker);
  await searchGeneric('');
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('input').nth(2).fill('0.6x');
  await modal.locator('select').nth(0).selectOption({ label: '固定配额' });
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from `groups` where name = ? and billing_type = ? and rate_value = ?', [updateMarker, '固定配额', '0.6x'])) === 1, 'Groups update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 1);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from `groups` where name = ?', [updateMarker])) === 0, 'Groups delete not written to MySQL');
});

await runStep('Subscriptions CRUD + Search', async () => {
  const createMarker = `ui-sub-${stamp}@example.com`;
  const updateMarker = `ui-sub-up-${stamp}@example.com`;
  await gotoPath('/subscriptions', 'table');
  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'select', index: 0, label: 'Claude Pro' },
    { kind: 'input', selector: 'input', index: 1, value: '200' }
  ]);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from subscriptions where user_name = ? and usage_text = ?', [createMarker, '$0.00 / $200'])) === 1, 'Subscriptions create not written to MySQL');

  await searchByTestId('subscriptions-search', createMarker);
  await rowByText(createMarker);
  await searchByTestId('subscriptions-search', 'missing-sub-marker');
  await ensureNoVisibleRow(createMarker);
  await searchByTestId('subscriptions-search', '');
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('select').nth(0).selectOption({ label: 'Enterprise Gold' });
  await modal.locator('input').nth(1).fill('500');
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from subscriptions where user_name = ? and usage_text = ?', [updateMarker, '$0.00 / $500'])) === 1, 'Subscriptions update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 2);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from subscriptions where user_name = ?', [updateMarker])) === 0, 'Subscriptions delete not written to MySQL');
});

await runStep('Subscriptions Pagination', async () => {
  const prefix = `ui-page-sub-${stamp}`;
  try {
    for (let i = 1; i <= 21; i += 1) {
      const marker = `${prefix}-${String(i).padStart(2, '0')}@example.com`;
      await execute(
        'insert into subscriptions (user_name, uid_value, group_name, usage_text, progress_value, expiry_label, status_name) values (?, ?, ?, ?, ?, ?, ?)',
        [marker, 900000 + i, 'Claude Pro', '$0.00 / $20', 0, '2026-12-31', '正常']
      );
    }

    await gotoPath('/subscriptions', 'table');
    await searchByTestId('subscriptions-search', prefix);
    await rowByText(`${prefix}-21@example.com`);
    await ensureNoVisibleRow(`${prefix}-01@example.com`);
    assert(await page.getByTestId('subscriptions-page-2').isVisible(), 'Subscriptions page 2 button should be visible');
    await page.getByTestId('subscriptions-page-2').click();
    await rowByText(`${prefix}-01@example.com`);
  } finally {
    await execute('delete from subscriptions where user_name like ?', [`${prefix}%`]);
  }
});

await runStep('Subscriptions Pagination Delete Boundary', async () => {
  const prefix = `ui-page-sub-del-${stamp}`;
  try {
    for (let i = 1; i <= 21; i += 1) {
      const marker = `${prefix}-${String(i).padStart(2, '0')}@example.com`;
      await execute(
        'insert into subscriptions (user_name, uid_value, group_name, usage_text, progress_value, expiry_label, status_name) values (?, ?, ?, ?, ?, ?, ?)',
        [marker, 910000 + i, 'Claude Pro', '$0.00 / $20', 0, '2026-12-31', '正常']
      );
    }

    await gotoPath('/subscriptions', 'table');
    await searchByTestId('subscriptions-search', prefix);
    await page.getByTestId('subscriptions-page-2').click();
    const row = await rowByText(`${prefix}-01@example.com`);
    await clickAction(row, 2);
    await waitForDb(async () => Number(await scalar('select count(*) from subscriptions where user_name like ?', [`${prefix}%`])) === 20, 'Subscriptions pagination delete not written to MySQL');
    await rowByText(`${prefix}-21@example.com`);
    assert(await page.getByTestId('subscriptions-page-2').count() === 0, 'Subscriptions page 2 should disappear after deleting the last item on page 2');
  } finally {
    await execute('delete from subscriptions where user_name like ?', [`${prefix}%`]);
  }
});
await runStep('Accounts CRUD + Search + Validation + Recovery', async () => {
  const createMarker = `ui-account-${stamp}`;
  const updateMarker = `ui-account-up-${stamp}`;
  await gotoPath('/accounts', 'table');

  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: '' },
    { kind: 'select', index: 0, label: 'OpenAI' },
    { kind: 'textarea', index: 0, value: '' }
  ]);
  await submitModal();
  await expectModalError('Account name is required');
  await waitForDb(async () => Number(await scalar('select count(*) from accounts where name = ?', [createMarker])) === 0, 'Invalid account submit should not write to MySQL');
  await modal.locator('input').nth(0).fill(createMarker);
  await modal.locator('textarea').nth(0).fill(`sk-test-${stamp}`);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from accounts where name = ?', [createMarker])) === 1, 'Accounts create not written to MySQL');

  await searchByTestId('accounts-search', createMarker);
  await rowByText(createMarker);
  await searchByTestId('accounts-search', 'missing-account-marker');
  await ensureNoVisibleRow(createMarker);
  await searchByTestId('accounts-search', '');
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  await waitForDb(async () => Number(await scalar('select count(*) from accounts where name = ? and error_count = 0', [createMarker])) === 1, 'Accounts test not written to MySQL');

  row = await rowByText(createMarker);
  await clickAction(row, 1);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('select').nth(0).selectOption({ label: 'Anthropic' });
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from accounts where name = ? and platform = ?', [updateMarker, 'Anthropic'])) === 1, 'Accounts update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 2);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from accounts where name = ?', [updateMarker])) === 0, 'Accounts delete not written to MySQL');
});
await runStep('Announcements CRUD + Search', async () => {
  const createMarker = `ui-ann-${stamp}`;
  const updateMarker = `ui-ann-up-${stamp}`;
  await gotoPath('/announcements', 'table');
  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'textarea', index: 0, value: `content-${stamp}` }
  ]);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from announcements where title = ?', [createMarker])) === 1, 'Announcements create not written to MySQL');

  await searchGeneric(createMarker, true);
  await rowByText(createMarker);
  await searchGeneric('missing-ann-marker', true);
  await ensureNoVisibleRow(createMarker);
  await searchGeneric('', true);
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('select').nth(2).selectOption({ index: 1 });
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from announcements where title = ?', [updateMarker])) === 1, 'Announcements update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 1);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from announcements where title = ?', [updateMarker])) === 0, 'Announcements delete not written to MySQL');
});

await runStep('IPs CRUD + Search', async () => {
  const createMarker = `ui-ip-${stamp}`;
  const updateMarker = `ui-ip-up-${stamp}`;
  await gotoPath('/ips', 'table');
  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'select', index: 0, label: 'SOCKS5' },
    { kind: 'input', selector: 'input', index: 1, value: '10.0.0.1:9000' }
  ]);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from ips where name = ?', [createMarker])) === 1, 'IPs create not written to MySQL');

  await searchGeneric(createMarker, true);
  await rowByText(createMarker);
  await searchGeneric('missing-ip-marker', true);
  await ensureNoVisibleRow(createMarker);
  await searchGeneric('', true);
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  await waitForDb(async () => Number(await scalar('select count(*) from ips where name = ? and latency = ?', [createMarker, '128ms'])) === 1, 'IPs test not written to MySQL');

  row = await rowByText(createMarker);
  await clickAction(row, 1);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('input').nth(1).fill('10.0.0.2:9001');
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from ips where name = ? and address = ?', [updateMarker, '10.0.0.2:9001'])) === 1, 'IPs update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 2);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from ips where name = ?', [updateMarker])) === 0, 'IPs delete not written to MySQL');
});

await runStep('IPs Batch Test All', async () => {
  const prefix = `ui-ip-batch-${stamp}`;
  try {
    await execute(
      'insert into ips (name, protocol, address, location, accounts_count, latency, status_name) values (?, ?, ?, ?, ?, ?, ?)',
      [`${prefix}-1`, 'SOCKS5', '10.2.0.1:9000', 'Batch-A', '0', '0ms', '正常']
    );
    await execute(
      'insert into ips (name, protocol, address, location, accounts_count, latency, status_name) values (?, ?, ?, ?, ?, ?, ?)',
      [`${prefix}-2`, 'HTTP', '10.2.0.2:9001', 'Batch-B', '0', '0ms', '正常']
    );

    await gotoPath('/ips', 'table');
    await searchGeneric(prefix, true);
    await rowByText(`${prefix}-1`);
    await page.getByRole('button', { name: /全量测试/ }).click();
    await waitForDb(async () => Number(await scalar('select count(*) from ips where name like ? and latency = ?', [`${prefix}%`, '128ms'])) === 2, 'IPs test-all not written to MySQL');
    const batchRow = await rowByText(`${prefix}-1`);
    const rowText = await batchRow.textContent();
    assert(rowText && rowText.includes('128ms'), 'IPs test-all should refresh the visible latency');
  } finally {
    await execute('delete from ips where name like ?', [`${prefix}%`]);
  }
});
await runStep('Redemptions CRUD + Search', async () => {
  const createMarker = `ui-redemption-${stamp}`;
  const updateMarker = `ui-redemption-up-${stamp}`;
  await gotoPath('/redemptions', 'table');
  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'input', selector: 'input', index: 1, value: '$66.00' },
    { kind: 'input', selector: 'input', index: 2, value: '1' },
    { kind: 'input', selector: 'input', index: 3, value: '2' }
  ]);
  await submitModal();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [createMarker])) === 1, 'Redemptions create not written to MySQL');

  await searchGeneric(createMarker, true);
  await rowByText(createMarker);
  await searchGeneric('missing-redemption-marker', true);
  await ensureNoVisibleRow(createMarker);
  await searchGeneric('', true);
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('input').nth(1).fill('$88.00');
  await modal.locator('select').nth(1).selectOption({ index: 2 });
  await submitModal();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ? and value_text = ?', [updateMarker, '$88.00'])) === 1, 'Redemptions update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 1);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [updateMarker])) === 0, 'Redemptions delete not written to MySQL');
});

await runStep('Redemptions Batch Generate', async () => {
  const batchMarker = `ui-redemption-batch-${stamp}`;
  try {
    await gotoPath('/redemptions', 'table');
    const modal = await openModalFromToolbar();
    await fillInputs(modal, [
      { kind: 'input', selector: 'input', index: 0, value: batchMarker },
      { kind: 'input', selector: 'input', index: 1, value: '$11.00' },
      { kind: 'input', selector: 'input', index: 2, value: '3' },
      { kind: 'input', selector: 'input', index: 3, value: '2' }
    ]);
    await submitModal();
    await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [batchMarker])) === 3, 'Redemptions batch create not written to MySQL');
    await searchGeneric(batchMarker, true);
    assert((await rowCountByText(batchMarker)) === 3, 'Expected 3 redemption rows after batch generation');
  } finally {
    await execute('delete from redemptions where name = ?', [batchMarker]);
  }
});
await runStep('Promos CRUD + Search + Duplicate + Recovery', async () => {
  const createMarker = `UIPROMO${stamp}`;
  const recoveryMarker = `UIPROMOREC${stamp}`;
  const updateMarker = `UIPROMOUP${stamp}`;
  await gotoPath('/promos', 'table');
  let modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'input', selector: 'input', index: 1, value: '$9.90' },
    { kind: 'input', selector: 'input', index: 2, value: '0 / 100' },
    { kind: 'input', selector: 'input', index: 3, value: '2026/12/31' }
  ]);
  await modal.locator('button.btn-primary').last().click();
  await rowByText(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ?', [createMarker])) === 1, 'Promos create not written to MySQL');

  await searchByTestId('promos-search', createMarker, true);
  await rowByText(createMarker);
  await searchByTestId('promos-search', 'missing-promo-marker', true);
  await ensureNoVisibleRow(createMarker);
  await searchByTestId('promos-search', '', true);
  await rowByText(createMarker);

  modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: createMarker },
    { kind: 'input', selector: 'input', index: 1, value: '$5.50' },
    { kind: 'input', selector: 'input', index: 2, value: '0 / 10' },
    { kind: 'input', selector: 'input', index: 3, value: '2026/12/31' }
  ]);
  await modal.locator('button.btn-primary').last().click();
  await expectModalError('Promo code already exists');
  await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ?', [createMarker])) === 1, 'Duplicate promo should not create a second row');
  await modal.locator('input').nth(0).fill(recoveryMarker);
  await modal.locator('button.btn-primary').last().click();
  await rowByText(recoveryMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ?', [recoveryMarker])) === 1, 'Promo recovery create not written to MySQL');

  let recoveryRow = await rowByText(recoveryMarker);
  await clickAction(recoveryRow, 1);
  await waitForRowGone(recoveryMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ?', [recoveryMarker])) === 0, 'Promo recovery cleanup not written to MySQL');

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(updateMarker);
  await modal.locator('input').nth(1).fill('$8.80');
  await modal.locator('input').nth(2).fill('1 / 100');
  await modal.locator('button.btn-primary').last().click();
  await rowByText(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ? and value_text = ?', [updateMarker, '$8.80'])) === 1, 'Promos update not written to MySQL');

  row = await rowByText(updateMarker);
  await clickAction(row, 1);
  await waitForRowGone(updateMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ?', [updateMarker])) === 0, 'Promos delete not written to MySQL');
});
await runStep('MyApiKeys Actions + Search', async () => {
  const createMarker = `ui-key-${stamp}`;
  await gotoPath('/my-api-keys', 'table');
  await page.locator('.controls-row .btn-primary').click();
  let modal = page.getByTestId('modal-content');
  await modal.locator('input').nth(0).fill(createMarker);
  await modal.locator('.btn-primary').last().click();
  await rowByText(createMarker);
  const createdId = await scalar('select id from api_keys where name = ?', [createMarker]);
  assert(createdId !== null, 'MyApiKeys create not written to MySQL');
  const oldKey = await scalar('select api_key from api_keys where id = ?', [createdId]);

  await searchGeneric(createMarker, true);
  await rowByText(createMarker);
  await searchGeneric('missing-key-marker', true);
  await ensureNoVisibleRow(createMarker);
  await searchGeneric('', true);
  await rowByText(createMarker);

  let row = await rowByText(createMarker);
  await clickAction(row, 0);
  await waitForDb(async () => {
    const newKey = await scalar('select api_key from api_keys where id = ?', [createdId]);
    return newKey && newKey !== oldKey;
  }, 'MyApiKeys rotate not written to MySQL');

  row = await rowByText(createMarker);
  await clickAction(row, 1);
  await waitForRowGone(createMarker);
  await waitForDb(async () => Number(await scalar('select count(*) from api_keys where id = ?', [createdId])) === 0, 'MyApiKeys delete not written to MySQL');
});
await runStep('Settings Update + Reload', async () => {
  const marker = `ui-settings-${stamp}`;
  await gotoPath('/settings', '[data-testid="settings-site-name"]');
  await page.getByTestId('settings-site-announcement').fill(marker);
  await page.getByTestId('settings-save').click();
  await waitForDb(async () => (await scalar('select site_announcement from settings where id = 1')) === marker, 'Settings update not written to MySQL');
  await page.reload({ waitUntil: 'networkidle' });
  await page.getByTestId('settings-site-announcement').waitFor({ state: 'visible' });
  assert((await page.getByTestId('settings-site-announcement').inputValue()) === marker, 'Settings value should persist after reload');
});
await runStep('Profile Update + Reload + 2FA', async () => {
  const marker = `ui-profile-${stamp}`;
  await db.execute('update profiles set two_factor_enabled = 0 where id = 1');
  await gotoPath('/profile', '[data-testid="profile-username"]');
  await page.getByTestId('profile-phone').fill(marker);
  await page.getByTestId('profile-bio').fill(marker);
  await page.getByTestId('profile-save').click();
  await waitForDb(async () => Number(await scalar('select count(*) from profiles where id = 1 and phone = ? and bio = ?', [marker, marker])) === 1, 'Profile update not written to MySQL');

  await page.reload({ waitUntil: 'networkidle' });
  await page.getByTestId('profile-phone').waitFor({ state: 'visible' });
  assert((await page.getByTestId('profile-phone').inputValue()) === marker, 'Profile phone should persist after reload');
  assert((await page.getByTestId('profile-bio').inputValue()) === marker, 'Profile bio should persist after reload');
  const initialBadge = await page.getByTestId('profile-2fa-badge').textContent();
  assert(initialBadge && initialBadge.includes('Disabled'), 'Profile 2FA badge should reflect disabled state before activation');

  await page.getByTestId('profile-tab-security').click();
  assert(await page.getByTestId('profile-enable-2fa').isEnabled(), 'Profile 2FA button should be enabled before activation');
  await page.getByTestId('profile-enable-2fa').click();
  await waitForDb(async () => Number(await scalar('select two_factor_enabled from profiles where id = 1')) === 1, 'Profile 2FA not written to MySQL');

  await page.reload({ waitUntil: 'networkidle' });
  await page.getByTestId('profile-2fa-badge').waitFor({ state: 'visible' });
  const badgeText = await page.getByTestId('profile-2fa-badge').textContent();
  assert(badgeText && badgeText.includes('Enabled'), 'Profile 2FA badge should persist after reload');
});
await runStep('MyRedemption Action', async () => {
  const marker = `UI-REDEEM-${stamp}`;
  await gotoPath('/my-redemption', '[data-testid="my-redemption-code"]');
  await page.getByTestId('my-redemption-code').fill(marker);
  await page.getByTestId('my-redemption-submit').click();
  await waitForDb(async () => Number(await scalar('select count(*) from my_redemption where id = 1 and history_json like ?', [`%${marker}%`])) === 1, 'MyRedemption action not written to MySQL');
});

await runStep('MySubscription Action', async () => {
  await gotoPath('/my-subscription', '[data-testid="my-subscription-renew"]');
  const beforeLength = Number(await scalar('select char_length(history_json) from my_subscription where id = 1'));
  await page.getByTestId('my-subscription-renew').click();
  await waitForDb(async () => Number(await scalar('select char_length(history_json) from my_subscription where id = 1')) > beforeLength, 'MySubscription action not written to MySQL');
});

await runStep('GET Retry Recovery', async () => {
  let intercepted = false;
  await page.route('**/api/admin/users*', async (route) => {
    if (!intercepted) {
      intercepted = true;
      await route.abort('failed');
      return;
    }
    await route.continue();
  });

  try {
    await gotoPath('/users', 'table');
    await page.locator('tbody tr').first().waitFor({ state: 'visible' });
    assert(intercepted, 'Expected the first users request to be interrupted for retry testing');
  } finally {
    await page.unroute('**/api/admin/users*');
  }
});

await runStep('Users Duplicate Submit Guard', async () => {
  const marker = `ui-user-dup-${stamp}`;
  await gotoPath('/users', 'table');
  const modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: marker },
    { kind: 'input', selector: 'input', index: 1, value: `${marker}@example.com` },
    { kind: 'input', selector: 'input', index: 2, value: '123456' },
    { kind: 'select', index: 0, label: 'VIP' }
  ]);
  const saveButton = modal.locator('.btn-primary').last();
  await saveButton.evaluate((node) => { node.click(); node.click(); });
  await rowByText(marker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [marker])) === 1, 'Users duplicate submit should only create one row');

  const row = await rowByText(marker);
  await clickAction(row, 2);
  await waitForRowGone(marker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [marker])) === 0, 'Users duplicate guard cleanup failed');
});

await runStep('Users Unicode Edge Data', async () => {
  const marker = `徐雅${stamp}`;
  await gotoPath('/users', 'table');
  const modal = await openModalFromToolbar();
  await fillInputs(modal, [
    { kind: 'input', selector: 'input', index: 0, value: marker },
    { kind: 'input', selector: 'input', index: 1, value: `xuya-${stamp}@example.com` },
    { kind: 'input', selector: 'input', index: 2, value: '123456' },
    { kind: 'select', index: 0, label: 'VIP' }
  ]);
  await submitModal();
  await rowByText(marker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [marker])) === 1, 'Unicode user create not written to MySQL');

  await searchByTestId('users-search', marker);
  await rowByText(marker);

  const row = await rowByText(marker);
  await clickAction(row, 2);
  await waitForRowGone(marker);
  await waitForDb(async () => Number(await scalar('select count(*) from users where username = ?', [marker])) === 0, 'Unicode user cleanup failed');
  await searchByTestId('users-search', '');
});

await runStep('Promos Duplicate Submit Guard', async () => {
  const marker = `UIPROMODUP${stamp}`;
  try {
    await gotoPath('/promos', 'table');
    const modal = await openModalFromToolbar();
    await fillInputs(modal, [
      { kind: 'input', selector: 'input', index: 0, value: marker },
      { kind: 'input', selector: 'input', index: 1, value: '$6.60' },
      { kind: 'input', selector: 'input', index: 2, value: '0 / 20' },
      { kind: 'input', selector: 'input', index: 3, value: '2026/12/31' }
    ]);
    const saveButton = modal.locator('button.btn-primary').last();
    await saveButton.evaluate((node) => { node.click(); node.click(); });
    await rowByText(marker);
    await waitForDb(async () => Number(await scalar('select count(*) from promos where code = ?', [marker])) === 1, 'Promos duplicate submit should only create one row');
  } finally {
    await execute('delete from promos where code = ?', [marker]);
  }
});

await runStep('Redemptions Duplicate Submit Guard', async () => {
  const marker = `ui-redemption-dup-${stamp}`;
  try {
    await gotoPath('/redemptions', 'table');
    const modal = await openModalFromToolbar();
    await fillInputs(modal, [
      { kind: 'input', selector: 'input', index: 0, value: marker },
      { kind: 'input', selector: 'input', index: 1, value: '$12.00' },
      { kind: 'input', selector: 'input', index: 2, value: '2' },
      { kind: 'input', selector: 'input', index: 3, value: '1' }
    ]);
    const saveButton = modal.locator('.btn-primary').last();
    await saveButton.evaluate((node) => { node.click(); node.click(); });
    await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [marker])) === 2, 'Redemptions duplicate submit should only create the requested batch size once');
    await searchGeneric(marker, true);
    assert((await rowCountByText(marker)) === 2, 'Expected exactly 2 redemption rows after duplicate click guard');
  } finally {
    await execute('delete from redemptions where name = ?', [marker]);
  }
});

await runStep('MyApiKeys Duplicate Submit Guard', async () => {
  const marker = `ui-key-dup-${stamp}`;
  try {
    await gotoPath('/my-api-keys', 'table');
    await page.locator('.controls-row .btn-primary').click();
    const modal = page.getByTestId('modal-content');
    await modal.locator('input').nth(0).fill(marker);
    const saveButton = modal.locator('.btn-primary').last();
    await saveButton.evaluate((node) => { node.click(); node.click(); });
    await rowByText(marker);
    await waitForDb(async () => Number(await scalar('select count(*) from api_keys where name = ?', [marker])) === 1, 'MyApiKeys duplicate submit should only create one row');
  } finally {
    await execute('delete from api_keys where name = ?', [marker]);
  }
});

await runStep('MySubscription Duplicate Renew Guard', async () => {
  await gotoPath('/my-subscription', '[data-testid="my-subscription-renew"]');
  const beforeCount = Number(await scalar('select json_length(history_json) from my_subscription where id = 1'));
  const renewButton = page.getByTestId('my-subscription-renew');
  await renewButton.evaluate((node) => { node.click(); node.click(); });
  await waitForDb(async () => Number(await scalar('select json_length(history_json) from my_subscription where id = 1')) === beforeCount + 1, 'MySubscription duplicate renew should append exactly one history item');
});

await runStep('Redemptions Validation + Recovery', async () => {
  const marker = `ui-redemption-recovery-${stamp}`;
  try {
    await gotoPath('/redemptions', 'table');
    const modal = await openModalFromToolbar();
    await fillInputs(modal, [
      { kind: 'input', selector: 'input', index: 0, value: '' },
      { kind: 'input', selector: 'input', index: 1, value: '' },
      { kind: 'input', selector: 'input', index: 2, value: '0' },
      { kind: 'input', selector: 'input', index: 3, value: '0' }
    ]);
    await submitModal();
    await expectModalError('Redemption name is required');
    await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [marker])) === 0, 'Invalid redemption submit should not write to MySQL');

    await modal.locator('input').nth(0).fill(marker);
    await modal.locator('input').nth(1).fill('$19.90');
    await modal.locator('input').nth(2).fill('1');
    await modal.locator('input').nth(3).fill('3');
    await submitModal();
    await rowByText(marker);
    await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [marker])) === 1, 'Redemption recovery create not written to MySQL');

    const row = await rowByText(marker);
    await clickAction(row, 1);
    await waitForRowGone(marker);
    await waitForDb(async () => Number(await scalar('select count(*) from redemptions where name = ?', [marker])) === 0, 'Redemption recovery cleanup failed');
  } finally {
    await execute('delete from redemptions where name = ?', [marker]);
  }
});

await runStep('MyApiKeys Validation + Recovery', async () => {
  const marker = `ui-key-recovery-${stamp}`;
  try {
    await gotoPath('/my-api-keys', 'table');
    await page.locator('.controls-row .btn-primary').click();
    const modal = page.getByTestId('modal-content');
    await modal.locator('input').nth(0).fill('');
    await modal.locator('.btn-primary').last().click();
    await expectModalError('API key name is required');
    await waitForDb(async () => Number(await scalar('select count(*) from api_keys where name = ?', [marker])) === 0, 'Invalid API key submit should not write to MySQL');

    await modal.locator('input').nth(0).fill(marker);
    await modal.locator('.btn-primary').last().click();
    await rowByText(marker);
    await waitForDb(async () => Number(await scalar('select count(*) from api_keys where name = ?', [marker])) === 1, 'API key recovery create not written to MySQL');

    const row = await rowByText(marker);
    await clickAction(row, 1);
    await waitForRowGone(marker);
    await waitForDb(async () => Number(await scalar('select count(*) from api_keys where name = ?', [marker])) === 0, 'API key recovery cleanup failed');
  } finally {
    await execute('delete from api_keys where name = ?', [marker]);
  }
});

await runStep('Settings Validation + Extreme Values + Reload', async () => {
  const siteName = `UI Settings 徐雅 ${stamp} <>&`;
  const announcement = `Line 1 徐雅 ${stamp}\n<script>alert("edge")</script>\n${'A'.repeat(180)}`;
  const apiProxy = `https://proxy.example.com/v1/${stamp}?name=%E5%BE%90%E9%9B%85&mode=test`;
  const streamTimeout = '2147483647';
  const retryLimit = '0';
  const beforeStream = Number(await scalar('select stream_timeout from settings where id = 1'));
  const beforeRetry = Number(await scalar('select retry_limit from settings where id = 1'));
  const toggledRegistration = Number(await scalar('select registration_open from settings where id = 1')) === 0;
  const defaultGroup = 'VIP';

  await gotoPath('/settings', '[data-testid="settings-site-name"]');
  await page.getByTestId('settings-tab-api').click();
  await page.getByTestId('settings-stream-timeout').fill('-1');
  await page.getByTestId('settings-retry-limit').fill('-2');
  await page.getByTestId('settings-save').click();
  await page.getByTestId('settings-save-error').waitFor({ state: 'visible' });
  const errorText = await page.getByTestId('settings-save-error').textContent();
  assert(errorText && errorText.includes('Stream timeout must be 0 or greater'), `Expected settings error message, got: ${errorText}`);
  await waitForDb(
    async () => Number(await scalar('select stream_timeout from settings where id = 1')) === beforeStream
      && Number(await scalar('select retry_limit from settings where id = 1')) === beforeRetry,
    'Invalid settings submit should not write to MySQL'
  );

  await page.getByTestId('settings-tab-general').click();
  await page.getByTestId('settings-site-name').fill(siteName);
  await page.getByTestId('settings-site-announcement').fill(announcement);
  await page.getByTestId('settings-tab-api').click();
  await page.getByTestId('settings-api-proxy').fill(apiProxy);
  await page.getByTestId('settings-stream-timeout').fill(streamTimeout);
  await page.getByTestId('settings-retry-limit').fill(retryLimit);
  await page.getByTestId('settings-tab-auth').click();
  const handle = page.locator('[data-testid="settings-registration-open"]');
  const currentRegistration = Number(await scalar('select registration_open from settings where id = 1')) === 1;
  if (currentRegistration !== toggledRegistration) {
    await handle.click();
  }
  await page.getByTestId('settings-default-group').selectOption(defaultGroup);
  await page.getByTestId('settings-save').click();
  await page.getByTestId('settings-save-status').waitFor({ state: 'visible' });
  await waitForDb(async () => {
    const [rows] = await db.execute('select site_name, site_announcement, api_proxy, stream_timeout, retry_limit, registration_open, default_group from settings where id = 1');
    const row = rows[0];
    return row
      && row.site_name === siteName
      && row.site_announcement === announcement
      && row.api_proxy === apiProxy
      && Number(row.stream_timeout) === Number(streamTimeout)
      && Number(row.retry_limit) === Number(retryLimit)
      && Number(row.registration_open) === (toggledRegistration ? 1 : 0)
      && row.default_group === defaultGroup;
  }, 'Settings extreme value update not written to MySQL');

  await page.reload({ waitUntil: 'networkidle' });
  await page.getByTestId('settings-site-name').waitFor({ state: 'visible' });
  assert((await page.getByTestId('settings-site-name').inputValue()) === siteName, 'Settings site name should persist after reload');
  assert((await page.getByTestId('settings-site-announcement').inputValue()) === announcement, 'Settings announcement should persist after reload');
  await page.getByTestId('settings-tab-api').click();
  assert((await page.getByTestId('settings-api-proxy').inputValue()) === apiProxy, 'Settings proxy should persist after reload');
  assert((await page.getByTestId('settings-stream-timeout').inputValue()) === streamTimeout, 'Settings stream timeout should persist after reload');
  assert((await page.getByTestId('settings-retry-limit').inputValue()) === retryLimit, 'Settings retry limit should persist after reload');
  await page.getByTestId('settings-tab-auth').click();
  assert((await page.getByTestId('settings-default-group').inputValue()) === defaultGroup, 'Settings default group should persist after reload');
});

await runStep('Profile Validation + Unicode + Long Text + Reload', async () => {
  const username = `徐雅-${stamp}`;
  const phone = `+86-177-${stamp.slice(-8, -4)}-${stamp.slice(-4)}`;
  const bio = `Profile line 1 徐雅 ${stamp}\nSpecial chars <> & " '\n${'bio-'.repeat(60)}`;
  const beforeUsername = await scalar('select username from profiles where id = 1');

  await gotoPath('/profile', '[data-testid="profile-username"]');
  await page.getByTestId('profile-username').fill('');
  await page.getByTestId('profile-save').click();
  await page.getByTestId('profile-save-error').waitFor({ state: 'visible' });
  const errorText = await page.getByTestId('profile-save-error').textContent();
  assert(errorText && errorText.includes('Username is required'), `Expected profile error message, got: ${errorText}`);
  await waitForDb(async () => (await scalar('select username from profiles where id = 1')) === beforeUsername, 'Invalid profile submit should not write to MySQL');

  await page.getByTestId('profile-username').fill(username);
  await page.getByTestId('profile-phone').fill(phone);
  await page.getByTestId('profile-bio').fill(bio);
  await page.getByTestId('profile-save').click();
  await page.getByTestId('profile-save-status').waitFor({ state: 'visible' });
  await waitForDb(async () => {
    const [rows] = await db.execute('select username, phone, bio from profiles where id = 1');
    const row = rows[0];
    return row && row.username === username && row.phone === phone && row.bio === bio;
  }, 'Profile unicode update not written to MySQL');

  await page.reload({ waitUntil: 'networkidle' });
  await page.getByTestId('profile-username').waitFor({ state: 'visible' });
  assert((await page.getByTestId('profile-username').inputValue()) === username, 'Profile username should persist after reload');
  assert((await page.getByTestId('profile-phone').inputValue()) === phone, 'Profile phone should persist after reload');
  assert((await page.getByTestId('profile-bio').inputValue()) === bio, 'Profile bio should persist after reload');
});

await runStep('Promos Delete Sweep', async () => {
  const prefix = `UISWEEP${stamp}`;
  try {
    for (let i = 1; i <= 3; i += 1) {
      await execute(
        'insert into promos (code, type_name, value_text, usage_text, expiry_label, status_name) values (?, ?, ?, ?, ?, ?)',
        [`${prefix}${i}`, 'Sweep', `$${i}.00`, '0 / 1', '2027/01/01', 'Active']
      );
    }

    await gotoPath('/promos', 'table');
    await searchByTestId('promos-search', prefix, true);
    assert((await rowCountByText(prefix)) === 3, 'Expected 3 promo rows before delete sweep');

    while ((await rowCountByText(prefix)) > 0) {
      const row = page.locator('tbody tr', { hasText: prefix }).first();
      await clickAction(row, 1);
      await page.waitForTimeout(300);
    }

    await waitForDb(async () => Number(await scalar('select count(*) from promos where code like ?', [`${prefix}%`])) === 0, 'Promo delete sweep not written to MySQL');
    await ensureNoVisibleRow(prefix);
  } finally {
    await execute('delete from promos where code like ?', [`${prefix}%`]);
  }
});
console.table(results);

const failed = results.filter((item) => item.result === 'FAIL');
await browser.close();
await db.end();
if (failed.length > 0) {
  process.exitCode = 1;
}
