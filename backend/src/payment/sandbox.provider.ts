import { Injectable } from '@nestjs/common';
import { createHmac, randomUUID, timingSafeEqual } from 'crypto';
import { InitiateInput, PspProvider, PspStatus } from './psp.provider';

/** Simulator. Same rules as the Android demo provider. NEVER moves real money. */
@Injectable()
export class SandboxProvider extends PspProvider {
  readonly mode = 'sandbox' as const;
  private started = new Map<string, { t: number; paise: number }>();
  static secret = process.env.PSP_WEBHOOK_SECRET || 'sandbox-secret';

  async resolve(kind: any, value: string, _ifsc?: string, name?: string) {
    return { name, vpa: kind === 'VPA' ? value : `${value}@sandbox`, verified: false, merchantInfo: 'SANDBOX - not verified' };
  }
  async initiate(i: InitiateInput) {
    const providerRef = 'SBX' + randomUUID().replace(/-/g, '').slice(0, 12).toUpperCase();
    this.started.set(providerRef, { t: Date.now(), paise: i.amountPaise });
    return { providerRef, status: 'PENDING' as PspStatus, maskedAccount: 'Sandbox Bank XXXX0000' };
  }
  async status(ref: string) {
    const s = this.started.get(ref);
    if (!s) return { status: 'SUCCESS' as PspStatus, upiTxnId: 'SBXUPI' + ref.slice(-8), maskedAccount: 'Sandbox Bank XXXX0000' };
    const rupees = Math.floor(s.paise / 100), el = Date.now() - s.t;
    let st: PspStatus = 'PENDING';
    if (rupees % 100 === 13) st = el > 2000 ? 'FAILED' : 'PENDING';
    else if (rupees % 100 === 17) st = el > 30000 ? 'SUCCESS' : 'PENDING';
    else st = el > 2000 ? 'SUCCESS' : 'PENDING';
    return { status: st, upiTxnId: st === 'SUCCESS' ? 'SBXUPI' + ref.slice(-8) : undefined, maskedAccount: 'Sandbox Bank XXXX0000' };
  }
  async fetchBill(category: string, customerId: string) {
    const amountPaise = (((customerId.split('').reduce((a, c) => a + c.charCodeAt(0), 0)) % 900) + 100) * 100;
    return { billerName: `${category} biller (SANDBOX)`, customerName: 'Sandbox Customer', dueDate: new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10), amountPaise };
  }
  verifyWebhook(raw: Buffer, sig: string) {
    const expected = createHmac('sha256', SandboxProvider.secret).update(raw).digest('hex');
    const a = Buffer.from(expected), b = Buffer.from(sig || '');
    return a.length === b.length && timingSafeEqual(a, b);
  }
}
