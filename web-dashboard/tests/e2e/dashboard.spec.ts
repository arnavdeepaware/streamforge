import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const pipelineId = '8fdc57d0-68b4-4729-b6cc-7034dd465819';

const routes = [
  { path: '/dashboard', heading: 'Welcome to StreamForge' },
  { path: '/pipelines', heading: 'Pipelines' },
  { path: '/pipelines/new', heading: 'New Pipeline' },
  { path: '/pipelines/mapper', heading: 'Field Mapper' },
  { path: '/schema-registry', heading: 'Schema Registry' },
  { path: `/pipelines/${pipelineId}`, heading: 'US equities ingest' },
] as const;

test.beforeEach(async ({ page }) => mockControlPlane(page));

for (const route of routes) {
  test(`${route.path} reflows without critical accessibility issues`, async ({
    page,
  }) => {
    await page.goto(route.path);
    await expect(
      page.getByRole('heading', { name: route.heading, exact: true }),
    ).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth + 1,
    );
    expect(overflow).toBe(false);

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag22aa'])
      .analyze();
    expect(
      results.violations.filter((violation) =>
        ['critical', 'serious'].includes(violation.impact ?? ''),
      ),
    ).toEqual([]);
  });
}

test('navigation and contextual help remain keyboard operable', async ({
  page,
}, testInfo) => {
  await page.goto('/dashboard');

  if (testInfo.project.name !== 'desktop') {
    await page.getByRole('button', { name: 'Open navigation' }).click();
    await expect(page.getByLabel('Primary sidebar')).toHaveClass(/open/);
    await page.keyboard.press('Escape');
    await expect(
      page.getByRole('button', { name: 'Open navigation' }),
    ).toBeFocused();
  }

  const helpButton =
    testInfo.project.name === 'desktop'
      ? page.getByRole('button', { name: 'Help', exact: true })
      : page.getByRole('button', { name: 'Open help' });
  await helpButton.click();
  await expect(
    page.getByRole('dialog', { name: 'Help and guidance' }),
  ).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(helpButton).toBeFocused();
});

async function mockControlPlane(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        body: JSON.stringify(body),
        contentType: 'application/json',
        status,
      });

    if (path.endsWith('/pipelines/preview/canonical-fields')) {
      return json([
        { path: 'instrument.symbol', type: 'STRING', protectedField: false },
        { path: 'payload.price', type: 'FIXED_DECIMAL', protectedField: false },
      ]);
    }
    if (path.endsWith('/pipelines/preview') && request.method() === 'POST') {
      return json({
        status: 'READY',
        input: {},
        transformed: {},
        output: {},
        errors: [],
      });
    }
    if (path.endsWith('/schemas')) {
      return json({
        items: [
          {
            id: 'schema-1',
            name: 'Canonical Market Event',
            description: 'The normalized event contract.',
            archived: false,
            latestRevisionNumber: 1,
            createdAt: '2026-08-03T12:00:00Z',
            updatedAt: '2026-08-03T12:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    }
    if (path.endsWith(`/pipelines/${pipelineId}/runs/latest`)) {
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith(`/pipelines/${pipelineId}`)) {
      return json({
        ...pipeline(),
        version: 1,
        latestRevision: {
          id: 'revision-1',
          revisionNumber: 3,
          createdAt: '2026-08-03T13:00:00Z',
        },
      });
    }
    if (path.endsWith('/pipelines')) {
      return json({
        items: [pipeline()],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    }
    return json({ detail: `Unhandled test request: ${path}` }, 404);
  });
}

function pipeline() {
  return {
    id: pipelineId,
    name: 'US equities ingest',
    description: 'Normalizes local STP traffic.',
    archived: false,
    latestRevisionNumber: 3,
    createdAt: '2026-08-03T12:00:00Z',
    updatedAt: '2026-08-03T13:00:00Z',
  };
}
