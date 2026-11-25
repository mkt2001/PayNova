import { createHmac } from 'crypto';
import { SandboxProvider } from '../src/payment/sandbox.provider';

describe('SandboxProvider', () => {
  const p = new SandboxProvider();
  it('is never SUCCESS at initiation', async () => {
    const r = await p.initiate({ idempotencyKey: 'k1', vpa: 'a@okaxis', amountPaise: 5000, userId: 'u' });
    expect(r.status).toBe('PENDING');
  });
  it('fails amounts ending in 13', async () => {
    const r = await p.initiate({ idempotencyKey: 'k2', vpa: 'a@okaxis', amountPaise: 1300, userId: 'u' });
    jest.spyOn(Date, 'now').mockReturnValue(Date.now() + 3000);
    expect((await p.status(r.providerRef)).status).toBe('FAILED');
    jest.restoreAllMocks();
  });
  it('verifies webhook signatures and rejects bad ones', () => {
    const raw = Buffer.from('{"eventId":"e1"}');
    const sig = createHmac('sha256', SandboxProvider.secret).update(raw).digest('hex');
    expect(p.verifyWebhook(raw, sig)).toBe(true);
    expect(p.verifyWebhook(raw, 'deadbeef')).toBe(false);
  });
});
